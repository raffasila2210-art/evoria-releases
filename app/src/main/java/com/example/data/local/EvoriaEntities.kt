package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "albums",
  indices = [Index(value = ["ownerUid"])],
)
data class LocalAlbumEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val title: String,
  val description: String = "",
  val coverUri: String? = null,
  val visibility: String = "private", // "private" | "shared"
  val soundtrackTitle: String? = null,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "kenangan_collections",
  indices = [Index(value = ["ownerUid"])],
)
data class KenanganCollectionEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val title: String,
  val description: String = "",
  val albumIdsCsv: String = "",
  val coverUri: String? = null,
  val visibility: String = "private", // "private" | "shared"
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
) {
  fun albumIdList(): List<String> =
    albumIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

@Entity(
  tableName = "memories",
  indices = [
    Index(value = ["ownerUid"]),
    Index(value = ["albumId"]),
  ],
)
data class LocalMemoryEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val albumId: String? = null,
  val title: String,
  val caption: String = "",
  val story: String = "",
  val locationName: String = "",
  val mediaUri: String? = null,
  val thumbnailUri: String? = null,
  val mediaType: String = "photo", // "photo" | "video" | "story"
  val visibility: String = "private", // "private" | "shared"
  val isFavorite: Boolean = false,
  val tagsCsv: String = "",
  val memoryDateMillis: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val syncState: String = "local_only", // "local_only" | "pending" | "synced"
)

@Entity(tableName = "upload_jobs")
data class UploadJobEntity(
  @PrimaryKey val id: String,
  val memoryId: String,
  val mediaUri: String,
  val targetAlbumId: String? = null,
  val targetVisibility: String = "private",
  val caption: String = "",
  val status: String = "queued", // "queued" | "processing" | "completed" | "failed"
  val attemptCount: Int = 0,
  val lastError: String? = null,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "journals",
  indices = [Index(value = ["ownerUid"])],
)
data class JournalEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val title: String,
  val body: String,
  val attachmentUri: String? = null,
  val visibility: String = "private",
  val moodTag: String = "tenang",
  val isDraft: Boolean = false,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "letters",
  indices = [Index(value = ["ownerUid"])],
)
data class LetterEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val title: String,
  val body: String,
  val recipientName: String = "Kamu",
  val isSealed: Boolean = true,
  val isDraft: Boolean = false,
  val isShared: Boolean = false,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "moods",
  indices = [Index(value = ["ownerUid"])],
)
data class MoodEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val moodKey: String, // "tenang" | "hangat" | "rindu" | "syukur" | "lelah" | "bahagia"
  val note: String = "",
  val recordedAtMillis: Long,
)

@Entity(
  tableName = "reflection_answers",
  indices = [Index(value = ["ownerUid"])],
)
data class ReflectionAnswerEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val promptId: String,
  val promptText: String,
  val answerText: String,
  val answeredAtMillis: Long,
)

@Entity(
  tableName = "time_capsules",
  indices = [Index(value = ["ownerUid"])],
)
data class TimeCapsuleEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val title: String,
  val body: String,
  val attachmentUri: String? = null,
  val unlockAtMillis: Long,
  val visibility: String = "private",
  val isSealed: Boolean = true,
  val createdAtMillis: Long,
) {
  fun isUnlocked(nowMillis: Long = System.currentTimeMillis()): Boolean =
    !isSealed || nowMillis >= unlockAtMillis
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
  @PrimaryKey val id: String,
  val title: String,
  val subtitle: String = "",
  val linkedAlbumId: String? = null,
  val createdAtMillis: Long,
  val sourceType: String = "local", // "local" | "spotify"
  val externalUrl: String? = null,
  val coverUrl: String? = null,
)

@Entity(
  tableName = "tracks",
  indices = [Index(value = ["playlistId"])],
)
data class TrackEntity(
  @PrimaryKey val id: String,
  val playlistId: String,
  val title: String,
  val artist: String,
  val mediaUri: String,
  val durationMs: Long = 0L,
  val sortOrder: Int = 0,
  val linkedMemoryId: String? = null,
  val sourceType: String = "local", // "local" | "spotify"
  val externalUrl: String? = null,
  val coverUrl: String? = null,
  val youtubeVideoId: String? = null,
)

@Entity(tableName = "local_notifications")
data class LocalNotificationEntity(
  @PrimaryKey val id: String,
  val kind: String,
  val title: String,
  val body: String,
  val isRead: Boolean = false,
  val createdAtMillis: Long,
)

@Entity(tableName = "sync_checkpoints")
data class SyncCheckpointEntity(
  @PrimaryKey val id: String = "primary",
  val lastSyncMillis: Long = 0L,
  val lastBackupMillis: Long = 0L,
  val statusMessage: String = "Siap disinkronkan secara lokal",
)

object PhotoIndexStatus {
  const val PENDING = "PENDING"
  const val INDEXED = "INDEXED"
  const val FAILED = "FAILED"
}

@Entity(
  tableName = "photo_embeddings",
  indices = [
    Index(value = ["ownerUid"]),
    Index(value = ["indexStatus"]),
    Index(value = ["modelVersion"]),
  ],
)
data class PhotoEmbeddingEntity(
  @PrimaryKey val memoryId: String,
  val ownerUid: String,
  val fileUri: String,
  val embeddingBlob: ByteArray,
  val embeddingDim: Int,
  val modelVersion: String,
  val indexStatus: String = PhotoIndexStatus.PENDING,
  val contentSignature: String = "",
  val indexedAtMillis: Long = 0L,
  val lastError: String? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PhotoEmbeddingEntity) return false
    return memoryId == other.memoryId &&
      ownerUid == other.ownerUid &&
      fileUri == other.fileUri &&
      embeddingBlob.contentEquals(other.embeddingBlob) &&
      embeddingDim == other.embeddingDim &&
      modelVersion == other.modelVersion &&
      indexStatus == other.indexStatus &&
      contentSignature == other.contentSignature &&
      indexedAtMillis == other.indexedAtMillis &&
      lastError == other.lastError
  }

  override fun hashCode(): Int {
    var result = memoryId.hashCode()
    result = 31 * result + ownerUid.hashCode()
    result = 31 * result + fileUri.hashCode()
    result = 31 * result + embeddingBlob.contentHashCode()
    result = 31 * result + embeddingDim
    result = 31 * result + modelVersion.hashCode()
    result = 31 * result + indexStatus.hashCode()
    result = 31 * result + contentSignature.hashCode()
    result = 31 * result + indexedAtMillis.hashCode()
    result = 31 * result + (lastError?.hashCode() ?: 0)
    return result
  }
}

