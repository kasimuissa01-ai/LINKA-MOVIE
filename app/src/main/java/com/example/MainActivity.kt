package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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

class MainActivity : ComponentActivity() {

    private lateinit var movieRepository: MovieRepository
    private lateinit var movieViewModel: MovieViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var adminViewModel: AdminViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize dependencies
        val database = MovieRoomDatabase.getInstance(applicationContext)
        val authService = com.example.data.remote.FirebaseAuthService(applicationContext)
        val firestoreService = com.example.data.remote.FirestoreService()
        val r2Client = CloudflareR2PresignedClient(
            tokenProvider = {
                authService.getIdToken()
            }
        )
        movieRepository = MovieRepository(
            movieDao = database.movieDao(),
            downloadDao = database.downloadDao(),
            uploadStateDao = database.uploadStateDao(),
            r2Client = r2Client,
            authService = authService,
            firestoreService = firestoreService
        )

        movieViewModel = MovieViewModel(movieRepository)
        downloadViewModel = DownloadViewModel(movieRepository)
        adminViewModel = AdminViewModel(movieRepository)
        authViewModel = AuthViewModel(movieRepository)
        playerViewModel = PlayerViewModel(movieRepository)

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
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

