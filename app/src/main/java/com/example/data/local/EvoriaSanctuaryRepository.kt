package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.util.MediaStorageHelper
import com.example.util.PersistedMedia
import com.example.worker.UploadQueueWorker
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupAlbumJson(
  val id: String,
  val title: String,
  val description: String,
  val visibility: String,
  val soundtrackTitle: String? = null,
  val createdAtMillis: Long,
)

@Serializable
data class BackupMemoryJson(
  val id: String,
  val albumId: String? = null,
  val title: String,
  val caption: String,
  val story: String,
  val locationName: String,
  val mediaType: String,
  val visibility: String,
  val isFavorite: Boolean,
  val tagsCsv: String,
  val memoryDateMillis: Long,
)

@Serializable
data class BackupJournalJson(
  val id: String,
  val title: String,
  val body: String,
  val visibility: String,
  val moodTag: String,
  val isDraft: Boolean,
  val createdAtMillis: Long,
)

@Serializable
data class BackupLetterJson(
  val id: String,
  val title: String,
  val body: String,
  val recipientName: String,
  val isSealed: Boolean,
  val isDraft: Boolean,
  val isShared: Boolean,
  val createdAtMillis: Long,
)

@Serializable
data class BackupCapsuleJson(
  val id: String,
  val title: String,
  val body: String,
  val unlockAtMillis: Long,
  val visibility: String,
  val isSealed: Boolean,
  val createdAtMillis: Long,
)

@Serializable
data class EvoriaBackupPayload(
  val version: Int = 1,
  val exportedAtMillis: Long,
  val albums: List<BackupAlbumJson>,
  val memories: List<BackupMemoryJson>,
  val journals: List<BackupJournalJson>,
  val letters: List<BackupLetterJson>,
  val capsules: List<BackupCapsuleJson>,
)

