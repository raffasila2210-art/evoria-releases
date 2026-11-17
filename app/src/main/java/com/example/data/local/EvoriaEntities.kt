package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
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

@Entity(tableName = "memories")
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

@Entity(tableName = "journals")
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

@Entity(tableName = "letters")
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

@Entity(tableName = "moods")
data class MoodEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val moodKey: String, // "tenang" | "hangat" | "rindu" | "syukur" | "lelah" | "bahagia"
  val note: String = "",
  val recordedAtMillis: Long,
)

@Entity(tableName = "reflection_answers")
data class ReflectionAnswerEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val promptId: String,
  val promptText: String,
  val answerText: String,
  val answeredAtMillis: Long,
)

@Entity(tableName = "time_capsules")
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

@Entity(tableName = "tracks")
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
