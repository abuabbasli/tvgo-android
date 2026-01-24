package com.example.androidtviptvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.androidtviptvapp.ui.screens.BabyLockManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BabyLockManager for parental controls
        BabyLockManager.init(this)

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
                                    // Trigger data loading after successful login
                                    TvRepository.loadData(this@MainActivity)
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

    // Global number input state for channel jumping (works even when sidebar has focus)
    var enteredNumber by remember { mutableStateOf("") }
    val numberInputTimeoutMs = 1500L

    // Auto-jump to channel after number input timeout
    LaunchedEffect(enteredNumber, currentRoute) {
        if (enteredNumber.isNotEmpty()) {
            kotlinx.coroutines.delay(numberInputTimeoutMs)
            val orderNumber = enteredNumber.toIntOrNull()
            if (orderNumber != null) {
                val targetChannel = TvRepository.getChannelByOrder(orderNumber)
                if (targetChannel != null) {
                    if (currentRoute == Routes.CHANNELS) {
                        // On channels screen: Update preview channel via savedStateHandle
                        navController.currentBackStackEntry?.savedStateHandle?.set("channelId", targetChannel.id)
                    } else if (currentRoute?.startsWith("player_channel") == true) {
                        // On fullscreen player: Navigate to new channel in fullscreen
                        navController.navigate("player_channel/${targetChannel.id}") {
                            popUpTo("player_channel/{channelId}") { inclusive = true }
                        }
                    }
                }
            }
            enteredNumber = ""
        }
    }

    val isPlayerScreen = currentRoute?.startsWith("player") == true
    val isChannelPlayerScreen = currentRoute?.startsWith("player_channel") == true
    val isChannelsScreen = currentRoute == Routes.CHANNELS

    // Number key handler for channels screen and player screen
    val numberKeyHandler: (KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown && (isChannelsScreen || isChannelPlayerScreen)) {
            when (event.key) {
                Key.Zero, Key.NumPad0 -> { enteredNumber += "0"; true }
                Key.One, Key.NumPad1 -> { enteredNumber += "1"; true }
                Key.Two, Key.NumPad2 -> { enteredNumber += "2"; true }
                Key.Three, Key.NumPad3 -> { enteredNumber += "3"; true }
                Key.Four, Key.NumPad4 -> { enteredNumber += "4"; true }
                Key.Five, Key.NumPad5 -> { enteredNumber += "5"; true }
                Key.Six, Key.NumPad6 -> { enteredNumber += "6"; true }
                Key.Seven, Key.NumPad7 -> { enteredNumber += "7"; true }
                Key.Eight, Key.NumPad8 -> { enteredNumber += "8"; true }
                Key.Nine, Key.NumPad9 -> { enteredNumber += "9"; true }
                else -> false
            }
        } else false
    }

    if (isPlayerScreen) {
        // Full screen player - no sidebar, with number key support for channel jumping
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent(numberKeyHandler)
        ) {
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

            // Number input display overlay (for direct channel jump in fullscreen)
            AnimatedVisibility(
                visible = enteredNumber.isNotEmpty() && isChannelPlayerScreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.85f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = enteredNumber,
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Show target channel name if found
                        val targetChannel = enteredNumber.toIntOrNull()?.let { TvRepository.getChannelByOrder(it) }
                        if (targetChannel != null) {
                            Text(
                                text = targetChannel.name,
                                color = Color(0xFF60A5FA),
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (enteredNumber.isNotEmpty()) {
                            Text(
                                text = "Channel not found",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Normal screens with sidebar
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent(numberKeyHandler)  // Capture number keys before children
            ) {
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

            // Number input display overlay (for direct channel jump)
            AnimatedVisibility(
                visible = enteredNumber.isNotEmpty() && isChannelsScreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.85f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = enteredNumber,
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Show target channel name if found
                        val targetChannel = enteredNumber.toIntOrNull()?.let { TvRepository.getChannelByOrder(it) }
                        if (targetChannel != null) {
                            Text(
                                text = targetChannel.name,
                                color = Color(0xFF60A5FA),
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (enteredNumber.isNotEmpty()) {
                            Text(
                                text = "Channel not found",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
