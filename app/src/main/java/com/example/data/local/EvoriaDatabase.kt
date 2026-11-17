package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    LocalAlbumEntity::class,
    KenanganCollectionEntity::class,
    LocalMemoryEntity::class,
    UploadJobEntity::class,
    JournalEntity::class,
    LetterEntity::class,
    MoodEntity::class,
    ReflectionAnswerEntity::class,
    TimeCapsuleEntity::class,
    PlaylistEntity::class,
    TrackEntity::class,
    LocalNotificationEntity::class,
    SyncCheckpointEntity::class,
  ],
  version = 6,
  exportSchema = false,
)
abstract class EvoriaDatabase : RoomDatabase() {
  abstract fun evoriaDao(): EvoriaDao

  companion object {
    @Volatile private var INSTANCE: EvoriaDatabase? = null

    private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE playlists ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'local'")
          db.execSQL("ALTER TABLE playlists ADD COLUMN externalUrl TEXT")
          db.execSQL("ALTER TABLE playlists ADD COLUMN coverUrl TEXT")
          db.execSQL("ALTER TABLE tracks ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'local'")
          db.execSQL("ALTER TABLE tracks ADD COLUMN externalUrl TEXT")
        }
      }

    internal val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE tracks ADD COLUMN coverUrl TEXT")
        }
      }

    internal val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE tracks ADD COLUMN youtubeVideoId TEXT")
        }
      }

    internal val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("CREATE INDEX IF NOT EXISTS index_albums_ownerUid ON albums(ownerUid)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_ownerUid ON memories(ownerUid)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_albumId ON memories(albumId)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_journals_ownerUid ON journals(ownerUid)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_letters_ownerUid ON letters(ownerUid)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_moods_ownerUid ON moods(ownerUid)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_reflection_answers_ownerUid ON reflection_answers(ownerUid)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_time_capsules_ownerUid ON time_capsules(ownerUid)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_playlistId ON tracks(playlistId)")
        }
      }

    internal val MIGRATION_5_6 =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS kenangan_collections (
              id TEXT NOT NULL PRIMARY KEY,
              ownerUid TEXT NOT NULL,
              title TEXT NOT NULL,
              description TEXT NOT NULL,
              albumIdsCsv TEXT NOT NULL,
              coverUri TEXT,
              visibility TEXT NOT NULL,
              createdAtMillis INTEGER NOT NULL,
              updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_kenangan_collections_ownerUid ON kenangan_collections(ownerUid)"
          )
        }
      }

    fun getInstance(context: Context): EvoriaDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                EvoriaDatabase::class.java,
                "evoria_sanctuary.db",
              )
              .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
              )
              .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
