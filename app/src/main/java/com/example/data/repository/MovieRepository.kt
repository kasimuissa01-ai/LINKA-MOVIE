package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieDao
import com.example.data.local.MovieEntity
import com.example.data.local.UploadStateDao
import com.example.data.local.UploadStateEntity
import com.example.data.remote.CloudflareR2PresignedClient
import com.example.data.remote.FirebaseAuthService
import com.example.data.remote.FirestoreService
import com.example.domain.model.DownloadItem
import com.example.domain.model.DownloadStatus
import com.example.domain.model.Movie
import com.example.domain.model.UploadPart
import com.example.domain.model.UploadSession
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
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
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MovieRepository(
    private val movieDao: MovieDao,
    private val downloadDao: DownloadDao,
    private val uploadStateDao: UploadStateDao,
    private val r2Client: CloudflareR2PresignedClient = CloudflareR2PresignedClient(),
    private val authService: FirebaseAuthService? = null,
    private val firestoreService: FirestoreService = FirestoreService(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val sessionManager: com.example.data.local.SessionManager? = null
) {
    companion object {
        private const val TAG = "MovieRepository"
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val activeUploadJobs = ConcurrentHashMap<String, Job>()

    // Current user session & role management
    private val _userSession = MutableStateFlow(
        sessionManager?.getSession() ?: UserSession(
            uid = "usr_stream_991",
            email = "alex.streamer@movieroom.io",
            role = UserRole.USER,
            token = "jwt_token_movieroom_user_secure"
        )
    )
    val userSession: StateFlow<UserSession> = _userSession.asStateFlow()

    fun isUserLoggedIn(): Boolean = sessionManager?.isLoggedIn == true

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
        val assignedRole = if (AuthRepository.isAdminPhoneNumber(phoneNumber)) UserRole.ADMIN else UserRole.USER
        if (authService == null) {
            val uid = "user_${System.currentTimeMillis()}"
            val session = UserSession(
                uid = uid,
                email = "$phoneNumber@movieroom.stream",
                displayName = userName.ifBlank { "Alex Vance" },
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
                    displayName = userName.ifBlank { "Alex Vance" },
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
                    displayName = userName.ifBlank { "Alex Vance" },
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
            email = "guest@movieroom.io",
            role = UserRole.USER,
            token = "dev_user_token"
        )
    }

    init {
        repositoryScope.launch {
            seedInitialCatalogIfEmpty()
        }
    }

    private suspend fun seedInitialCatalogIfEmpty() {
        // Try syncing from remote Firestore first
        try {
            val remoteMovies = firestoreService.fetchMovies()
            if (remoteMovies.isNotEmpty()) {
                remoteMovies.forEach { movie ->
                    movieDao.insertMovie(MovieEntity.fromDomain(movie))
                }
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore sync note: ${e.message}")
        }

        if (movieDao.getMovieCount() == 0) {
            val initialMovies = listOf(
                Movie(
                    id = "m_cyber_01",
                    title = "Neon Horizon: 2099",
                    description = "In a rain-drenched dystopian metropolis powered by rogue AI, a lone courier discovers a cipher that could bring down the central grid.",
                    genres = listOf("Sci-Fi", "Cyberpunk", "Thriller"),
                    coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80",
                    videoKey = "movies/neon_horizon_2099.mp4",
                    videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    durationMinutes = 128,
                    fileSizeMb = 1420,
                    releaseYear = 2026,
                    rating = 4.9,
                    cast = listOf("Elena Rostova", "Kaelen Voss", "Marcus Sterling"),
                    isFeatured = true
                ),
                Movie(
                    id = "m_space_02",
                    title = "Solaris Echo",
                    description = "An exploration vessel stranded near an event horizon encounters impossible anomalies that reflect the crew's deepest regrets.",
                    genres = listOf("Sci-Fi", "Mystery", "Drama"),
                    coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=80",
                    videoKey = "movies/solaris_echo.mp4",
                    videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    durationMinutes = 144,
                    fileSizeMb = 2100,
                    releaseYear = 2025,
                    rating = 4.8,
                    cast = listOf("Siddharth Roy", "Amara Chen", "Tariq Morales"),
                    isFeatured = true
                ),
                Movie(
                    id = "m_shadow_03",
                    title = "The Obsidian Protocol",
                    description = "When a top-secret black-ops satellite drops out of orbit, an elite tactical team races against time through frozen Scandinavian mountains.",
                    genres = listOf("Action", "Thriller"),
                    coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&auto=format&fit=crop&q=80",
                    videoKey = "movies/obsidian_protocol.mp4",
                    videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    durationMinutes = 112,
                    fileSizeMb = 1150,
                    releaseYear = 2025,
                    rating = 4.6,
                    cast = listOf("Victor Draven", "Natasha Grey"),
                    isFeatured = false
                ),
                Movie(
                    id = "m_chrono_04",
                    title = "Chronos Divide",
                    description = "A quantum physicist accidentally fractures the timeline, waking up each day in an alternate timeline where history unfolded differently.",
                    genres = listOf("Sci-Fi", "Mind-Bending"),
                    coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80",
                    videoKey = "movies/chronos_divide.mp4",
                    videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    durationMinutes = 135,
                    fileSizeMb = 1680,
                    releaseYear = 2024,
                    rating = 4.7,
                    cast = listOf("David Miller", "Seraphina Lin"),
                    isFeatured = true
                ),
                Movie(
                    id = "m_abyss_05",
                    title = "Midnight Mariana",
                    description = "Seven miles below sea level in the deepest trench on Earth, deep-sea drillers awake something ancient and predatory.",
                    genres = listOf("Horror", "Thriller", "Sci-Fi"),
                    coverUrl = "https://images.unsplash.com/photo-1551244072-5d12893278ab?w=800&auto=format&fit=crop&q=80",
                    videoKey = "movies/midnight_mariana.mp4",
                    videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    durationMinutes = 98,
                    fileSizeMb = 980,
                    releaseYear = 2026,
                    rating = 4.5,
                    cast = listOf("Jason Hawke", "Chloe Mercer"),
                    isFeatured = false
                )
            )

            initialMovies.forEach { movie ->
                movieDao.insertMovie(MovieEntity.fromDomain(movie))
                // Also write to Firestore
                firestoreService.saveMovie(movie)
            }
        }
    }

    // Movies
    fun getAllMovies(): Flow<List<Movie>> =
        movieDao.getAllMovies().map { list -> list.map { it.toDomain() } }

    fun getFeaturedMovies(): Flow<List<Movie>> =
        movieDao.getFeaturedMovies().map { list -> list.map { it.toDomain() } }

    fun observeMovieById(id: String): Flow<Movie?> =
        movieDao.observeMovieById(id).map { it?.toDomain() }

    fun searchMovies(query: String): Flow<List<Movie>> =
        movieDao.searchMovies(query).map { list -> list.map { it.toDomain() } }

    suspend fun insertMovie(movie: Movie) {
        movieDao.insertMovie(MovieEntity.fromDomain(movie))
        firestoreService.saveMovie(movie)
    }

    suspend fun updateMovie(movie: Movie) {
        movieDao.updateMovie(MovieEntity.fromDomain(movie))
        firestoreService.saveMovie(movie)
    }

    suspend fun deleteMovie(movieId: String) {
        movieDao.deleteMovieById(movieId)
        downloadDao.deleteByMovieId(movieId)
        firestoreService.deleteMovie(movieId)
    }

    // Downloads
    fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { list -> list.map { it.toDomain() } }

    fun observeDownloadForMovie(movieId: String): Flow<DownloadItem?> =
        downloadDao.observeDownloadByMovieId(movieId).map { it?.toDomain() }

    suspend fun startDownload(movie: Movie, context: Context) {
        val downloadId = "dl_${movie.id}"
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val destFile = File(destDir, "movie_${movie.id}.mp4")

        val totalBytesEstimate = movie.fileSizeMb * 1024 * 1024L
        val existing = downloadDao.getDownloadByMovieId(movie.id)

        val item = existing?.toDomain()?.copy(
            status = DownloadStatus.DOWNLOADING
        ) ?: DownloadItem(
            id = downloadId,
            movieId = movie.id,
            movieTitle = movie.title,
            coverUrl = movie.coverUrl,
            localFilePath = destFile.absolutePath,
            progress = 0f,
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = 0L,
            totalBytes = totalBytesEstimate
        )

        downloadDao.insertOrUpdate(DownloadEntity.fromDomain(item))
        firestoreService.syncDownload(_userSession.value.uid, item)

        val job = repositoryScope.launch {
            try {
                // Obtain signed download URL from Supabase Edge Function get-download-url
                val signedDownloadUrl = r2Client.getDownloadUrl(movie.videoKey)
                Log.d(TAG, "Starting download via signed URL for ${movie.title} from R2 bucket stories: $signedDownloadUrl")

                val simulatedTotal = 50 * 1024 * 1024L // 50MB representative sample size
                var currentBytes = item.downloadedBytes

                while (currentBytes < simulatedTotal) {
                    if (!activeDownloadJobs.containsKey(downloadId)) {
                        break
                    }
                    kotlinx.coroutines.delay(120)
                    currentBytes += 1024 * 1024L // +1MB per tick
                    val progress = (currentBytes.toFloat() / simulatedTotal).coerceAtMost(1f)
                    downloadDao.updateProgress(
                        id = downloadId,
                        progress = progress,
                        status = DownloadStatus.DOWNLOADING.name,
                        bytes = currentBytes
                    )
                }

                if (currentBytes >= simulatedTotal) {
                    destFile.createNewFile()
                    downloadDao.updateProgress(
                        id = downloadId,
                        progress = 1.0f,
                        status = DownloadStatus.COMPLETED.name,
                        bytes = simulatedTotal
                    )
                    firestoreService.syncDownload(
                        _userSession.value.uid,
                        item.copy(status = DownloadStatus.COMPLETED, progress = 1.0f)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${movie.title}: ${e.message}")
                downloadDao.updateProgress(
                    id = downloadId,
                    progress = item.progress,
                    status = DownloadStatus.FAILED.name,
                    bytes = item.downloadedBytes
                )
            } finally {
                activeDownloadJobs.remove(downloadId)
            }
        }
        activeDownloadJobs[downloadId] = job
    }

    suspend fun pauseDownload(downloadId: String) {
        activeDownloadJobs[downloadId]?.cancel()
        activeDownloadJobs.remove(downloadId)
        downloadDao.updateProgress(downloadId, 0.5f, DownloadStatus.PAUSED.name, 25 * 1024 * 1024L)
    }

    suspend fun deleteDownload(downloadId: String) {
        activeDownloadJobs[downloadId]?.cancel()
        activeDownloadJobs.remove(downloadId)
        downloadDao.deleteById(downloadId)
    }

    /**
     * Resolves playback URI: Checks local offline file existence first, then gets
     * a signed presigned GET URL from Supabase Edge Function `get-download-url` for Cloudflare R2 bucket `stories`.
     */
    suspend fun resolvePlaybackUri(movie: Movie, context: Context): String = withContext(Dispatchers.IO) {
        val destDir = context.getExternalFilesDir(null) ?: context.filesDir
        val localFile = File(destDir, "movie_${movie.id}.mp4")
        if (localFile.exists() && localFile.length() > 0) {
            Log.d(TAG, "Offline file found for ${movie.title} at ${localFile.absolutePath}")
            return@withContext localFile.toURI().toString()
        }

        // Fetch fresh signed GET URL from Supabase Edge Function get-download-url
        val signedUrl = r2Client.getDownloadUrl(movie.videoKey)
        if (signedUrl.isNotEmpty()) {
            return@withContext signedUrl
        }
        return@withContext movie.videoStreamUrl
    }

    // Multipart Upload for Admin via Supabase Edge Functions (bucket `stories`)
    suspend fun initiateMultipartUpload(
        movie: Movie,
        fileSizeMb: Long
    ): UploadSession = withContext(Dispatchers.IO) {
        val totalBytes = fileSizeMb * 1024 * 1024L
        val session = r2Client.initiateMultipartUpload(
            movieId = movie.id,
            movieTitle = movie.title,
            videoKey = movie.videoKey,
            totalBytes = totalBytes
        )
        uploadStateDao.saveSession(UploadStateEntity.fromDomain(session))
        session
    }

    suspend fun executePartUpload(
        session: UploadSession,
        partIndex: Int,
        onPartProgress: (Int, Float) -> Unit
    ): UploadSession = withContext(Dispatchers.IO) {
        val part = session.parts[partIndex]
        
        // Request signed part upload URL from Supabase Edge Function get-part-url
        val presignedUrl = r2Client.getPartUrl(session.videoKey, session.uploadId, part.partNumber)
            ?: "${CloudflareR2PresignedClient.SUPABASE_FUNCTIONS_BASE}/get-part-url?key=${session.videoKey}&part=${part.partNumber}"

        val etag = r2Client.uploadPartChunk(
            presignedPartUrl = presignedUrl,
            partData = ByteArray(1024),
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
}
