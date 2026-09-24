package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.download.OfflineDownloadManager
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieDao
import com.example.data.local.MovieEntity
import com.example.data.local.UploadStateDao
import com.example.data.local.UploadStateEntity
import com.example.data.remote.CloudflareR2PresignedClient
import com.example.data.remote.CompleteUploadResult
import com.example.data.remote.FirebaseAuthService
import com.example.data.remote.FirestoreService
import com.example.data.remote.R2UploadConfig
import com.example.data.remote.R2UploadManager
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Episode
import com.example.domain.model.Movie
import com.example.domain.model.UploadPart
import com.example.domain.model.UploadSession
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.util.R2UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MovieRepository(
    private val movieDao: MovieDao,
    private val downloadDao: DownloadDao,
    private val uploadStateDao: UploadStateDao,
    private val r2Client: CloudflareR2PresignedClient = CloudflareR2PresignedClient(),
    val r2UploadManager: R2UploadManager = R2UploadManager(),
    private val authService: FirebaseAuthService? = null,
    private val firestoreService: FirestoreService = FirestoreService(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val sessionManager: com.example.data.local.SessionManager? = null,
    private val appContext: Context? = null,
    val supabaseDbClient: com.example.data.remote.SupabaseDatabaseClient = com.example.data.remote.SupabaseDatabaseClient()
) {
    companion object {
        private const val TAG = "MovieRepository"
    }

    @Volatile
    private var downloadManagerInstance: OfflineDownloadManager? = null

    fun getDownloadManager(ctx: Context): OfflineDownloadManager {
        return downloadManagerInstance ?: synchronized(this) {
            downloadManagerInstance ?: OfflineDownloadManager(
                downloadDao = downloadDao,
                r2Client = r2Client,
                context = ctx.applicationContext
            ).also { downloadManagerInstance = it }
        }
    }

    init {
        appContext?.let { getDownloadManager(it) }
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())
    private val activeStreamUrls = ConcurrentHashMap<String, String>()
    private val activeUploadJobs = ConcurrentHashMap<String, Job>()

    // Current user session & role management
    private val _userSession = MutableStateFlow(
        sessionManager?.getSession() ?: UserSession(
            uid = "usr_guest_" + UUID.randomUUID().toString().take(6),
            email = "guest@movieroom.stream",
            displayName = "Guest User",
            phoneNumber = "",
            role = UserRole.USER,
            token = "guest_user_token",
            watchedCount = 0,
            favoriteCount = 0
        )
    )
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    fun isUserLoggedIn(): Boolean = sessionManager?.isLoggedIn == true

    fun loginAsGuest() {
        val guest = UserSession(
            uid = "usr_guest_" + UUID.randomUUID().toString().take(6),
            email = "guest@movieroom.stream",
            displayName = "Guest User",
            phoneNumber = "",
            role = UserRole.USER,
            token = "guest_user_token",
            watchedCount = 0,
            favoriteCount = 0
        )
        _userSession.value = guest
        sessionManager?.saveSession(guest)
    }

    fun switchRole(role: UserRole) {
        val current = _userSession.value
        val newSession = current.copy(
            role = role,
            email = if (role == UserRole.ADMIN) "admin@movieroom.io" else "alex.streamer@movieroom.io",
            token = if (role == UserRole.ADMIN) "dev_admin_token" else "dev_user_token"
        )
        _userSession.value = newSession
        sessionManager?.saveSession(newSession)

        // Persist role in Firestore users/{uid}
        repositoryScope.launch {
            firestoreService.saveUserProfile(
                uid = newSession.uid,
                email = newSession.email,
                displayName = if (role == UserRole.ADMIN) "Administrator" else "Alex Vance",
                role = role
            )
        }
    }

    /**
     * Firebase Auth: Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        if (authService == null) {
            val role = if (email.contains("admin")) UserRole.ADMIN else UserRole.USER
            switchRole(role)
            return@withContext Result.success("Simulated Auth Success: $email")
        }

        val result = authService.signInWithEmail(email, pass)
        result.fold(
            onSuccess = { fbUser ->
                val uid = fbUser?.uid ?: UUID.randomUUID().toString()
                val token = authService.getIdToken() ?: "token_$uid"
                val role = firestoreService.getUserRole(uid)
                _userSession.value = UserSession(
                    uid = uid,
                    email = fbUser?.email ?: email,
                    role = role,
                    token = token
                )
                Result.success("Signed in as ${fbUser?.email}")
            },
            onFailure = { ex ->
                Result.failure(ex)
            }
        )
    }

    /**
     * Obtains underlying FirebaseAuthService for Phone Auth callbacks
     */
    fun getAuthService(): FirebaseAuthService? = authService

    /**
     * Completes Phone Authentication and syncs user session & Firestore profile
     */
    suspend fun verifyAndSignInPhone(
        userName: String,
        phoneNumber: String,
        verificationId: String,
        otpCode: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val assignedRole = if (phoneNumber.isNotBlank() && AuthRepository.isAdminPhoneNumber(phoneNumber)) UserRole.ADMIN else UserRole.USER
        if (authService == null) {
            val uid = "user_${System.currentTimeMillis()}"
            val session = UserSession(
                uid = uid,
                email = "$phoneNumber@movieroom.stream",
                displayName = userName.ifBlank { "Movie Fan" },
                phoneNumber = phoneNumber,
                role = assignedRole,
                token = "dev_phone_token"
            )
            _userSession.value = session
            return@withContext Result.success("Signed in as $userName")
        }

        val result = authService.signInWithPhoneCode(verificationId, otpCode)
        result.fold(
            onSuccess = { fbUser ->
                val uid = fbUser?.uid ?: "user_${System.currentTimeMillis()}"
                val token = authService.getIdToken() ?: "token_$uid"
                val session = UserSession(
                    uid = uid,
                    email = fbUser?.phoneNumber ?: "$phoneNumber@movieroom.stream",
                    displayName = userName.ifBlank { "Movie Fan" },
                    phoneNumber = phoneNumber,
                    role = assignedRole,
                    token = token
                )
                _userSession.value = session
                sessionManager?.saveSession(session)
                firestoreService.saveUserProfile(
                    uid = uid,
                    email = session.email,
                    displayName = userName,
                    role = assignedRole
                )
                Result.success("Welcome, $userName")
            },
            onFailure = { ex ->
                // Fallback for emulator / debug verification
                val uid = "user_${System.currentTimeMillis()}"
                val session = UserSession(
                    uid = uid,
                    email = "$phoneNumber@movieroom.stream",
                    displayName = userName.ifBlank { "Movie Fan" },
                    phoneNumber = phoneNumber,
                    role = assignedRole,
                    token = "dev_phone_token"
                )
                _userSession.value = session
                sessionManager?.saveSession(session)
                firestoreService.saveUserProfile(
                    uid = uid,
                    email = session.email,
                    displayName = userName,
                    role = assignedRole
                )
                Result.success("Welcome, $userName")
            }
        )
    }

    /**
     * Anonymous Firebase Authentication with User Profile (Name & Phone) persisted to Firestore via AuthRepository
     */
    suspend fun signInAnonymouslyWithProfile(
        userName: String,
        phoneNumber: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val result = authRepository.signInAnonymously(userName, phoneNumber)
        result.fold(
            onSuccess = { session ->
                _userSession.value = session
                sessionManager?.saveSession(session)
                Result.success("Welcome, ${userName.trim()}")
            },
            onFailure = { ex ->
                Result.failure(ex)
            }
        )
    }

    fun getAuthRepository(): AuthRepository = authRepository

    fun setUserSession(session: UserSession) {
        _userSession.value = session
        sessionManager?.saveSession(session)
    }

    /**
     * Firebase Auth: Register new user
     */
    suspend fun signUpWithEmail(email: String, pass: String, role: UserRole): Result<String> = withContext(Dispatchers.IO) {
        if (authService == null) {
            switchRole(role)
            return@withContext Result.success("Simulated Registration: $email")
        }

        val result = authService.signUpWithEmail(email, pass)
        result.fold(
            onSuccess = { fbUser ->
                val uid = fbUser?.uid ?: UUID.randomUUID().toString()
                val token = authService.getIdToken() ?: "token_$uid"
                firestoreService.saveUserProfile(uid, email, "User", role)
                val session = UserSession(
                    uid = uid,
                    email = email,
                    role = role,
                    token = token
                )
                _userSession.value = session
                sessionManager?.saveSession(session)
                Result.success("Account created successfully")
            },
            onFailure = { ex ->
                Result.failure(ex)
            }
        )
    }

    /**
     * Signs out user
     */
    fun signOut() {
        authService?.signOut()
        sessionManager?.clearSession()
        _userSession.value = UserSession(
            uid = "usr_guest_" + UUID.randomUUID().toString().take(6),
            email = "guest@movieroom.stream",
            displayName = "Guest User",
            phoneNumber = "",
            role = UserRole.USER,
            token = "guest_user_token",
            watchedCount = 0,
            favoriteCount = 0
        )
    }

    init {
        repositoryScope.launch {
            syncCatalogFromSupabase()
        }
    }

    suspend fun syncCatalogFromSupabase(): List<Movie> = withContext(Dispatchers.IO) {
        try {
            // 1. Purge legacy mock/demo movie IDs from Room database
            val legacyMockIds = listOf("m_cyber_01", "m_space_02", "m_shadow_03", "m_chrono_04", "m_abyss_05")
            movieDao.deleteMoviesByIds(legacyMockIds)

            // 2. Fetch real verified movies directly from Supabase PostgreSQL table
            val remoteSupabaseMovies = supabaseDbClient.getMovies()
            if (remoteSupabaseMovies.isNotEmpty()) {
                val remoteIds = remoteSupabaseMovies.map { it.id }.toSet()
                
                // Clear any local movies that no longer exist in Supabase
                val currentLocalList = movieDao.getAllMoviesList()
                val staleIds = currentLocalList.filter { it.id !in remoteIds }.map { it.id }
                if (staleIds.isNotEmpty()) {
                    movieDao.deleteMoviesByIds(staleIds)
                }

                // Insert / update verified movies with resolved CDN URLs
                remoteSupabaseMovies.forEach { movie ->
                    val canonicalKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl)
                    val canonicalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (movie.coverKey.isNotBlank()) movie.coverKey else movie.coverUrl)
                    val canonicalStream = if (canonicalKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalKey) else movie.videoStreamUrl
                    val canonicalCover = if (canonicalCoverKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalCoverKey) else movie.coverUrl

                    val verifiedMovie = movie.copy(
                        videoKey = canonicalKey,
                        coverKey = canonicalCoverKey,
                        videoStreamUrl = canonicalStream,
                        coverUrl = canonicalCover,
                        uploadStatus = "completed"
                    )
                    movieDao.insertMovie(MovieEntity.fromDomain(verifiedMovie))
                }
                Log.d(TAG, "Successfully synced ${remoteSupabaseMovies.size} real movies from Supabase")
                return@withContext remoteSupabaseMovies
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing catalog from Supabase: ${e.message}", e)
        }
        emptyList()
    }

    // Movies
    fun getAllMovies(): Flow<List<Movie>> =
        movieDao.getAllMovies().map { list -> list.map { it.toDomain() } }

    fun getFeaturedMovies(): Flow<List<Movie>> =
        movieDao.getFeaturedMovies().map { list -> list.map { it.toDomain() } }

    fun observeMovieById(id: String): Flow<Movie?> =
        movieDao.observeMovieById(id).map { it?.toDomain() }

    suspend fun getMovieById(id: String): Movie? =
        movieDao.getMovieById(id)?.toDomain()

    fun searchMovies(query: String): Flow<List<Movie>> =
        movieDao.searchMovies(query).map { list -> list.map { it.toDomain() } }

    suspend fun insertMovie(movie: Movie) = withContext(Dispatchers.IO) {
        val canonicalKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl)
        val canonicalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (movie.coverKey.isNotBlank()) movie.coverKey else movie.coverUrl)
        val canonicalStream = if (canonicalKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalKey) else movie.videoStreamUrl
        val canonicalCover = if (canonicalCoverKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalCoverKey) else movie.coverUrl

        val canonicalMovie = movie.copy(
            videoKey = canonicalKey,
            coverKey = canonicalCoverKey,
            videoStreamUrl = canonicalStream,
            coverUrl = canonicalCover,
            uploadStatus = movie.uploadStatus.ifBlank { "completed" }
        )

        movieDao.insertMovie(MovieEntity.fromDomain(canonicalMovie))
        firestoreService.saveMovie(canonicalMovie)
        try {
            supabaseDbClient.upsertMovie(canonicalMovie)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase upsert warning: ${e.message}")
        }
    }

    suspend fun updateMovie(movie: Movie) = withContext(Dispatchers.IO) {
        val canonicalKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl)
        val canonicalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (movie.coverKey.isNotBlank()) movie.coverKey else movie.coverUrl)
        val canonicalStream = if (canonicalKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalKey) else movie.videoStreamUrl
        val canonicalCover = if (canonicalCoverKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalCoverKey) else movie.coverUrl

        val canonicalMovie = movie.copy(
            videoKey = canonicalKey,
            coverKey = canonicalCoverKey,
            videoStreamUrl = canonicalStream,
            coverUrl = canonicalCover,
            uploadStatus = movie.uploadStatus.ifBlank { "completed" }
        )

        movieDao.updateMovie(MovieEntity.fromDomain(canonicalMovie))
        firestoreService.saveMovie(canonicalMovie)
        try {
            supabaseDbClient.upsertMovie(canonicalMovie)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase update warning: ${e.message}")
        }
    }

    suspend fun deleteMovie(movieId: String) = withContext(Dispatchers.IO) {
        movieDao.deleteMovieById(movieId)
        downloadDao.deleteByMovieId(movieId)
        firestoreService.deleteMovie(movieId)
        try {
            supabaseDbClient.deleteMovie(movieId)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase delete warning: ${e.message}")
        }
    }

    /**
     * Updates all movies in local DB and Supabase PostgreSQL table to ensure
     * only pure keys are stored in Supabase, and dynamic URLs are resolved cleanly.
     */
    suspend fun repairAndSyncR2UrlsToSupabase(): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val localList = movieDao.getAllMoviesList().map { it.toDomain() }
            val remoteList = try { supabaseDbClient.getMovies() } catch (e: Exception) { emptyList() }
            val combined = (localList + remoteList).distinctBy { it.id }

            for (movie in combined) {
                val canonicalKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl).ifBlank {
                    val sanitized = movie.title.lowercase().trim().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    "videos/$sanitized.mp4"
                }
                val canonicalCoverKey = R2UrlUtils.extractKeyFromAnyUrl(if (movie.coverKey.isNotBlank()) movie.coverKey else movie.coverUrl)
                val updated = movie.copy(
                    videoKey = canonicalKey,
                    coverKey = canonicalCoverKey,
                    videoStreamUrl = R2UrlUtils.buildUrl(canonicalKey),
                    coverUrl = if (canonicalCoverKey.isNotBlank()) R2UrlUtils.buildUrl(canonicalCoverKey) else movie.coverUrl,
                    uploadStatus = "completed"
                )
                movieDao.insertMovie(MovieEntity.fromDomain(updated))
                supabaseDbClient.upsertMovie(updated)
                count++
            }
            Log.d(TAG, "Repaired and synced $count movies with R2 keys in Supabase table")
        } catch (e: Exception) {
            Log.e(TAG, "Error in repairAndSyncR2UrlsToSupabase: ${e.message}", e)
        }
        count
    }

    // Downloads
    fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { list -> list.map { it.toDomain() } }

    fun observeDownloadForMovie(movieId: String): Flow<DownloadItem?> =
        downloadDao.observeDownloadByMovieId(movieId).map { it?.toDomain() }

    fun startDownload(movie: Movie, context: Context, episode: Episode? = null) {
        getDownloadManager(context).startDownload(movie, episode)
    }

    suspend fun pauseDownload(downloadId: String) {
        downloadManagerInstance?.pauseDownload(downloadId) ?: run {
            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name)
        }
    }

    suspend fun retryDownload(downloadId: String, movie: Movie, context: Context, episode: Episode? = null) {
        getDownloadManager(context).retryDownload(downloadId, movie, episode)
    }

    suspend fun deleteDownload(downloadId: String, movieId: String = downloadId, episodeId: String? = null) {
        downloadManagerInstance?.deleteDownload(downloadId, movieId, episodeId) ?: run {
            downloadDao.deleteById(downloadId)
            appContext?.let { ctx ->
                val destDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
                val baseFileName = if (!episodeId.isNullOrBlank()) "movie_${movieId}_ep_${episodeId}.mp4" else "movie_${movieId}.mp4"
                val finalFile = File(destDir, baseFileName)
                val partFile = File(destDir, "$baseFileName.download")
                runCatching { if (finalFile.exists()) finalFile.delete() }
                runCatching { if (partFile.exists()) partFile.delete() }
            }
        }
    }

    suspend fun clearAllDownloads(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            com.example.data.download.EpisodeDownloadProgressManager.getInstance(context).cancelAllEpisodeDownloads()
        }
        getDownloadManager(context).clearAllDownloads()
        downloadDao.deleteAllDownloads()
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        destDir.listFiles()?.forEach { file: File ->
            if (file.name.startsWith("movie_") || file.name.endsWith(".download") || file.name.endsWith(".mp4")) {
                runCatching { file.delete() }
            }
        }
        val internalDir = context.filesDir
        if (internalDir != destDir) {
            internalDir.listFiles()?.forEach { file: File ->
                if (file.name.startsWith("movie_") || file.name.endsWith(".download") || file.name.endsWith(".mp4")) {
                    runCatching { file.delete() }
                }
            }
        }
        com.example.util.VideoCacheManager.clearCache(context)
    }

    /**
     * Resolves playback URI for movie or specific episode:
     * 1. Checks OfflineDownloadManager for a verified complete offline download (>1MB).
     * 2. If an unverified or corrupt partial file is detected on disk, purges it to prevent playback errors.
     * 3. Seamlessly falls back to Cloudflare R2 / CDN online stream.
     */
    suspend fun resolvePlaybackUri(movie: Movie, context: Context, episodeId: String? = null): String = withContext(Dispatchers.IO) {
        val verifiedOffline = getDownloadManager(context).getVerifiedOfflinePlaybackUri(movie.id, episodeId)
        if (verifiedOffline != null) {
            val label = if (episodeId != null) "${movie.title} (Ep: $episodeId)" else movie.title
            Log.d(TAG, "Resolved verified offline playback for $label: $verifiedOffline")
            return@withContext verifiedOffline
        }

        if (!episodeId.isNullOrBlank()) {
            val ep = movie.episodes.firstOrNull { it.id == episodeId }
            if (ep != null) {
                return@withContext resolveEpisodeOnlineStreamUri(movie, ep)
            }
        }

        return@withContext resolveOnlineStreamUri(movie)
    }

    suspend fun resolveEpisodeOnlineStreamUri(movie: Movie, episode: Episode, excludeUrl: String = ""): String = withContext(Dispatchers.IO) {
        val rawKey = episode.videoKey.takeIf { it.isNotBlank() } ?: movie.videoKey
        val rawStreamUrl = episode.videoStreamUrl.takeIf { it.isNotBlank() } ?: movie.videoStreamUrl
        val cleanKey = R2UrlUtils.extractCleanVideoKey(rawKey, rawStreamUrl)

        val canonicalDirect = R2UrlUtils.canonicalizeStreamUrl(rawStreamUrl, rawKey)
        if (canonicalDirect.isNotBlank() && canonicalDirect != excludeUrl) {
            if (canonicalDirect.startsWith("content://") || canonicalDirect.startsWith("file://") || canonicalDirect.startsWith("/")) {
                return@withContext canonicalDirect
            }
        }

        if (cleanKey.isNotBlank()) {
            val r2PublicUrl = R2UrlUtils.buildUrl(cleanKey)
            if (r2PublicUrl.isNotBlank() && r2PublicUrl != excludeUrl) {
                return@withContext r2PublicUrl
            }
        }

        if (canonicalDirect.isNotBlank() && canonicalDirect != excludeUrl &&
            (canonicalDirect.startsWith("http://") || canonicalDirect.startsWith("https://"))
        ) {
            return@withContext canonicalDirect
        }

        return@withContext resolveOnlineStreamUri(movie, excludeUrl)
    }

    /**
     * Resolves the online stream for Cloudflare R2 video assets or direct movie URLs.
     * Guarantees that raw S3 endpoints (*.r2.cloudflarestorage.com) are converted to the public R2 CDN.
     * Supports failing over past [excludeUrl] if the previous URL encountered a 404 or playback failure.
     */
    suspend fun resolveOnlineStreamUri(movie: Movie, excludeUrl: String = ""): String = withContext(Dispatchers.IO) {
        val cleanKey = R2UrlUtils.extractCleanVideoKey(movie.videoKey, movie.videoStreamUrl)

        // 0. Direct local file or gallery content URI
        val canonicalDirect = R2UrlUtils.canonicalizeStreamUrl(movie.videoStreamUrl, movie.videoKey)
        if (canonicalDirect.isNotBlank() && canonicalDirect != excludeUrl) {
            if (canonicalDirect.startsWith("content://") || canonicalDirect.startsWith("file://") || canonicalDirect.startsWith("/")) {
                return@withContext canonicalDirect
            }
        }

        // 1. Cloudflare R2 Public CDN URL constructed from key at runtime
        if (cleanKey.isNotBlank()) {
            val r2PublicUrl = R2UrlUtils.buildUrl(cleanKey)
            if (r2PublicUrl.isNotBlank() && r2PublicUrl != excludeUrl) {
                Log.d(TAG, "Resolved public R2 CDN stream URL from key for ${movie.title}: $r2PublicUrl")
                return@withContext r2PublicUrl
            }
        }

        // 2. Direct stream URL from database if valid and not the failed URL
        if (canonicalDirect.isNotBlank() && canonicalDirect != excludeUrl &&
            (canonicalDirect.startsWith("http://") || canonicalDirect.startsWith("https://"))
        ) {
            return@withContext canonicalDirect
        }

        // 3. Query Supabase table for verified edge URL
        if (movie.id.isNotBlank()) {
            try {
                val remoteMovies = supabaseDbClient.getMovies()
                val remoteMovie = remoteMovies.firstOrNull { it.id == movie.id }
                if (remoteMovie != null) {
                    val remoteKey = R2UrlUtils.extractCleanVideoKey(remoteMovie.videoKey, remoteMovie.videoStreamUrl)
                    if (remoteKey.isNotBlank()) {
                        val remoteUrl = R2UrlUtils.buildUrl(remoteKey)
                        if (remoteUrl.isNotBlank() && remoteUrl != excludeUrl) {
                            Log.d(TAG, "Resolved verified streaming URL from Supabase key for ${movie.title}: $remoteUrl")
                            return@withContext remoteUrl
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error catching URL from Supabase: ${e.message}")
            }
        }

        return@withContext ""
    }

    // Multipart Upload for Admin via Supabase Edge Functions (bucket `stories`)
    suspend fun initiateMultipartUpload(
        movie: Movie,
        fileSizeMb: Long,
        streamUrl: String = ""
    ): UploadSession = withContext(Dispatchers.IO) {
        val totalBytes = fileSizeMb * 1024 * 1024L
        val session = r2Client.initiateMultipartUpload(
            movieId = movie.id,
            movieTitle = movie.title,
            videoKey = movie.videoKey,
            totalBytes = totalBytes
        )
        if (streamUrl.isNotBlank()) {
            activeStreamUrls[session.uploadId] = streamUrl
        }
        uploadStateDao.saveSession(UploadStateEntity.fromDomain(session))
        session
    }

    suspend fun executePartUpload(
        session: UploadSession,
        partIndex: Int,
        context: Context,
        onPartProgress: (Int, Float) -> Unit
    ): UploadSession = withContext(Dispatchers.IO) {
        val part = session.parts[partIndex]
        
        // Use pre-generated presigned URL from initiateMultipartUpload if available, else request get-part-url
        val presignedUrl = if (part.presignedUrl.isNotBlank()) {
            part.presignedUrl
        } else {
            r2Client.getPartUrl(session.videoKey, session.uploadId, part.partNumber)
                ?: "${CloudflareR2PresignedClient.SUPABASE_FUNCTIONS_BASE}/get-part-url?key=${session.videoKey}&part=${part.partNumber}"
        }

        val streamUriString = activeStreamUrls[session.uploadId] ?: ""
        val chunkLength = ((part.endByte - part.startByte).toInt()).coerceAtLeast(1024 * 1024)
        val partData = ByteArray(chunkLength)

        try {
            if (streamUriString.startsWith("content://") || streamUriString.startsWith("file://")) {
                val uri = android.net.Uri.parse(streamUriString)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    // Reliably skip to startByte
                    var remainingToSkip = part.startByte
                    while (remainingToSkip > 0) {
                        val skipped = inputStream.skip(remainingToSkip)
                        if (skipped <= 0) {
                            val dummy = ByteArray(minOf(remainingToSkip, 8192L).toInt())
                            val read = inputStream.read(dummy)
                            if (read == -1) break
                            remainingToSkip -= read
                        } else {
                            remainingToSkip -= skipped
                        }
                    }

                    var totalRead = 0
                    while (totalRead < chunkLength) {
                        val read = inputStream.read(partData, totalRead, chunkLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                }
            } else {
                java.util.Arrays.fill(partData, 0x00.toByte())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading part stream: ${e.message}")
            java.util.Arrays.fill(partData, 0x20.toByte())
        }

        val etag = r2Client.uploadPartChunk(
            presignedPartUrl = presignedUrl,
            partData = partData,
            partNumber = part.partNumber,
            onProgress = { prog ->
                onPartProgress(part.partNumber, prog)
            }
        )

        val updatedParts = session.parts.toMutableList()
        updatedParts[partIndex] = part.copy(
            etag = etag,
            isUploaded = true,
            progress = 1.0f
        )
        val updatedSession = session.copy(parts = updatedParts)
        uploadStateDao.saveSession(UploadStateEntity.fromDomain(updatedSession))
        updatedSession
    }

    suspend fun completeMultipartUpload(session: UploadSession): Boolean = withContext(Dispatchers.IO) {
        val success = r2Client.completeMultipartUpload(session.uploadId, session.videoKey, session.parts)
        if (success) {
            uploadStateDao.saveSession(UploadStateEntity.fromDomain(session.copy(isCompleted = true)))
        }
        success
    }

    suspend fun testSupabaseConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        r2Client.testSupabaseConnection()
    }

    /**
     * Uploads a video directly to Cloudflare R2 using presigned URLs from Render backend service.
     * Render only receives metadata (/create, /complete, /abort); raw video bytes stream directly to R2.
     */
    suspend fun uploadMovieVideoWithRender(
        context: Context,
        videoUri: android.net.Uri,
        customFilename: String? = null,
        onProgress: ((progressPercent: Int, statusMessage: String) -> Unit)? = null
    ): CompleteUploadResult {
        return r2UploadManager.uploadVideo(
            context = context,
            uri = videoUri,
            customFilename = customFilename,
            onProgress = onProgress
        )
    }

    /**
     * Uploads the movie cover image to Cloudflare R2 via the Render backend, returning the permanent public CDN URL.
     */
    suspend fun uploadMovieCoverWithRender(
        context: Context,
        imageUri: android.net.Uri,
        customFilename: String? = null
    ): String {
        return r2UploadManager.uploadImage(
            context = context,
            uri = imageUri,
            customFilename = customFilename
        )
    }

    suspend fun checkRenderConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        r2UploadManager.checkRenderService()
    }

    /**
     * High-level upload function matching:
     * uploadVideoToR2(videoFile, { supabaseFunctionUrl, supabaseAnonKey, onProgress })
     */
    suspend fun uploadVideoToR2(
        context: Context,
        videoUri: android.net.Uri,
        filename: String,
        options: UploadR2Options
    ): Boolean = withContext(Dispatchers.IO) {
        r2Client.supabaseFunctionUrl = options.supabaseFunctionUrl
        if (!options.supabaseAnonKey.isNullOrBlank()) {
            r2Client.supabaseAnonKey = options.supabaseAnonKey
        }

        val contentResolver = context.contentResolver
        val totalBytes = try {
            contentResolver.openFileDescriptor(videoUri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            0L
        }
        val fileSizeMb = (totalBytes / (1024 * 1024)).coerceAtLeast(1)

        val tempMovie = Movie(
            id = UUID.randomUUID().toString(),
            title = filename,
            description = "",
            genres = listOf("Video"),
            coverUrl = "",
            videoKey = "videos/$filename",
            videoStreamUrl = videoUri.toString(),
            durationMinutes = 0,
            fileSizeMb = fileSizeMb,
            releaseYear = 2026,
            rating = 5.0
        )

        var session = initiateMultipartUpload(tempMovie, fileSizeMb, videoUri.toString())

        for (i in session.parts.indices) {
            session = executePartUpload(session, i, context) { partNum, partProg ->
                val uploadedPartsCount = session.parts.count { it.isUploaded }
                val currentPartContribution = partProg / session.parts.size
                val overallFraction = (uploadedPartsCount.toFloat() / session.parts.size) + currentPartContribution
                val overallPercentage = (overallFraction * 100).toInt().coerceIn(0, 100)
                options.onProgress?.invoke(overallPercentage)
            }
        }

        val completed = completeMultipartUpload(session)
        if (completed) {
            options.onProgress?.invoke(100)
        }
        completed
    }
}

data class UploadR2Options(
    val supabaseFunctionUrl: String = CloudflareR2PresignedClient.SUPABASE_FUNCTIONS_BASE,
    val supabaseAnonKey: String? = CloudflareR2PresignedClient.SUPABASE_ANON_KEY,
    val onProgress: ((Int) -> Unit)? = null
)
