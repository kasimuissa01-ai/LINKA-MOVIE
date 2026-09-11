package com.example.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.Movie
import com.example.presentation.components.AppUpdateDialog
import com.example.presentation.components.BottomUpdateAlert
import com.example.presentation.screens.AdminAddEditMovieScreen
import com.example.presentation.screens.AdminDashboardScreen
import com.example.presentation.screens.DownloadsScreen
import com.example.presentation.screens.HomeScreen
import com.example.presentation.screens.MovieDetailScreen
import com.example.presentation.screens.OnboardingAuthScreen
import com.example.presentation.screens.ProfileScreen
import com.example.presentation.screens.SearchScreen
import com.example.presentation.screens.VideoPlayerScreen
import com.example.presentation.viewmodel.AdminViewModel
import com.example.presentation.viewmodel.AppUpdateViewModel
import com.example.presentation.viewmodel.AuthViewModel
import com.example.presentation.viewmodel.DownloadViewModel
import com.example.presentation.viewmodel.MovieViewModel
import com.example.presentation.viewmodel.PlayerViewModel
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

sealed class Screen(val route: String) {
    object OnboardingAuth : Screen("onboarding_auth")
    object Home : Screen("home")
    object Search : Screen("search")
    object Downloads : Screen("downloads")
    object Profile : Screen("profile")
    object MovieDetail : Screen("movie_detail/{movieId}") {
        fun createRoute(movieId: String) = "movie_detail/$movieId"
    }
    object VideoPlayer : Screen("video_player/{movieId}") {
        fun createRoute(movieId: String) = "video_player/$movieId"
    }
    object AdminDashboard : Screen("admin_dashboard")
    object AdminAddMovie : Screen("admin_add_movie")
    object AdminEditMovie : Screen("admin_edit_movie/{movieId}") {
        fun createRoute(movieId: String) = "admin_edit_movie/$movieId"
    }
}

data class BottomNavItem(
    val title: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AppNavigation(
    movieViewModel: MovieViewModel,
    downloadViewModel: DownloadViewModel,
    adminViewModel: AdminViewModel,
    authViewModel: AuthViewModel,
    playerViewModel: PlayerViewModel,
    updateViewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("Search", Screen.Search.route, Icons.Filled.Search, Icons.Outlined.Search),
        BottomNavItem("Downloads", Screen.Downloads.route, Icons.Filled.Download, Icons.Outlined.Download),
        BottomNavItem("Profile", Screen.Profile.route, Icons.Filled.Person, Icons.Outlined.Person)
    )

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Downloads.route,
        Screen.Profile.route
    )

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        contentColor = TextPrimary,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("main_bottom_nav_bar")
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = CinematicRed,
                                    indicatorColor = CinematicRed,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_item_${item.title.lowercase()}")
                            )
                        }
                    }
                }
            },
            containerColor = ObsidianBlack,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (authViewModel.isUserLoggedIn()) Screen.Home.route else Screen.OnboardingAuth.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                // Onboarding & Phone Auth
                composable(Screen.OnboardingAuth.route) {
                    OnboardingAuthScreen(
                        authViewModel = authViewModel,
                        onNavigateToHome = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.OnboardingAuth.route) { inclusive = true }
                            }
                        }
                    )
                }

                // Home
                composable(Screen.Home.route) {
                    HomeScreen(
                        movieViewModel = movieViewModel,
                        downloadViewModel = downloadViewModel,
                        updateViewModel = updateViewModel,
                        onMovieClick = { movie ->
                            navController.navigate(Screen.MovieDetail.createRoute(movie.id))
                        },
                        onPlayClick = { movie ->
                            navController.navigate(Screen.VideoPlayer.createRoute(movie.id))
                        }
                    )
                }

                // Search
                composable(Screen.Search.route) {
                    SearchScreen(
                        movieViewModel = movieViewModel,
                        onMovieClick = { movie ->
                            navController.navigate(Screen.MovieDetail.createRoute(movie.id))
                        }
                    )
                }

                // Downloads
                composable(Screen.Downloads.route) {
                    DownloadsScreen(
                        downloadViewModel = downloadViewModel,
                        movieViewModel = movieViewModel,
                        onPlayMovie = { movie ->
                            navController.navigate(Screen.VideoPlayer.createRoute(movie.id))
                        },
                        onBrowseCatalog = {
                            navController.navigate(Screen.Home.route)
                        }
                    )
                }

                // Profile
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        authViewModel = authViewModel,
                        onNavigateToAdmin = {
                            navController.navigate(Screen.AdminDashboard.route)
                        },
                        onNavigateToOnboarding = {
                            navController.navigate(Screen.OnboardingAuth.route)
                        }
                    )
                }

            // Movie Detail
            composable(
                route = Screen.MovieDetail.route,
                arguments = listOf(navArgument("movieId") { type = NavType.StringType })
            ) { backStackEntry ->
                val movieId = backStackEntry.arguments?.getString("movieId")
                val movieUiState by movieViewModel.uiState.collectAsState()
                val movie = movieUiState.allMovies.find { it.id == movieId }
                if (movie != null) {
                    MovieDetailScreen(
                        movie = movie,
                        allMovies = movieUiState.allMovies,
                        downloadViewModel = downloadViewModel,
                        onBackClick = { navController.popBackStack() },
                        onPlayFullscreenClick = { selectedMovie ->
                            navController.navigate(Screen.VideoPlayer.createRoute(selectedMovie.id))
                        },
                        onSelectRecommendedMovie = { recommendedMovie ->
                            navController.navigate(Screen.MovieDetail.createRoute(recommendedMovie.id)) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            // Video Player
            composable(
                route = Screen.VideoPlayer.route,
                arguments = listOf(navArgument("movieId") { type = NavType.StringType })
            ) { backStackEntry ->
                val movieId = backStackEntry.arguments?.getString("movieId")
                val movieUiState by movieViewModel.uiState.collectAsState()
                val movie = movieUiState.allMovies.find { it.id == movieId }
                if (movie != null) {
                    VideoPlayerScreen(
                        movie = movie,
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // Admin Dashboard
            composable(Screen.AdminDashboard.route) {
                AdminDashboardScreen(
                    adminViewModel = adminViewModel,
                    onBackClick = { navController.popBackStack() },
                    onAddMovieClick = { navController.navigate(Screen.AdminAddMovie.route) },
                    onEditMovieClick = { movie ->
                        navController.navigate(Screen.AdminEditMovie.createRoute(movie.id))
                    }
                )
            }

            // Admin Add Movie
            composable(Screen.AdminAddMovie.route) {
                AdminAddEditMovieScreen(
                    adminViewModel = adminViewModel,
                    existingMovie = null,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Admin Edit Movie
            composable(
                route = Screen.AdminEditMovie.route,
                arguments = listOf(navArgument("movieId") { type = NavType.StringType })
            ) { backStackEntry ->
                val movieId = backStackEntry.arguments?.getString("movieId")
                val adminMovies by adminViewModel.movies.collectAsState()
                val movie = adminMovies.find { it.id == movieId }
                AdminAddEditMovieScreen(
                    adminViewModel = adminViewModel,
                    existingMovie = movie,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

        // Bottom Animated Update Alert (Floats smoothly above content/nav bar)
        BottomUpdateAlert(
            updateViewModel = updateViewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showBottomBar) 76.dp else 12.dp)
                .navigationBarsPadding()
        )

        // Detailed In-App Update Dialog Modal
        AppUpdateDialog(updateViewModel = updateViewModel)
    }
}