class EvoriaSanctuaryRepository(
  private val context: Context,
  private val dao: EvoriaDao,
) {
  private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

  fun observeAlbums(uid: String): Flow<List<LocalAlbumEntity>> = dao.observeAlbums(uid)

  fun observeMemories(uid: String): Flow<List<LocalMemoryEntity>> = dao.observeMemories(uid)

  fun observeUploadJobs(): Flow<List<UploadJobEntity>> = dao.observeUploadJobs()

  fun observeJournals(uid: String): Flow<List<JournalEntity>> = dao.observeJournals(uid)

  fun observeLetters(uid: String): Flow<List<LetterEntity>> = dao.observeLetters(uid)

  fun observeMoods(uid: String): Flow<List<MoodEntity>> = dao.observeMoods(uid)

  fun observeReflectionAnswers(uid: String): Flow<List<ReflectionAnswerEntity>> =
    dao.observeReflectionAnswers(uid)

  fun observeTimeCapsules(uid: String): Flow<List<TimeCapsuleEntity>> = dao.observeTimeCapsules(uid)

  fun observePlaylists(): Flow<List<PlaylistEntity>> = dao.observePlaylists()

  fun observeTracks(): Flow<List<TrackEntity>> = dao.observeAllTracks()

  fun observeLocalNotifications(): Flow<List<LocalNotificationEntity>> =
    dao.observeLocalNotifications()

  fun observeSyncCheckpoint(): Flow<SyncCheckpointEntity?> = dao.observeSyncCheckpoint()

  suspend fun persistMediaUri(uri: Uri): Result<PersistedMedia> =
    MediaStorageHelper.persistUriToAppStorage(context, uri)

  fun resolveDisplayName(uri: Uri): String =
    MediaStorageHelper.queryDisplayName(context, uri)

  fun getLocalMediaSizeFormatted(): String =
    MediaStorageHelper.formatBytes(MediaStorageHelper.calculateStorageBytes(context))

  suspend fun saveAlbum(album: LocalAlbumEntity) {
    dao.upsertAlbum(album)
  }

  suspend fun deleteAlbum(albumId: String) {
    dao.deleteAlbum(albumId)
  }

  suspend fun saveMemoryAndEnqueue(
    memory: LocalMemoryEntity,
    enqueueUploadJob: Boolean = true,
  ) {
    dao.upsertMemory(memory)
    if (enqueueUploadJob && memory.mediaUri != null) {
      val jobId = "job_${UUID.randomUUID().toString().take(10)}"
      val now = System.currentTimeMillis()
      dao.upsertUploadJob(
        UploadJobEntity(
          id = jobId,
          memoryId = memory.id,
          mediaUri = memory.mediaUri,
          targetAlbumId = memory.albumId,
          targetVisibility = memory.visibility,
          caption = memory.caption,
          status = "queued",
          attemptCount = 0,
          createdAtMillis = now,
          updatedAtMillis = now,
        )
      )
      UploadQueueWorker.enqueue(
        context = context,
        jobId = jobId,
        requiresNetwork = memory.visibility == "shared",
      )
    }
  }

  suspend fun retryUploadJob(job: UploadJobEntity) {
    val now = System.currentTimeMillis()
    dao.upsertUploadJob(job.copy(status = "queued", lastError = null, updatedAtMillis = now))
    UploadQueueWorker.enqueue(
      context = context,
      jobId = job.id,
      requiresNetwork = job.targetVisibility == "shared",
    )
  }

  suspend fun deleteUploadJob(jobId: String) = dao.deleteUploadJob(jobId)

  suspend fun toggleFavorite(memoryId: String, favorite: Boolean) {
    dao.setMemoryFavorite(memoryId, favorite, System.currentTimeMillis())
  }

  suspend fun deleteMemory(memory: LocalMemoryEntity) {
    MediaStorageHelper.deletePersistedFile(memory.mediaUri)
    dao.deleteMemory(memory.id)
  }

  suspend fun saveJournal(journal: JournalEntity) = dao.upsertJournal(journal)

  suspend fun deleteJournal(id: String) = dao.deleteJournal(id)

  suspend fun saveLetter(letter: LetterEntity) = dao.upsertLetter(letter)

  suspend fun deleteLetter(id: String) = dao.deleteLetter(id)

  suspend fun recordMood(mood: MoodEntity) = dao.insertMood(mood)

  suspend fun saveReflectionAnswer(answer: ReflectionAnswerEntity) =
    dao.insertReflectionAnswer(answer)

  suspend fun saveTimeCapsule(capsule: TimeCapsuleEntity) = dao.upsertTimeCapsule(capsule)

  suspend fun deleteTimeCapsule(id: String) = dao.deleteTimeCapsule(id)

  /**
   * Data-layer enforcement of Time Capsule lock rules:
   * returns null body when sealed and unlockAtMillis is still in the future.
   */
  suspend fun readTimeCapsuleEnforced(
    id: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): TimeCapsuleEntity? {
    val cap = dao.getTimeCapsuleById(id) ?: return null
    return if (cap.isUnlocked(nowMillis)) cap
    else cap.copy(body = "", attachmentUri = null)
  }

  suspend fun savePlaylist(playlist: PlaylistEntity) = dao.upsertPlaylist(playlist)

  suspend fun deletePlaylist(id: String) {
    dao.deleteTracksForPlaylist(id)
    dao.deletePlaylist(id)
  }

  suspend fun saveTrack(track: TrackEntity) = dao.upsertTrack(track)

  suspend fun deleteTrack(id: String) = dao.deleteTrack(id)

  suspend fun addLocalNotification(kind: String, title: String, body: String) {
    dao.insertNotification(
      LocalNotificationEntity(
        id = "ln_${UUID.randomUUID().toString().take(8)}",
        kind = kind,
        title = title,
        body = body,
        isRead = false,
        createdAtMillis = System.currentTimeMillis(),
      )
    )
  }

  suspend fun markNotificationRead(id: String) = dao.markNotificationRead(id)

  suspend fun markAllNotificationsRead() = dao.markAllNotificationsRead()

  suspend fun updateSyncCheckpoint(syncMillis: Long? = null, backupMillis: Long? = null, message: String) {
    val current = SyncCheckpointEntity(
      lastSyncMillis = syncMillis ?: System.currentTimeMillis(),
      lastBackupMillis = backupMillis ?: 0L,
      statusMessage = message,
    )
    dao.upsertSyncCheckpoint(current)
  }

  suspend fun exportBackupJson(uid: String): String {
    val albums = dao.getAlbumsSnapshot(uid)
    val memories = dao.getMemoriesSnapshot(uid)
    val journals = dao.getJournalsSnapshot(uid)
    val letters = dao.getLettersSnapshot(uid)
    val capsules = dao.getTimeCapsulesSnapshot(uid)
    val payload =
      EvoriaBackupPayload(
        exportedAtMillis = System.currentTimeMillis(),
        albums =
          albums.map {
            BackupAlbumJson(
              id = it.id,
              title = it.title,
              description = it.description,
              visibility = it.visibility,
              soundtrackTitle = it.soundtrackTitle,
              createdAtMillis = it.createdAtMillis,
            )
          },
        memories =
          memories.map {
            BackupMemoryJson(
              id = it.id,
              albumId = it.albumId,
              title = it.title,
              caption = it.caption,
              story = it.story,
              locationName = it.locationName,
              mediaType = it.mediaType,
              visibility = it.visibility,
              isFavorite = it.isFavorite,
              tagsCsv = it.tagsCsv,
              memoryDateMillis = it.memoryDateMillis,
            )
          },
        journals =
          journals.map {
            BackupJournalJson(
              id = it.id,
              title = it.title,
              body = it.body,
              visibility = it.visibility,
              moodTag = it.moodTag,
              isDraft = it.isDraft,
              createdAtMillis = it.createdAtMillis,
            )
          },
        letters =
          letters.map {
            BackupLetterJson(
              id = it.id,
              title = it.title,
              body = it.body,
              recipientName = it.recipientName,
              isSealed = it.isSealed,
              isDraft = it.isDraft,
              isShared = it.isShared,
              createdAtMillis = it.createdAtMillis,
            )
          },
        capsules =
          capsules.map {
            BackupCapsuleJson(
              id = it.id,
              title = it.title,
              body = it.body,
              unlockAtMillis = it.unlockAtMillis,
              visibility = it.visibility,
              isSealed = it.isSealed,
              createdAtMillis = it.createdAtMillis,
            )
          },
      )
    return json.encodeToString(payload)
  }

  suspend fun restoreBackupJson(uid: String, rawJson: String): Result<Int> = runCatching {
    val payload = json.decodeFromString<EvoriaBackupPayload>(rawJson)
    var restored = 0
    val now = System.currentTimeMillis()
    payload.albums.forEach {
      dao.upsertAlbum(
        LocalAlbumEntity(
          id = it.id,
          ownerUid = uid,
          title = it.title,
          description = it.description,
          visibility = it.visibility,
          soundtrackTitle = it.soundtrackTitle,
          createdAtMillis = it.createdAtMillis,
          updatedAtMillis = now,
        )
      )
      restored++
    }
    payload.memories.forEach {
      dao.upsertMemory(
        LocalMemoryEntity(
          id = it.id,
          ownerUid = uid,
          albumId = it.albumId,
          title = it.title,
          caption = it.caption,
          story = it.story,
          locationName = it.locationName,
          mediaType = it.mediaType,
          visibility = it.visibility,
          isFavorite = it.isFavorite,
          tagsCsv = it.tagsCsv,
          memoryDateMillis = it.memoryDateMillis,
          createdAtMillis = now,
          updatedAtMillis = now,
          syncState = "synced",
        )
      )
      restored++
    }
    payload.journals.forEach {
      dao.upsertJournal(
        JournalEntity(
          id = it.id,
          ownerUid = uid,
          title = it.title,
          body = it.body,
          visibility = it.visibility,
          moodTag = it.moodTag,
          isDraft = it.isDraft,
          createdAtMillis = it.createdAtMillis,
          updatedAtMillis = now,
        )
      )
      restored++
    }
    payload.letters.forEach {
      dao.upsertLetter(
        LetterEntity(
          id = it.id,
          ownerUid = uid,
          title = it.title,
          body = it.body,
          recipientName = it.recipientName,
          isSealed = it.isSealed,
          isDraft = it.isDraft,
          isShared = it.isShared,
          createdAtMillis = it.createdAtMillis,
          updatedAtMillis = now,
        )
      )
      restored++
    }
    payload.capsules.forEach {
      dao.upsertTimeCapsule(
        TimeCapsuleEntity(
          id = it.id,
          ownerUid = uid,
          title = it.title,
          body = it.body,
          unlockAtMillis = it.unlockAtMillis,
          visibility = it.visibility,
          isSealed = it.isSealed,
          createdAtMillis = it.createdAtMillis,
        )
      )
      restored++
    }
    restored
  }

  suspend fun clearAllUserLocalData(uid: String) {
    val albums = dao.getAlbumsSnapshot(uid)
    val memories = dao.getMemoriesSnapshot(uid)
    val journals = dao.getJournalsSnapshot(uid)
    val capsules = dao.getTimeCapsulesSnapshot(uid)
    albums.forEach { MediaStorageHelper.deletePersistedFile(it.coverUri) }
    memories.forEach { MediaStorageHelper.deletePersistedFile(it.mediaUri) }
    journals.forEach { MediaStorageHelper.deletePersistedFile(it.attachmentUri) }
    capsules.forEach { MediaStorageHelper.deletePersistedFile(it.attachmentUri) }
    dao.clearUserAlbums(uid)
    dao.clearUserMemories(uid)
    dao.clearUserJournals(uid)
    dao.clearUserLetters(uid)
    dao.clearUserMoods(uid)
    dao.clearUserReflections(uid)
    dao.clearUserCapsules(uid)
    dao.clearAllUploadJobs()
    dao.clearAllLocalNotifications()
  }
}
