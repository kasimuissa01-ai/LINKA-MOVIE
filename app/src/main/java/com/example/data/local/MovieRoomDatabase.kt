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
    version = 6,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val movieColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`movies`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0) {
                            movieColumns.add(cursor.getString(nameIndex))
                        }
                    }
                }
                if (!movieColumns.contains("country")) {
                    db.execSQL("ALTER TABLE `movies` ADD COLUMN `country` TEXT NOT NULL DEFAULT 'USA'")
                }
                if (!movieColumns.contains("isTrending")) {
                    db.execSQL("ALTER TABLE `movies` ADD COLUMN `isTrending` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val downloadColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`downloads`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0) {
                            downloadColumns.add(cursor.getString(nameIndex))
                        }
                    }
                }
                if (!downloadColumns.contains("isFileVerified")) {
                    db.execSQL("ALTER TABLE `downloads` ADD COLUMN `isFileVerified` INTEGER NOT NULL DEFAULT 0")
                }
                if (!downloadColumns.contains("lastVerifiedAt")) {
                    db.execSQL("ALTER TABLE `downloads` ADD COLUMN `lastVerifiedAt` INTEGER NOT NULL DEFAULT 0")
                }
                // Normalize any legacy file:// URI strings into filesystem paths so File checks and Media3 resolve cleanly
                db.execSQL(
                    "UPDATE `downloads` SET `localFilePath` = SUBSTR(`localFilePath`, 8) WHERE `localFilePath` LIKE 'file://%'"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_downloads_userId_movieId` ON `downloads` (`userId`, `movieId`)"
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        )

        fun getInstance(context: Context): MovieRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MovieRoomDatabase::class.java,
                    "movieroom.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
