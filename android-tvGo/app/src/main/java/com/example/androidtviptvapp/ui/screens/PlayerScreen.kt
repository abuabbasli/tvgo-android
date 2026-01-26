package com.example.androidtviptvapp.ui.screens

import com.example.androidtviptvapp.data.PlaybackManager
import com.example.androidtviptvapp.data.ChannelPlaybackSource
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.player.PlayerView
import com.example.androidtviptvapp.player.AdaptExoPlayerView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * PlayerScreen with full OnTV-style controls
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    channelId: String? = null,
    onChannelChanged: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRetrying by remember { mutableStateOf(false) }

    // Get channel from repository
    val channel = remember(channelId) {
        channelId?.let { id ->
            TvRepository.channels.find { it.id == id }
        }
    }

    // Create PlaybackSource for the channel
    val initialSource = remember(channel) {
        channel?.let {
            ChannelPlaybackSource.create(it, TvRepository.channels.toList())
        }
    }

    // Current source state
    var currentSource by remember { mutableStateOf(initialSource) }

    // Overlay states (OnTV-main pattern: info, controls, hotbar)
    var showOverlay by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }

    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }

    // Focus requester for key capture
    val focusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }

    // PlayerView reference
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Channel switching timing control (Android TV best practice)
    // Uses timestamp-based throttling to ensure one channel at a time during long press
    var lastChannelSwitchTime by remember { mutableStateOf(0L) }
    val channelSwitchIntervalMs = 500L  // Minimum 500ms between channel switches

    // Number input for direct channel jump (remote number keys)
    var enteredNumber by remember { mutableStateOf("") }
    val numberInputTimeoutMs = 1500L  // Auto-jump after 1.5 seconds

    // Auto-jump to channel after number input timeout
    LaunchedEffect(enteredNumber) {
        if (enteredNumber.isNotEmpty()) {
            delay(numberInputTimeoutMs)
            val orderNumber = enteredNumber.toIntOrNull()
            if (orderNumber != null) {
                val targetChannel = TvRepository.getChannelByOrder(orderNumber)
                if (targetChannel != null) {
                    val newSource = ChannelPlaybackSource.create(targetChannel, TvRepository.channels.toList())
                    playerView?.play(newSource)
                    currentSource = newSource
                    onChannelChanged(targetChannel.id)
                    // Store as last played channel
                    ChannelFocusManager.updatePlayedChannel(targetChannel.id)
                    showOverlay = true
                }
            }
            enteredNumber = ""
        }
    }

    // Auto-hide overlay after 8 seconds (OnTV-main uses 8s)
    LaunchedEffect(showOverlay, showControls) {
        if (showOverlay || showControls) {
            delay(8000)
            showOverlay = false
            showControls = false
        }
    }

    // CRITICAL: Tick loop for health monitoring (OnTV-main pattern)
    // This runs every 300ms and calls player.tick() to detect frozen streams
    LaunchedEffect(playerView) {
        playerView?.let { pv ->
            while (true) {
                delay(300) // OnTV-main TICK_DT = 300ms
                pv.tick()
                // Update UI state from player
                isPlaying = !pv.pause && pv.isPlayReady
                isBuffering = pv.isBuffering
            }
        }
    }

    /**
     * Switch channel with strict timing control for Android TV remotes.
     *
     * - First press (repeatCount == 0): Switch immediately
     * - Long press (repeatCount > 0): One channel per 500ms
     *
     * This ensures smooth one-at-a-time scrolling during long press.
     */
    fun switchChannelWithRepeat(direction: Int, repeatCount: Int): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSwitch = currentTime - lastChannelSwitchTime

        // First press always switches immediately
        // Repeated presses (long press) only switch every 500ms
        val shouldSwitch = if (repeatCount == 0) {
            true
        } else {
            timeSinceLastSwitch >= channelSwitchIntervalMs
        }

        if (shouldSwitch) {
            lastChannelSwitchTime = currentTime
            val next = playerView?.jumpChannel(direction)
            if (next != null) {
                currentSource = next
                onChannelChanged(next.channel.id)
                // Store as last played channel
                ChannelFocusManager.updatePlayedChannel(next.channel.id)
                showOverlay = true
            }
        }

        return true // Always consume the event to prevent bubbling
    }

    // Toggle play/pause
    fun togglePlayPause() {
        playerView?.let { pv ->
            pv.pause = !pv.pause
            isPlaying = !pv.pause
        }
    }

    // Save initial channel
    LaunchedEffect(Unit) {
        channelId?.let {
            onChannelChanged(it)
            // Store as last played channel
            ChannelFocusManager.updatePlayedChannel(it)
        }
    }
    
    // Setup error callbacks
    DisposableEffect(Unit) {
        PlaybackManager.onPlaybackError = { message, retrying ->
            errorMessage = if (message.isEmpty()) null else message
            isRetrying = retrying
        }
        onDispose {
            PlaybackManager.onPlaybackError = null
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                // Only handle KeyDown events - use repeatCount for long press
                if (event.type == KeyEventType.KeyDown) {
                    // Get repeat count from native event (0 = first press, >0 = key held)
                    val repeatCount = event.nativeKeyEvent.repeatCount

                    when (event.key) {
                        Key.DirectionUp -> {
                            if (showControls) {
                                false // Let focus handle it
                            } else {
                                switchChannelWithRepeat(1, repeatCount)
                            }
                        }
                        Key.DirectionDown -> {
                            if (showControls) {
                                false
                            } else {
                                switchChannelWithRepeat(-1, repeatCount)
                            }
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (!showControls) {
                                showOverlay = true
                                showControls = true
                            }
                            true
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (!showControls) {
                                showOverlay = true
                                showControls = true
                                true
                            } else {
                                false
                            }
                        }
                        Key.MediaPlayPause -> {
                            togglePlayPause()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (showControls) {
                                showControls = false
                                showOverlay = false
                                true
                            } else {
                                // Mark that we're returning from fullscreen - skip debounce in preview
                                com.example.androidtviptvapp.player.SharedPlayerManager.markReturningFromFullscreen()
                                onBack()
                                true
                            }
                        }
                        Key.Menu -> {
                            showOverlay = true
                            showControls = !showControls
                            true
                        }
                        // Number keys for direct channel jump
                        Key.Zero, Key.NumPad0 -> { enteredNumber += "0"; showOverlay = true; true }
                        Key.One, Key.NumPad1 -> { enteredNumber += "1"; showOverlay = true; true }
                        Key.Two, Key.NumPad2 -> { enteredNumber += "2"; showOverlay = true; true }
                        Key.Three, Key.NumPad3 -> { enteredNumber += "3"; showOverlay = true; true }
                        Key.Four, Key.NumPad4 -> { enteredNumber += "4"; showOverlay = true; true }
                        Key.Five, Key.NumPad5 -> { enteredNumber += "5"; showOverlay = true; true }
                        Key.Six, Key.NumPad6 -> { enteredNumber += "6"; showOverlay = true; true }
                        Key.Seven, Key.NumPad7 -> { enteredNumber += "7"; showOverlay = true; true }
                        Key.Eight, Key.NumPad8 -> { enteredNumber += "8"; showOverlay = true; true }
                        Key.Nine, Key.NumPad9 -> { enteredNumber += "9"; showOverlay = true; true }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Request focus for key capture
        LaunchedEffect(showControls) {
            if (!showControls) {
                focusRequester.requestFocus()
            }
        }

        // View-based PlayerView (OnTV-main pattern - dedicated player instance)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = AdaptExoPlayerView.RESIZE_MODE_FIT

                    // Initialize the player
                    init()
                    playerView = this

                    // Register with PlaybackManager
                    PlaybackManager.registerPlayerView(this)

                    // Start playback
                    val source = initialSource
                    if (source != null) {
                        play(source)
                    } else {
                        playUrl(videoUrl)
                    }
                }
            },
            onRelease = { view ->
                // Cleanup
                PlaybackManager.unregisterPlayerView(view)
                view.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Top gradient for info overlay
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        // Channel info overlay (top)
        // Use local variable to avoid race conditions with mutable state
        val sourceForOverlay = currentSource
        AnimatedVisibility(
            visible = showOverlay && sourceForOverlay != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            sourceForOverlay?.let { source ->
                ChannelInfoOverlay(
                    source = source,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        
        // Bottom gradient for controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }
        
        // Player controls (bottom)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerControlsBar(
                isPlaying = isPlaying,
                onPlayPause = { togglePlayPause() },
                onPrev = { switchChannelWithRepeat(-1, 0) },
                onNext = { switchChannelWithRepeat(1, 0) },
                onBackward = { /* TODO: Seek backward for VOD */ },
                onForward = { /* TODO: Seek forward for VOD */ },
                isLiveStream = true, // For live channels
                controlsFocusRequester = controlsFocusRequester,
                modifier = Modifier.padding(24.dp)
            )
        }
        
        // Buffering indicator
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        // Error display
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        Color.Black.copy(alpha = 0.8f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    color = if (isRetrying) Color(0xFFFFA500) else Color.Red
                )
            }
        }

        // Number input display (for direct channel jump)
        AnimatedVisibility(
            visible = enteredNumber.isNotEmpty(),
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

/**
 * Player controls bar (OnTV-style)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerControlsBar(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackward: () -> Unit,
    onForward: () -> Unit,
    isLiveStream: Boolean,
    controlsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // LIVE indicator
        if (isLiveStream) {
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Red, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous channel
            PlayerControlButton(
                icon = Icons.Default.SkipPrevious,
                onClick = onPrev,
                contentDescription = "Previous",
                enabled = true
            )
            
            // Backward (disabled for live)
            PlayerControlButton(
                icon = Icons.Default.FastRewind,
                onClick = onBackward,
                contentDescription = "Rewind",
                enabled = !isLiveStream
            )
            
            // Play/Pause (main button, focused by default)
            PlayerControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                onClick = onPlayPause,
                contentDescription = if (isPlaying) "Pause" else "Play",
                enabled = true,
                isMain = true,
                modifier = Modifier.focusRequester(controlsFocusRequester)
            )
            
            // Forward (disabled for live)
            PlayerControlButton(
                icon = Icons.Default.FastForward,
                onClick = onForward,
                contentDescription = "Forward",
                enabled = !isLiveStream
            )
            
            // Next channel
            PlayerControlButton(
                icon = Icons.Default.SkipNext,
                onClick = onNext,
                contentDescription = "Next",
                enabled = true
            )
        }
    }
}

/**
 * Individual player control button
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean,
    isMain: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val size = if (isMain) 64.dp else 48.dp
    val iconSize = if (isMain) 36.dp else 24.dp
    
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .size(size)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isMain) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.3f),
            disabledContainerColor = Color.Transparent
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = CircleShape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Channel info overlay showing logo, name, and current program
 */
@Composable
private fun ChannelInfoOverlay(
    source: ChannelPlaybackSource,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val channel = source.channel
    val currentProgram = TvRepository.currentPrograms[channel.id]
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel logo
        if (channel.logo.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(channel.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
        }
        
        Column {
            // Channel name with order number
            Text(
                text = channel.displayName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Current program
            if (currentProgram != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentProgram.title ?: "No program info",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp
                )
                
                if (currentProgram.start != null && currentProgram.end != null) {
                    Text(
                        text = "${formatTime(currentProgram.start)} - ${formatTime(currentProgram.end)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularProgressIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    // Simple loading indicator using animated box
    Box(
        modifier = modifier
            .border(3.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .border(3.dp, color, CircleShape)
        )
    }
}

private fun formatTime(isoTime: String?): String {
    if (isoTime == null) return ""
    return try {
        val parts = isoTime.split("T")
        if (parts.size >= 2) {
            parts[1].substring(0, 5)
        } else {
            isoTime
        }
    } catch (e: Exception) {
        isoTime
    }
}
