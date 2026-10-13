package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MovieEntity::class,
        DownloadEntity::class,
        UploadStateEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MovieRoomDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun downloadDao(): DownloadDao
    abstract fun uploadStateDao(): UploadStateDao

    companion object {
        @Volatile
        private var INSTANCE: MovieRoomDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): MovieRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MovieRoomDatabase::class.java,
                    "movieroom.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
