package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.EvoriaDatabase
import com.example.data.local.EvoriaSanctuaryRepository
import com.example.data.local.PhotoEmbeddingEntity
import com.example.data.local.PhotoIndexStatus
import com.example.ml.EmbeddingMath
import com.example.ml.EvoriaVisionRuntime
import com.example.ml.ModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

/**
 * Background WorkManager indexer for EVORIA Vision Search Lite.
 * Incrementally computes compact Float16 visual embeddings for unindexed or modified photos
 * without blocking the UI thread and unloads the vision session when done.
 */
class VisionIndexWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val ownerUid = inputData.getString(KEY_OWNER_UID)?.trim().orEmpty()
    if (ownerUid.isEmpty()) return Result.failure()

    val modelManager = EvoriaVisionRuntime.modelManager(applicationContext)
    val state = modelManager.ensureReady()
    if (state !is ModelState.Ready) {
      return Result.success(workDataOf(KEY_INDEXED_COUNT to 0))
    }

    val engine = EvoriaVisionRuntime.embeddingEngine(applicationContext)
    val dao = EvoriaDatabase.getInstance(applicationContext).evoriaDao()
    val repo = EvoriaSanctuaryRepository(applicationContext, dao)

    val modelVersion = modelManager.modelVersion()
    val memories = repo.getMemoriesSnapshot(ownerUid)
    val existingById = repo.getPhotoEmbeddingsSnapshot(ownerUid).associateBy { it.memoryId }

    val toIndex =
      memories.filter { mem ->
        if (!EvoriaVisionRuntime.isIndexablePhoto(mem)) return@filter false
        val uri = mem.mediaUri ?: return@filter false
        val sig = EvoriaVisionRuntime.computeContentSignature(applicationContext, uri)
        val existing = existingById[mem.id]
        existing == null ||
          existing.modelVersion != modelVersion ||
          existing.contentSignature != sig ||
          existing.indexStatus == PhotoIndexStatus.PENDING
      }

    if (toIndex.isEmpty()) {
      return Result.success(workDataOf(KEY_INDEXED_COUNT to 0))
    }

    var indexedCount = 0
    val total = toIndex.size
    return try {
      toIndex.forEachIndexed { idx, memory ->
        if (isStopped) return@forEachIndexed
        val uri = memory.mediaUri ?: return@forEachIndexed
        val sig = EvoriaVisionRuntime.computeContentSignature(applicationContext, uri)

        val vector = engine.embedImage(uri)
        val now = System.currentTimeMillis()
        if (vector != null && vector.isNotEmpty()) {
          val compressed = EmbeddingMath.toFloat16ByteArray(vector)
          repo.upsertPhotoEmbedding(
            PhotoEmbeddingEntity(
              memoryId = memory.id,
              ownerUid = ownerUid,
              fileUri = uri,
              embeddingBlob = compressed,
              embeddingDim = vector.size,
              modelVersion = modelVersion,
              indexStatus = PhotoIndexStatus.INDEXED,
              contentSignature = sig,
              indexedAtMillis = now,
              lastError = null,
            )
          )
          indexedCount++
        } else {
          repo.upsertPhotoEmbedding(
            PhotoEmbeddingEntity(
              memoryId = memory.id,
              ownerUid = ownerUid,
              fileUri = uri,
              embeddingBlob = ByteArray(0),
              embeddingDim = 0,
              modelVersion = modelVersion,
              indexStatus = PhotoIndexStatus.FAILED,
              contentSignature = sig,
              indexedAtMillis = now,
              lastError = "Gagal mengekstrak fitur gambar lokal.",
            )
          )
        }

        setProgress(
          workDataOf(
            KEY_DONE to (idx + 1),
            KEY_TOTAL to total,
            KEY_INDEXED_COUNT to indexedCount,
          )
        )
        yield()
      }
      Result.success(workDataOf(KEY_INDEXED_COUNT to indexedCount))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Vision indexing worker error", e)
      Result.retry()
    } finally {
      engine.unloadVisionEncoder()
    }
  }

  companion object {
    private const val TAG = "VisionIndexWorker"
    const val KEY_OWNER_UID = "owner_uid"
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_INDEXED_COUNT = "indexed_count"

    fun uniqueWorkName(ownerUid: String): String = "evoria_vision_index_$ownerUid"

    fun enqueue(
      context: Context,
      ownerUid: String,
      requiresBatteryNotLow: Boolean = false,
      replaceExisting: Boolean = false,
    ) {
      val constraints =
        Constraints.Builder()
          .setRequiresBatteryNotLow(requiresBatteryNotLow)
          .build()
      val request =
        OneTimeWorkRequestBuilder<VisionIndexWorker>()
          .setInputData(workDataOf(KEY_OWNER_UID to ownerUid))
          .setConstraints(constraints)
          .build()
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniqueWork(
          uniqueWorkName(ownerUid),
          if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
          request,
        )
    }
  }
}
