package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.OfflineDownloadManager
import com.example.data.download.OfflineDownloadRecoveryService
import com.example.data.download.OfflineStoragePaths
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieEntity
import com.example.data.local.MovieRoomDatabase
import com.example.data.remote.CloudflareR2PresignedClient
import com.example.data.repository.MovieRepository
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Movie
import com.example.presentation.screens.isDownloadVerifiedComplete
import com.example.presentation.screens.resolveOfflineMovie
import com.example.util.VideoCacheManager
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineDownloadVerificationTest {

    private lateinit var context: Context
    private lateinit var userDir: File
    private lateinit var database: MovieRoomDatabase
    private lateinit var downloadManager: OfflineDownloadManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        userDir = OfflineStoragePaths.getUserDownloadDir(context, "user_abc")
        userDir.mkdirs()

        database = Room.inMemoryDatabaseBuilder(context, MovieRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloadManager = OfflineDownloadManager(
            downloadDao = database.downloadDao(),
            r2Client = CloudflareR2PresignedClient(),
            context = context,
            userIdProvider = { "user_abc" }
        )
    }

    @After
    fun teardown() {
        database.close()
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        File(destDir, "users").deleteRecursively()
        File(context.filesDir, "users").deleteRecursively()
        File(context.filesDir, "offline_downloads").deleteRecursively()
        destDir.listFiles()?.filter { it.name.startsWith("movie_test_") }?.forEach { it.delete() }
        context.filesDir.listFiles()?.filter { it.name.startsWith("movie_test_") }?.forEach { it.delete() }
        File(context.cacheDir, "movie_test_cached.mp4").delete()
        File(context.cacheDir, "movieroom_video_cache").deleteRecursively()
    }

    private fun createFileWithSize(file: File, sizeBytes: Long) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
    }

    @Test
    fun `incomplete statuses never pass verification even with partial file`() {
        val mp4 = File(userDir, "movie_m1.mp4")
        createFileWithSize(mp4, 2L * 1024L * 1024L)

        listOf(
            DownloadStatus.FAILED,
            DownloadStatus.PAUSED,
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING
        ).forEach { status ->
            val item = DownloadItem(
                id = "user_abc_m1",
                movieId = "m1",
                movieTitle = "Test Movie",
                coverUrl = "",
                localFilePath = mp4.absolutePath,
                progress = 0.5f,
                status = status,
                downloadedBytes = 1024L * 1024L,
                totalBytes = 2L * 1024L * 1024L,
                userId = "user_abc"
            )
            assertFalse("Status $status must be treated as not complete", isDownloadVerifiedComplete(item, context))
        }
    }

    @Test
    fun `completed status with missing or tiny file returns false`() {
        val missingFile = File(userDir, "movie_m2.mp4")
        val missingItem = DownloadItem(
            id = "user_abc_m2",
            movieId = "m2",
            movieTitle = "Missing File Movie",
            coverUrl = "",
            localFilePath = missingFile.absolutePath,
            progress = 1.0f,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = 0L,
            totalBytes = 2L * 1024L * 1024L,
            userId = "user_abc"
        )
        assertFalse(isDownloadVerifiedComplete(missingItem, context))

        val tinyFile = File(userDir, "movie_m3.mp4")
        createFileWithSize(tinyFile, 512L * 1024L)
        val tinyItem = missingItem.copy(id = "user_abc_m3", movieId = "m3", localFilePath = tinyFile.absolutePath)
        assertFalse(isDownloadVerifiedComplete(tinyItem, context))
    }

    @Test
    fun `completed status with verified file on disk returns true and resolves path`() {
        val validFile = File(userDir, "movie_m4.mp4")
        createFileWithSize(validFile, 2L * 1024L * 1024L)

        val item = DownloadItem(
            id = "user_abc_m4",
            movieId = "m4",
            movieTitle = "Valid Offline Movie",
            coverUrl = "",
            localFilePath = "",
            progress = 1.0f,
            status = DownloadStatus.COMPLETED,
            downloadedBytes = validFile.length(),
            totalBytes = validFile.length(),
            userId = "user_abc"
        )

        assertTrue(isDownloadVerifiedComplete(item, context))
        val resolved = resolveOfflineMovie(item, context, emptyList())
        assertEquals(validFile.absolutePath, resolved.videoStreamUrl)
    }

    @Test
    fun `primary offline download targets reside strictly in persistent internal filesDir not cache`() {
        val movieTarget = OfflineStoragePaths.getPrimaryTargetFile(context, "user_abc", "mov_100", null)
        val epTemp = OfflineStoragePaths.getPrimaryTempFile(context, "user_abc", "mov_100", "ep_1")

        assertTrue(
            "Movie target must live inside persistent internal filesDir",
            movieTarget.canonicalPath.startsWith(context.filesDir.canonicalPath)
        )
        assertTrue(
            "Episode temp file must live inside persistent internal filesDir",
            epTemp.canonicalPath.startsWith(context.filesDir.canonicalPath)
        )
        assertFalse("Movie target must never be in cacheDir", OfflineStoragePaths.isInsideCacheDir(context, movieTarget))
        assertFalse("Episode temp file must never be in cacheDir", OfflineStoragePaths.isInsideCacheDir(context, epTemp))
    }

    @Test
    fun `getVerifiedOfflinePlaybackUri promotes cache path to persistent internal storage and preserves legacy files`() = runBlocking {
        // 1. Legacy internal file still resolves cleanly without re-downloading
        val legacyInternalFile = File(context.filesDir, "movie_test_legacy.mp4")
        createFileWithSize(legacyInternalFile, 2L * 1024L * 1024L)

        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "user_abc_test_legacy",
                movieId = "test_legacy",
                movieTitle = "Legacy Internal Movie",
                coverUrl = "",
                localFilePath = "/stale/nonexistent/path/movie_test_legacy.mp4",
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                downloadedBytes = legacyInternalFile.length(),
                totalBytes = legacyInternalFile.length(),
                userId = "user_abc"
            )
        )

        val resolvedUri = downloadManager.getVerifiedOfflinePlaybackUri(
            movieId = "test_legacy",
            episodeId = null,
            userId = "user_abc"
        )
        assertEquals(Uri.fromFile(legacyInternalFile).toString(), resolvedUri)

        // Ensure other users still cannot access another user's offline record
        assertNull(
            downloadManager.getVerifiedOfflinePlaybackUri(
                movieId = "test_legacy",
                episodeId = null,
                userId = "other_user"
            )
        )

        // 2. If a record ever pointed into cacheDir, it is promoted into persistent internal storage
        val accidentalCacheFile = File(context.cacheDir, "movie_test_cached.mp4")
        createFileWithSize(accidentalCacheFile, 2L * 1024L * 1024L)
        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "user_abc_test_cached",
                movieId = "test_cached",
                movieTitle = "Cached Movie",
                coverUrl = "",
                localFilePath = accidentalCacheFile.absolutePath,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                downloadedBytes = accidentalCacheFile.length(),
                totalBytes = accidentalCacheFile.length(),
                userId = "user_abc"
            )
        )

        val promotedUri = downloadManager.getVerifiedOfflinePlaybackUri(
            movieId = "test_cached",
            episodeId = null,
            userId = "user_abc"
        )
        val expectedPersistentFile = OfflineStoragePaths.getPrimaryTargetFile(context, "user_abc", "test_cached", null)
        assertEquals(Uri.fromFile(expectedPersistentFile).toString(), promotedUri)
        assertTrue(expectedPersistentFile.exists())
        assertFalse(OfflineStoragePaths.isInsideCacheDir(context, expectedPersistentFile))
        assertEquals(expectedPersistentFile.absolutePath, database.downloadDao().getDownloadById("user_abc_test_cached")?.localFilePath)
    }

    @Test
    fun `resolvePlaybackUri falls back to whole movie offline file for synth_s1_e1 episode id`() = runBlocking {
        val movieFile = OfflineStoragePaths.getPrimaryTargetFile(context, "user_abc", "test_synth", null)
        createFileWithSize(movieFile, 2L * 1024L * 1024L)

        val repository = MovieRepository(
            movieDao = database.movieDao(),
            downloadDao = database.downloadDao(),
            uploadStateDao = database.uploadStateDao(),
            appContext = context
        )
        val ownerField = repository.userSession.value.uid
        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "${ownerField}_test_synth",
                movieId = "test_synth",
                movieTitle = "Whole Movie",
                coverUrl = "",
                localFilePath = movieFile.absolutePath,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                downloadedBytes = movieFile.length(),
                totalBytes = movieFile.length(),
                userId = ownerField
            )
        )

        val movie = Movie(
            id = "test_synth",
            title = "Whole Movie",
            description = "",
            genres = listOf("Action"),
            coverUrl = "",
            videoStreamUrl = "https://example.com/should_not_use_network.mp4"
        )

        val playbackUri = repository.resolvePlaybackUri(
            movie = movie,
            context = context,
            episodeId = "synth_s1_e1_test_synth"
        )
        assertEquals(Uri.fromFile(movieFile).toString(), playbackUri)
    }

    @Test
    fun `media3 smart data source reads persistent internal offline file without touching network cache`() = runBlocking {
        val cacheDirFolder = File(context.cacheDir, "movieroom_video_cache")
        cacheDirFolder.deleteRecursively()

        val targetFile = OfflineStoragePaths.getPrimaryTargetFile(context, "user_abc", "media3_test", null)
        targetFile.parentFile?.mkdirs()
        val payload = ByteArray(1024 * 1024 + 128) { (it % 251).toByte() }
        targetFile.writeBytes(payload)

        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "user_abc_media3_test",
                movieId = "media3_test",
                movieTitle = "Media3 Offline Movie",
                coverUrl = "",
                localFilePath = targetFile.absolutePath,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                downloadedBytes = targetFile.length(),
                totalBytes = targetFile.length(),
                userId = "user_abc"
            )
        )

        val resolvedUriStr = downloadManager.getVerifiedOfflinePlaybackUri("media3_test", null, "user_abc")
        assertNotNull(resolvedUriStr)
        val resolvedUri = Uri.parse(resolvedUriStr!!)
        assertEquals("file", resolvedUri.scheme)

        // Verify Media3 smart DataSource reads the local file bytes directly (both file:// and schemeless /)
        val factory = VideoCacheManager.createSmartDataSourceFactory(context)
        listOf(resolvedUri, Uri.parse(targetFile.absolutePath)).forEach { uri ->
            val dataSource = factory.createDataSource()
            try {
                val openedLen = dataSource.open(DataSpec(uri))
                assertEquals(payload.size.toLong(), openedLen)
                val headerBuf = ByteArray(64)
                val bytesRead = dataSource.read(headerBuf, 0, headerBuf.size)
                assertEquals(64, bytesRead)
                assertArrayEquals(payload.copyOfRange(0, 64), headerBuf)
            } finally {
                dataSource.close()
            }
        }

        // Verify SimpleCache in cacheDir was NOT initialized when opening offline local paths
        assertFalse(
            "Offline local file playback must not initialize SimpleCache in cacheDir",
            cacheDirFolder.exists()
        )

        // Verify Media3 ExoPlayer accepts the local file MediaItem offline
        val player = VideoCacheManager.buildFastPlayer(context)
        try {
            val mediaItem = MediaItem.fromUri(resolvedUri)
            player.setMediaItem(mediaItem)
            assertEquals(resolvedUri, player.currentMediaItem?.localConfiguration?.uri)
        } finally {
            player.release()
        }
    }

    @Test
    fun `room migration 5 to 6 preserves existing downloads and normalizes file uri paths`() = runBlocking {
        val dbName = "migration_test_v5_v6.db"
        context.deleteDatabase(dbName)

        val v5Callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `movies` (
                        `id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL,
                        `genres` TEXT NOT NULL, `coverKey` TEXT NOT NULL, `coverUrl` TEXT NOT NULL,
                        `videoKey` TEXT NOT NULL, `videoStreamUrl` TEXT NOT NULL,
                        `durationMinutes` INTEGER NOT NULL, `fileSizeMb` INTEGER NOT NULL,
                        `releaseYear` INTEGER NOT NULL, `rating` REAL NOT NULL, `cast` TEXT NOT NULL,
                        `isFeatured` INTEGER NOT NULL, `uploadStatus` TEXT NOT NULL,
                        `uploadDate` INTEGER NOT NULL, `episodesJson` TEXT NOT NULL,
                        `isSwahiliTranslated` INTEGER NOT NULL, `phoneNumber` TEXT NOT NULL,
                        `country` TEXT NOT NULL, `isTrending` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `id` TEXT NOT NULL, `movieId` TEXT NOT NULL, `movieTitle` TEXT NOT NULL,
                        `coverUrl` TEXT NOT NULL, `localFilePath` TEXT NOT NULL,
                        `progress` REAL NOT NULL, `status` TEXT NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL,
                        `episodeId` TEXT, `episodeTitle` TEXT,
                        `seasonNumber` INTEGER, `episodeNumber` INTEGER,
                        `userId` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `upload_sessions` (
                        `uploadId` TEXT NOT NULL, `movieId` TEXT NOT NULL, `movieTitle` TEXT NOT NULL,
                        `videoKey` TEXT NOT NULL, `totalBytes` INTEGER NOT NULL,
                        `chunkSize` INTEGER NOT NULL, `partsSummary` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL, `isPaused` INTEGER NOT NULL,
                        PRIMARY KEY(`uploadId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `downloads` (
                        `id`, `movieId`, `movieTitle`, `coverUrl`, `localFilePath`,
                        `progress`, `status`, `downloadedBytes`, `totalBytes`, `userId`
                    ) VALUES (
                        'user_abc_m_v5', 'm_v5', 'Migrated Movie', '',
                        'file:///data/user/0/com.example/files/movie_m_v5.mp4',
                        1.0, 'COMPLETED', 2097152, 2097152, 'user_abc'
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(v5Callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, MovieRoomDatabase::class.java, dbName)
            .addMigrations(*MovieRoomDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val migrated = migratedDb.downloadDao().getDownloadById("user_abc_m_v5")
            assertNotNull("Existing v5 download record must survive migration to v6", migrated)
            assertEquals("/data/user/0/com.example/files/movie_m_v5.mp4", migrated!!.localFilePath)
            assertFalse(migrated.isFileVerified)
            assertEquals(0L, migrated.lastVerifiedAt)
            assertEquals(DownloadStatus.COMPLETED.name, migrated.status)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `recovery service scans internal filesystem and maps existing and orphan movie files to Room`() = runBlocking {
        // Seed catalog metadata in MovieDao for one movie & series episode
        database.movieDao().insertMovie(
            MovieEntity(
                id = "mov_orphan_1",
                title = "Orphan Movie Restored",
                description = "Saved on internal disk",
                genres = "Action",
                coverUrl = "https://cdn.example.com/orphan.jpg",
                videoStreamUrl = "https://cdn.example.com/orphan.mp4"
            )
        )
        database.movieDao().insertMovie(
            MovieEntity(
                id = "ser_99",
                title = "Offline Series",
                description = "Series",
                genres = "Drama",
                episodesJson = """[{"id":"ser_99_ep_2","movieId":"ser_99","episodeNumber":2,"seasonNumber":1,"title":"Episode Two"}]"""
            )
        )

        // 1. Existing Room row with stale path + FAILED status, while real file exists in internal filesDir
        val repairedFile = OfflineStoragePaths.getPrimaryTargetFile(context, "user_abc", "test_repair", null)
        createFileWithSize(repairedFile, 2L * 1024L * 1024L)
        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "user_abc_test_repair",
                movieId = "test_repair",
                movieTitle = "Repaired Movie",
                coverUrl = "",
                localFilePath = "/nonexistent/old/movie_test_repair.mp4",
                progress = 0.4f,
                status = DownloadStatus.FAILED.name,
                downloadedBytes = 1000L,
                totalBytes = 2L * 1024L * 1024L,
                userId = "user_abc",
                isFileVerified = false
            )
        )

        // 2. Existing Room row marked COMPLETED whose file is missing on disk
        database.downloadDao().insertOrUpdate(
            DownloadEntity(
                id = "user_abc_test_gone",
                movieId = "test_gone",
                movieTitle = "Missing On Disk",
                coverUrl = "",
                localFilePath = "/nonexistent/movie_test_gone.mp4",
                progress = 1.0f,
                status = DownloadStatus.COMPLETED.name,
                downloadedBytes = 2L * 1024L * 1024L,
                totalBytes = 2L * 1024L * 1024L,
                userId = "user_abc",
                isFileVerified = true
            )
        )

        // 3. Orphan whole movie in internal filesDir/offline_downloads/users/user_abc/
        val orphanMovieFile = OfflineStoragePaths.getPrimaryTargetFile(context, "user_abc", "mov_orphan_1", null)
        createFileWithSize(orphanMovieFile, 3L * 1024L * 1024L)

        // 4. Orphan episode with underscores in ID in internal filesDir/users/user_abc/
        val orphanEpisodeFile = File(File(File(context.filesDir, "users"), "user_abc"), "movie_ser_99_ep_ser_99_ep_2.mp4")
        createFileWithSize(orphanEpisodeFile, 2L * 1024L * 1024L)

        val recoveryService = OfflineDownloadRecoveryService(
            context = context,
            downloadDao = database.downloadDao(),
            movieDao = database.movieDao()
        )
        val report = recoveryService.scanAndReconcileOfflineFiles(activeUserId = "user_abc")

        assertTrue("Expected at least 3 scanned valid files", report.scannedFilesCount >= 3)
        assertEquals(1, report.repairedPathsCount)
        assertEquals(2, report.recoveredOrphanCount)
        assertEquals(1, report.invalidatedMissingCount)

        // Verify repaired entry
        val repairedEntity = database.downloadDao().getDownloadById("user_abc_test_repair")
        assertNotNull(repairedEntity)
        assertEquals(repairedFile.absolutePath, repairedEntity!!.localFilePath)
        assertEquals(DownloadStatus.COMPLETED.name, repairedEntity.status)
        assertTrue(repairedEntity.isFileVerified)
        assertTrue(repairedEntity.lastVerifiedAt > 0L)
        assertEquals(
            Uri.fromFile(repairedFile).toString(),
            downloadManager.getVerifiedOfflinePlaybackUri("test_repair", null, "user_abc")
        )

        // Verify missing entry was invalidated
        val missingEntity = database.downloadDao().getDownloadById("user_abc_test_gone")
        assertNotNull(missingEntity)
        assertEquals(DownloadStatus.FAILED.name, missingEntity!!.status)
        assertFalse(missingEntity.isFileVerified)

        // Verify orphan movie was mapped to Room with catalog metadata & playable offline
        val orphanMovieEntity = database.downloadDao().getDownloadById("user_abc_mov_orphan_1")
        assertNotNull(orphanMovieEntity)
        assertEquals("Orphan Movie Restored", orphanMovieEntity!!.movieTitle)
        assertEquals(orphanMovieFile.absolutePath, orphanMovieEntity.localFilePath)
        assertTrue(orphanMovieEntity.isFileVerified)
        assertEquals(
            Uri.fromFile(orphanMovieFile).toString(),
            downloadManager.getVerifiedOfflinePlaybackUri("mov_orphan_1", null, "user_abc")
        )

        // Verify orphan episode with underscores in episodeId was mapped to Room cleanly
        val orphanEpEntity = database.downloadDao().getDownloadById("user_abc_ser_99_ep_ser_99_ep_2")
        assertNotNull(orphanEpEntity)
        assertEquals("ser_99", orphanEpEntity!!.movieId)
        assertEquals("ser_99_ep_2", orphanEpEntity.episodeId)
        assertEquals("Episode Two", orphanEpEntity.episodeTitle)
        assertEquals(orphanEpisodeFile.absolutePath, orphanEpEntity.localFilePath)
        assertTrue(orphanEpEntity.isFileVerified)
        assertEquals(
            Uri.fromFile(orphanEpisodeFile).toString(),
            downloadManager.getVerifiedOfflinePlaybackUri("ser_99", "ser_99_ep_2", "user_abc")
        )
    }
}
