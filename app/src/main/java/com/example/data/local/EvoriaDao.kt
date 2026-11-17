package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvoriaDao {

  // --- Albums ---
  @Query("SELECT * FROM albums WHERE ownerUid = :uid ORDER BY updatedAtMillis DESC")
  fun observeAlbums(uid: String): Flow<List<LocalAlbumEntity>>

  @Query("SELECT * FROM albums WHERE ownerUid = :uid ORDER BY updatedAtMillis DESC")
  suspend fun getAlbumsSnapshot(uid: String): List<LocalAlbumEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAlbum(album: LocalAlbumEntity)

  @Query("DELETE FROM albums WHERE id = :albumId")
  suspend fun deleteAlbum(albumId: String)

  // --- Memories ---
  @Query("SELECT * FROM memories WHERE ownerUid = :uid ORDER BY memoryDateMillis DESC")
  fun observeMemories(uid: String): Flow<List<LocalMemoryEntity>>

  @Query("SELECT * FROM memories WHERE ownerUid = :uid ORDER BY memoryDateMillis DESC")
  suspend fun getMemoriesSnapshot(uid: String): List<LocalMemoryEntity>

  @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
  suspend fun getMemoryById(id: String): LocalMemoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMemory(memory: LocalMemoryEntity)

  @Query("UPDATE memories SET isFavorite = :fav, updatedAtMillis = :now WHERE id = :id")
  suspend fun setMemoryFavorite(id: String, fav: Boolean, now: Long)

  @Query("DELETE FROM memories WHERE id = :memoryId")
  suspend fun deleteMemory(memoryId: String)

  // --- Upload Jobs ---
  @Query("SELECT * FROM upload_jobs ORDER BY createdAtMillis DESC")
  fun observeUploadJobs(): Flow<List<UploadJobEntity>>

  @Query("SELECT * FROM upload_jobs WHERE id = :jobId LIMIT 1")
  suspend fun getUploadJob(jobId: String): UploadJobEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertUploadJob(job: UploadJobEntity)

  @Query("DELETE FROM upload_jobs WHERE id = :jobId")
  suspend fun deleteUploadJob(jobId: String)

  // --- Journals ---
  @Query("SELECT * FROM journals WHERE ownerUid = :uid ORDER BY updatedAtMillis DESC")
  fun observeJournals(uid: String): Flow<List<JournalEntity>>

  @Query("SELECT * FROM journals WHERE ownerUid = :uid ORDER BY updatedAtMillis DESC")
  suspend fun getJournalsSnapshot(uid: String): List<JournalEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertJournal(journal: JournalEntity)

  @Query("DELETE FROM journals WHERE id = :id")
  suspend fun deleteJournal(id: String)

  // --- Letters ---
  @Query("SELECT * FROM letters WHERE ownerUid = :uid ORDER BY updatedAtMillis DESC")
  fun observeLetters(uid: String): Flow<List<LetterEntity>>

  @Query("SELECT * FROM letters WHERE ownerUid = :uid ORDER BY updatedAtMillis DESC")
  suspend fun getLettersSnapshot(uid: String): List<LetterEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertLetter(letter: LetterEntity)

  @Query("DELETE FROM letters WHERE id = :id")
  suspend fun deleteLetter(id: String)

  // --- Moods ---
  @Query("SELECT * FROM moods WHERE ownerUid = :uid ORDER BY recordedAtMillis DESC")
  fun observeMoods(uid: String): Flow<List<MoodEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMood(mood: MoodEntity)

  // --- Reflection Answers ---
  @Query("SELECT * FROM reflection_answers WHERE ownerUid = :uid ORDER BY answeredAtMillis DESC")
  fun observeReflectionAnswers(uid: String): Flow<List<ReflectionAnswerEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReflectionAnswer(answer: ReflectionAnswerEntity)

  // --- Time Capsules ---
  @Query("SELECT * FROM time_capsules WHERE ownerUid = :uid ORDER BY unlockAtMillis ASC")
  fun observeTimeCapsules(uid: String): Flow<List<TimeCapsuleEntity>>

  @Query("SELECT * FROM time_capsules WHERE ownerUid = :uid ORDER BY unlockAtMillis ASC")
  suspend fun getTimeCapsulesSnapshot(uid: String): List<TimeCapsuleEntity>

  @Query("SELECT * FROM time_capsules WHERE id = :id LIMIT 1")
  suspend fun getTimeCapsuleById(id: String): TimeCapsuleEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTimeCapsule(capsule: TimeCapsuleEntity)

  @Query("DELETE FROM time_capsules WHERE id = :id")
  suspend fun deleteTimeCapsule(id: String)

  // --- Music Playlists & Tracks ---
  @Query("SELECT * FROM playlists ORDER BY createdAtMillis DESC")
  fun observePlaylists(): Flow<List<PlaylistEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPlaylist(playlist: PlaylistEntity)

  @Query("DELETE FROM playlists WHERE id = :id")
  suspend fun deletePlaylist(id: String)

  @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
  suspend fun deleteTracksForPlaylist(playlistId: String)

  @Query("SELECT * FROM tracks ORDER BY sortOrder ASC")
  fun observeAllTracks(): Flow<List<TrackEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertTrack(track: TrackEntity)

  @Query("DELETE FROM tracks WHERE id = :id")
  suspend fun deleteTrack(id: String)

  // --- Notifications ---
  @Query("SELECT * FROM local_notifications ORDER BY createdAtMillis DESC")
  fun observeLocalNotifications(): Flow<List<LocalNotificationEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotification(notification: LocalNotificationEntity)

  @Query("UPDATE local_notifications SET isRead = 1 WHERE id = :id")
  suspend fun markNotificationRead(id: String)

  @Query("UPDATE local_notifications SET isRead = 1")
  suspend fun markAllNotificationsRead()

  // --- Sync Checkpoint ---
  @Query("SELECT * FROM sync_checkpoints WHERE id = 'primary' LIMIT 1")
  fun observeSyncCheckpoint(): Flow<SyncCheckpointEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSyncCheckpoint(checkpoint: SyncCheckpointEntity)

  // --- Wipe User Data ---
  @Query("DELETE FROM albums WHERE ownerUid = :uid")
  suspend fun clearUserAlbums(uid: String)

  @Query("DELETE FROM memories WHERE ownerUid = :uid")
  suspend fun clearUserMemories(uid: String)

  @Query("DELETE FROM journals WHERE ownerUid = :uid")
  suspend fun clearUserJournals(uid: String)

  @Query("DELETE FROM letters WHERE ownerUid = :uid")
  suspend fun clearUserLetters(uid: String)

  @Query("DELETE FROM moods WHERE ownerUid = :uid")
  suspend fun clearUserMoods(uid: String)

  @Query("DELETE FROM reflection_answers WHERE ownerUid = :uid")
  suspend fun clearUserReflections(uid: String)

  @Query("DELETE FROM time_capsules WHERE ownerUid = :uid")
  suspend fun clearUserCapsules(uid: String)

  @Query("DELETE FROM upload_jobs")
  suspend fun clearAllUploadJobs()

  @Query("DELETE FROM local_notifications")
  suspend fun clearAllLocalNotifications()
}
