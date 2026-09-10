package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.MovieRoomDatabase
import com.example.data.remote.CloudflareR2PresignedClient
import com.example.data.repository.MovieRepository
import com.example.presentation.navigation.AppNavigation
import com.example.presentation.viewmodel.AdminViewModel
import com.example.presentation.viewmodel.AuthViewModel
import com.example.presentation.viewmodel.DownloadViewModel
import com.example.presentation.viewmodel.MovieViewModel
import com.example.presentation.viewmodel.PlayerViewModel
import com.example.ui.theme.MovieRoomTheme
import com.example.ui.theme.ObsidianBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val APP_LOGO_URL = "https://vqgnxqabvmmpfoiceass.supabase.co/storage/v1/object/public/posters/53b872bf8d82ba00514f876c2489bff8.jpg"
        const val AUTH_BG_URL = "https://vqgnxqabvmmpfoiceass.supabase.co/storage/v1/object/public/posters/994191af-3db3-4f4c-9a1d-0b0c4d1fa5ef/468bc883dda769afbd066f44d5aa8b8a.jpg"
    }

    private lateinit var movieRepository: MovieRepository
    private lateinit var movieViewModel: MovieViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var adminViewModel: AdminViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var appUpdateViewModel: com.example.presentation.viewmodel.AppUpdateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize High-Performance Coil ImageLoader with Memory & Disk Cache
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        // Preload logo and key backgrounds immediately for instant rendering
        lifecycleScope.launch(Dispatchers.IO) {
            val urlsToPreload = listOf(
                APP_LOGO_URL,
                AUTH_BG_URL,
                "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=80",
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80"
            )
            for (url in urlsToPreload) {
                val req = ImageRequest.Builder(applicationContext)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(req)
            }
        }

        // Initialize dependencies
        val database = MovieRoomDatabase.getInstance(applicationContext)
        val authService = com.example.data.remote.FirebaseAuthService(applicationContext)
        val firestoreService = com.example.data.remote.FirestoreService()
        val r2Client = CloudflareR2PresignedClient(
            tokenProvider = {
                authService.getIdToken()
            }
        )
        val sessionManager = com.example.data.local.SessionManager(applicationContext)
        movieRepository = MovieRepository(
            movieDao = database.movieDao(),
            downloadDao = database.downloadDao(),
            uploadStateDao = database.uploadStateDao(),
            r2Client = r2Client,
            authService = authService,
            firestoreService = firestoreService,
            sessionManager = sessionManager
        )

        movieViewModel = MovieViewModel(movieRepository)
        downloadViewModel = DownloadViewModel(movieRepository)
        adminViewModel = AdminViewModel(movieRepository)
        authViewModel = AuthViewModel(movieRepository)
        playerViewModel = PlayerViewModel(movieRepository)
        appUpdateViewModel = com.example.presentation.viewmodel.AppUpdateViewModel()

        // Schedule Daily Morning, Afternoon & Night Movie Recommendations
        com.example.util.DailyMovieNotificationReceiver.scheduleDailyPicks(applicationContext)

        setContent {
            MovieRoomTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBlack
                ) {
                    AppNavigation(
                        movieViewModel = movieViewModel,
                        downloadViewModel = downloadViewModel,
                        adminViewModel = adminViewModel,
                        authViewModel = authViewModel,
                        playerViewModel = playerViewModel,
                        updateViewModel = appUpdateViewModel
                    )
                }
            }
        }
    }
}


