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
  version = 4,
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

    fun getInstance(context: Context): EvoriaDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                EvoriaDatabase::class.java,
                "evoria_sanctuary.db",
              )
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
