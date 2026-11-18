package com.example.ml

import android.content.Context
import android.net.Uri
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.PhotoEmbeddingEntity
import com.example.data.local.PhotoIndexStatus
import java.io.File

data class IndexingStats(
  val totalPhotoCount: Int = 0,
  val indexedCount: Int = 0,
  val pendingCount: Int = 0,
  val failedCount: Int = 0,
) {
  val progressFraction: Float
    get() = if (totalPhotoCount <= 0) 0f else (indexedCount.toFloat() / totalPhotoCount.toFloat()).coerceIn(0f, 1f)
}

/**
 * Shared process-wide holder for ModelManager and OnnxClipEmbeddingEngine so
 * foreground searches and WorkManager background indexing never allocate duplicate ONNX sessions.
 */
object EvoriaVisionRuntime {

  @Volatile private var modelManagerInstance: ModelManager? = null
  @Volatile private var embeddingEngineInstance: OnnxClipEmbeddingEngine? = null

  fun modelManager(context: Context): ModelManager {
    return modelManagerInstance
      ?: synchronized(this) {
        modelManagerInstance ?: ModelManager(context.applicationContext).also { modelManagerInstance = it }
      }
  }

  fun embeddingEngine(context: Context): OnnxClipEmbeddingEngine {
    return embeddingEngineInstance
      ?: synchronized(this) {
        val mm = modelManager(context)
        embeddingEngineInstance
          ?: OnnxClipEmbeddingEngine(context.applicationContext, mm).also { embeddingEngineInstance = it }
      }
  }

  fun isIndexablePhoto(memory: LocalMemoryEntity): Boolean {
    val uri = memory.mediaUri?.trim().orEmpty()
    if (uri.isEmpty()) return false
    val type = memory.mediaType.trim().lowercase()
    return type != "video" && type != "story"
  }

  fun computeContentSignature(context: Context, uriString: String?): String {
    val raw = uriString?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val uri = Uri.parse(raw)
    if (uri.scheme == null || uri.scheme.equals("file", ignoreCase = true)) {
      val f = uri.path?.let { File(it) }
      if (f != null && f.exists()) {
        return "${f.length()}_${f.lastModified()}"
      }
    }
    return raw.hashCode().toString()
  }

  fun computeIndexingStats(
    memories: List<LocalMemoryEntity>,
    embeddings: List<PhotoEmbeddingEntity>,
    modelVersion: String = ModelManager.MODEL_VERSION,
  ): IndexingStats {
    val photos = memories.filter(::isIndexablePhoto)
    if (photos.isEmpty()) return IndexingStats()

    val byMemoryId = embeddings.associateBy { it.memoryId }
    var indexed = 0
    var failed = 0
    var pending = 0

    for (photo in photos) {
      val emb = byMemoryId[photo.id]
      when {
        emb != null && emb.indexStatus == PhotoIndexStatus.INDEXED && emb.modelVersion == modelVersion -> indexed++
        emb != null && emb.indexStatus == PhotoIndexStatus.FAILED && emb.modelVersion == modelVersion -> failed++
        else -> pending++
      }
    }
    return IndexingStats(
      totalPhotoCount = photos.size,
      indexedCount = indexed,
      pendingCount = pending,
      failedCount = failed,
    )
  }
}
