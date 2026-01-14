package com.example.androidtviptvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.androidtviptvapp.data.PlaybackManager
import com.example.androidtviptvapp.ui.AppNavigation
import com.example.androidtviptvapp.ui.Routes
import com.example.androidtviptvapp.ui.components.Sidebar
import com.example.androidtviptvapp.ui.components.ViewMode
import com.example.androidtviptvapp.ui.theme.AndroidTvIptvAppTheme

import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.ui.screens.LoginScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load public config first (for branding on login screen)
        TvRepository.loadData()

        setContent {
            @OptIn(ExperimentalTvMaterial3Api::class)
            AndroidTvIptvAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    // Observe authentication state
                    val isAuthenticated = TvRepository.isAuthenticated

                    // Observe loading state
                    val isLoading by TvRepository.isLoading.collectAsState()
                    val loadingProgress by TvRepository.loadingProgress.collectAsState()
                    val isDataReady by TvRepository.isDataReady.collectAsState()

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isAuthenticated) {
                            // Show login screen if not authenticated
                            LoginScreen(
                                onLoginSuccess = {
                                    // Data is already loaded after successful login
                                }
                            )
                        } else {
                            // Show loading screen while data loads
                            AnimatedVisibility(
                                visible = isLoading && !isDataReady,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                LoadingScreen(progress = loadingProgress)
                            }

                            // Main app content - shown when data is ready or loading is complete
                            AnimatedVisibility(
                                visible = !isLoading || isDataReady,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                MainContent()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        PlaybackManager.release()
        TvRepository.cleanup()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadingScreen(progress: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App logo or name
            Text(
                text = TvRepository.appConfig?.appName ?: "TV App",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Loading indicator
            Text(
                text = progress,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MainContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    // Global view mode state for channels screen (default to LIST for better TV navigation)
    var channelViewMode by remember { mutableStateOf(ViewMode.LIST) }

    val isPlayerScreen = currentRoute?.startsWith("player") == true

    if (isPlayerScreen) {
        // Full screen player - no sidebar, no Row layout
        AppNavigation(
            navController = navController,
            channelViewMode = channelViewMode,
            onPlayUrl = { url ->
                val encodedUrl = android.util.Base64.encodeToString(
                    url.toByteArray(),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                )
                navController.navigate("player/$encodedUrl")
            },
            onPlayChannel = { channelId ->
                navController.navigate("player_channel/$channelId")
            }
        )
    } else {
        // Normal screens with sidebar
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                selectedRoute = currentRoute ?: Routes.HOME,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                viewMode = channelViewMode,
                onViewModeChange = { channelViewMode = it },
                showViewToggle = currentRoute == Routes.CHANNELS
            )

            AppNavigation(
                navController = navController,
                channelViewMode = channelViewMode,
                onPlayUrl = { url ->
                    val encodedUrl = android.util.Base64.encodeToString(
                        url.toByteArray(),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    navController.navigate("player/$encodedUrl")
                },
                onPlayChannel = { channelId ->
                    navController.navigate("player_channel/$channelId")
                }
            )
        }
    }
}
