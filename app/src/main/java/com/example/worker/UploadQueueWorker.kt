package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.data.local.EvoriaDatabase
import com.example.data.local.LocalNotificationEntity
import com.example.data.local.SyncCheckpointEntity
import com.example.data.remote.EvoriaCloudRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class UploadQueueWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
    val dao = EvoriaDatabase.getInstance(applicationContext).evoriaDao()
    val job = dao.getUploadJob(jobId) ?: return Result.success()

    val now = System.currentTimeMillis()
    dao.upsertUploadJob(
      job.copy(
        status = "processing",
        attemptCount = job.attemptCount + 1,
        updatedAtMillis = now,
      )
    )

    val memory = dao.getMemoryById(job.memoryId)
    if (memory == null) {
      dao.upsertUploadJob(job.copy(status = "failed", lastError = "Kenangan lokal tidak ditemukan.", updatedAtMillis = now))
      return Result.failure()
    }

    // If visibility is "private", local-first persistence is already complete!
    if (job.targetVisibility != "shared") {
      dao.upsertMemory(memory.copy(syncState = "local_only", updatedAtMillis = System.currentTimeMillis()))
      dao.upsertUploadJob(job.copy(status = "completed", attemptCount = job.attemptCount + 1, lastError = null, updatedAtMillis = System.currentTimeMillis()))
      return Result.success()
    }

    // If shared, sync metadata to linked couple sanctuary in Firestore if authenticated & linked
    val currentUser = Firebase.auth.currentUser
    if (currentUser == null) {
      dao.upsertUploadJob(
        job.copy(
          status = "failed",
          attemptCount = job.attemptCount + 1,
          lastError = "Masuk akun Google diperlukan untuk menyinkronkan kenangan bersama.",
          updatedAtMillis = System.currentTimeMillis(),
        )
      )
      return Result.retry()
    }

    return try {
      val dbId = applicationContext.getString(R.string.firestore_database_id)
      val cloud = EvoriaCloudRepository(FirebaseFirestore.getInstance(dbId), Firebase.auth)
      val couple = cloud.observeActiveCouple(currentUser.uid).first()
      if (couple == null) {
        dao.upsertMemory(memory.copy(syncState = "local_only", updatedAtMillis = System.currentTimeMillis()))
        dao.upsertUploadJob(
          job.copy(
            status = "failed",
            attemptCount = job.attemptCount + 1,
            lastError = "Belum terhubung dengan pasangan. Disimpan di perangkat.",
            updatedAtMillis = System.currentTimeMillis(),
          )
        )
        return Result.failure()
      }

      val publish =
        cloud.publishSharedMemory(
          coupleId = couple.id,
          memberUids = couple.memberUids,
          memoryId = memory.id,
          albumId = memory.albumId,
          title = memory.title,
          caption = memory.caption,
          story = memory.story,
          locationName = memory.locationName,
          mediaType = memory.mediaType,
          memoryDateMillis = memory.memoryDateMillis,
          isFavorite = memory.isFavorite,
          tags = memory.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        )

      if (publish.isSuccess) {
        val doneAt = System.currentTimeMillis()
        dao.upsertMemory(memory.copy(syncState = "synced", updatedAtMillis = doneAt))
        dao.upsertUploadJob(job.copy(status = "completed", attemptCount = job.attemptCount + 1, lastError = null, updatedAtMillis = doneAt))
        dao.upsertSyncCheckpoint(
          SyncCheckpointEntity(
            lastSyncMillis = doneAt,
            statusMessage = "Metadata kenangan bersama tersinkronisasi.",
          )
        )
        dao.insertNotification(
          LocalNotificationEntity(
            id = "notif_${UUID.randomUUID().toString().take(8)}",
            kind = "upload",
            title = "Kenangan tersinkronisasi",
            body = "\"${memory.title}\" siap dilihat di ruang bersama.",
            createdAtMillis = doneAt,
          )
        )
        Result.success()
      } else {
        val err = publish.exceptionOrNull()?.localizedMessage ?: "Gagal menyinkronkan ke cloud."
        dao.upsertUploadJob(
          job.copy(
            status = "failed",
            attemptCount = job.attemptCount + 1,
            lastError = err,
            updatedAtMillis = System.currentTimeMillis(),
          )
        )
        if (runAttemptCount < 4) Result.retry() else Result.failure()
      }
    } catch (e: Exception) {
      dao.upsertUploadJob(
        job.copy(
          status = "failed",
          attemptCount = job.attemptCount + 1,
          lastError = e.localizedMessage ?: "Kesalahan antrean unggahan.",
          updatedAtMillis = System.currentTimeMillis(),
        )
      )
      if (runAttemptCount < 4) Result.retry() else Result.failure()
    }
  }

  companion object {
    const val KEY_JOB_ID = "job_id"

    fun enqueue(context: Context, jobId: String, requiresNetwork: Boolean) {
      val constraints =
        Constraints.Builder()
          .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
          .build()
      val request =
        OneTimeWorkRequestBuilder<UploadQueueWorker>()
          .setInputData(workDataOf(KEY_JOB_ID to jobId))
          .setConstraints(constraints)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
          .build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork("evoria_upload_$jobId", ExistingWorkPolicy.REPLACE, request)
    }
  }
}
