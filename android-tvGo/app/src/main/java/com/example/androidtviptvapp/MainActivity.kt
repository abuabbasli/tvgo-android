package com.example.androidtviptvapp

import android.os.Bundle
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import android.view.KeyEvent
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
import com.example.androidtviptvapp.data.SessionManager
import com.example.androidtviptvapp.ui.screens.LoginScreen
import com.example.androidtviptvapp.ui.screens.BabyLockManager
import com.example.androidtviptvapp.ui.components.GlobalPlayerOverlay
import com.example.androidtviptvapp.player.SharedPlayerManager

class MainActivity : ComponentActivity() {

    // Key throttling to prevent crashes from rapid focus traversal on long press
    // Lower value = faster scrolling, higher value = more stable
    private var lastNavKeyTime = 0L
    private val navKeyThrottleMs = 30L // Very fast scrolling - minimal throttle

    /**
     * Intercept key events BEFORE they reach Compose to prevent crashes from
     * rapid focus traversal on detached nodes during long press.
     *
     * Known Compose TV crashes this prevents:
     * 1. "visitAncestors called on an unattached node" - focus traversal crash
     * 2. "replace() called on item that was not placed" - layout crash during rapid scroll
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Check if this is a navigation key (D-pad)
        val isNavKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }

        if (isNavKey) {
            val now = System.currentTimeMillis()
            if (now - lastNavKeyTime < navKeyThrottleMs) {
                // Too fast - consume the event to prevent rapid focus changes
                return true
            }
            lastNavKeyTime = now
        }

        // Let the event through normally, catch any Compose crashes
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            // Catch Compose crashes:
            // - "unattached node" from focus traversal
            // - "replace() called on item that was not placed" from layout
            android.util.Log.w("MainActivity", "Caught Compose crash: ${e.message}")
            true
        } catch (e: Exception) {
            // Catch any other unexpected errors during key dispatch
            android.util.Log.w("MainActivity", "Caught key dispatch error: ${e.message}")
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SessionManager for login caching
        SessionManager.init(this)

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

                    // Auto-login state
                    var isAttemptingAutoLogin by remember { mutableStateOf(true) }
                    var autoLoginFailed by remember { mutableStateOf(false) }

                    // Attempt auto-login on first launch
                    LaunchedEffect(Unit) {
                        if (SessionManager.hasSavedSession() && !isAuthenticated) {
                            val success = SessionManager.tryAutoLogin(this@MainActivity)
                            autoLoginFailed = !success
                        }
                        isAttemptingAutoLogin = false
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Show loading while attempting auto-login
                        if (isAttemptingAutoLogin && SessionManager.hasSavedSession()) {
                            LoadingScreen(progress = "Logging in...")
                        } else if (!isAuthenticated) {
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
            // App logo from config
            com.example.androidtviptvapp.ui.components.AppLogo(
                size = 120.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = TvRepository.appConfig?.appName ?: "TV App",
                style = MaterialTheme.typography.headlineMedium,
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
            // Store the current entered number to compare later
            val currentNumber = enteredNumber

            try {
                kotlinx.coroutines.delay(numberInputTimeoutMs)

                // Only proceed if the number hasn't changed during the delay
                // This prevents jumping to intermediate values when typing multi-digit numbers
                if (currentNumber != enteredNumber) {
                    android.util.Log.d("MainActivity", "Number changed during delay ($currentNumber -> $enteredNumber), skipping jump")
                    return@LaunchedEffect
                }

                val orderNumber = enteredNumber.toIntOrNull()
                android.util.Log.d("MainActivity", "Channel jump: enteredNumber='$enteredNumber', orderNumber=$orderNumber, currentRoute=$currentRoute")

                if (orderNumber != null) {
                    val targetChannel = TvRepository.getChannelByOrder(orderNumber)
                    android.util.Log.d("MainActivity", "Looking for channel with order $orderNumber, found: ${targetChannel?.name} (id=${targetChannel?.id})")

                    if (targetChannel != null) {
                        if (currentRoute == Routes.CHANNELS) {
                            // On channels screen: Update preview channel via savedStateHandle
                            android.util.Log.d("MainActivity", "Jumping to channel ${targetChannel.name} on channels screen")
                            try {
                                navController.currentBackStackEntry?.savedStateHandle?.set("channelId", targetChannel.id)
                            } catch (e: Exception) {
                                android.util.Log.w("MainActivity", "Failed to set channel ID: ${e.message}")
                            }
                        } else if (currentRoute?.startsWith("player_channel") == true) {
                            // On fullscreen player: Navigate to new channel in fullscreen
                            android.util.Log.d("MainActivity", "Jumping to channel ${targetChannel.name} on player screen")
                            try {
                                navController.navigate("player_channel/${targetChannel.id}") {
                                    popUpTo("player_channel/{channelId}") { inclusive = true }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("MainActivity", "Failed to navigate to channel: ${e.message}")
                            }
                        }
                    } else {
                        android.util.Log.w("MainActivity", "No channel found with order $orderNumber. Total channels: ${TvRepository.channels.size}")
                        // Log first few channels to see their order values
                        TvRepository.channels.take(5).forEach { ch ->
                            android.util.Log.d("MainActivity", "  Channel: ${ch.name}, order=${ch.order}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Error in channel jump: ${e.message}")
            } finally {
                // Only clear if we're still on the same number (not interrupted by new input)
                if (currentNumber == enteredNumber) {
                    enteredNumber = ""
                }
            }
        }
    }

    val isPlayerScreen = currentRoute?.startsWith("player") == true
    val isChannelPlayerScreen = currentRoute?.startsWith("player_channel") == true
    val isChannelsScreen = currentRoute == Routes.CHANNELS

    // Number key handler for channels screen and player screen
    val numberKeyHandler: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
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

    // Update overlay visibility based on current route
    LaunchedEffect(currentRoute) {
        val shouldShowOverlay = isChannelsScreen || isChannelPlayerScreen
        SharedPlayerManager.setOverlayVisible(shouldShowOverlay)
    }

    // Root Box that contains everything + the global player overlay
    Box(modifier = Modifier.fillMaxSize()) {
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

            }
        }

        // =====================================================================
        // GLOBAL PLAYER OVERLAY - Sits on top of everything, NEVER moves
        // Position/size controlled by SharedPlayerManager state
        // =====================================================================
        GlobalPlayerOverlay(
            modifier = Modifier.fillMaxSize()
        )

        // Number input display overlay for channels screen - using Popup to render above native player view
        if (enteredNumber.isNotEmpty() && isChannelsScreen) {
            Popup(
                alignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(24.dp)
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
