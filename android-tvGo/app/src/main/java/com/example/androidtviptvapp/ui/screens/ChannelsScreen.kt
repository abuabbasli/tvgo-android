package com.example.androidtviptvapp.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import com.example.androidtviptvapp.data.PlaybackManager
import com.example.androidtviptvapp.data.ChannelPlaybackSource
import com.example.androidtviptvapp.player.PlayerView as TvPlayerView
import com.example.androidtviptvapp.player.SharedPlayerManager
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
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.ui.components.CategoryFilter
import com.example.androidtviptvapp.ui.components.ChannelCard
import com.example.androidtviptvapp.ui.components.ChannelListItem
import com.example.androidtviptvapp.ui.components.ViewMode
import kotlinx.coroutines.delay

private const val TAG = "ChannelsScreen"

/**
 * ChannelsScreen with integrated fullscreen mode (OnTV-main pattern).
 *
 * CRITICAL: Does NOT navigate to a separate PlayerScreen!
 * Instead, the player resizes in place:
 * - Preview mode: Player shown in preview box, menu visible
 * - Fullscreen mode: Player fills screen, menu hidden
 *
 * This avoids the crash from trying to re-parent the singleton player.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewMode: ViewMode = ViewMode.GRID,
    initialChannelId: String? = null,
    onChannelClick: (Channel) -> Unit = {},  // Only used for external navigation if needed
    onBack: () -> Unit = {}  // Called when back pressed in preview mode
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // FULLSCREEN STATE - controls whether menu is shown or player fills screen
    var isFullscreen by remember { mutableStateOf(false) }

    var selectedCategory by remember { mutableStateOf("all") }
    var focusedChannel by remember {
        mutableStateOf(
            if (initialChannelId != null) TvRepository.channels.find { it.id == initialChannelId }
            else null
        )
    }
    var debouncedFocusedChannel by remember { mutableStateOf(focusedChannel) }
    var previewChannel by remember {
        mutableStateOf(
            if (initialChannelId != null) TvRepository.channels.find { it.id == initialChannelId }
            else null
        )
    }
    var isClickTriggered by remember { mutableStateOf(false) }

    // CHANNEL NAVIGATION DEBOUNCE - prevents rapid-fire on long press
    var lastChannelSwitchTime by remember { mutableStateOf(0L) }
    val channelSwitchDebounceMs = 400L  // 400ms between channel switches

    // Focus requesters
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val fullscreenFocusRequester = remember { FocusRequester() }

    val gridState = androidx.tv.foundation.lazy.grid.rememberTvLazyGridState()
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()

    val filteredChannels = remember(selectedCategory) {
        if (selectedCategory == "all") {
            TvRepository.channels
        } else {
            TvRepository.channels.filter { it.category == selectedCategory }
        }
    }

    // Get singleton player
    val singletonPlayer = remember(context) {
        SharedPlayerManager.getOrCreatePlayer(context)
    }
    var playerViewRef by remember { mutableStateOf<TvPlayerView?>(null) }

    // Player state observation
    val playerState by SharedPlayerManager.playerState.collectAsState()
    var showOverlay by remember { mutableStateOf(true) }

    // Lifecycle - ticking
    DisposableEffect(Unit) {
        SharedPlayerManager.startTicking(scope)
        SharedPlayerManager.markAttached()
        onDispose {
            SharedPlayerManager.stopTicking()
            SharedPlayerManager.markDetached()
        }
    }

    // Auto-hide overlay in fullscreen after 5 seconds
    LaunchedEffect(isFullscreen, showOverlay) {
        if (isFullscreen && showOverlay) {
            delay(5000)
            showOverlay = false
        }
    }

    // Debounce focus updates
    LaunchedEffect(focusedChannel?.id) {
        focusedChannel?.let { channel ->
            delay(150)
            debouncedFocusedChannel = channel
        }
    }

    // Initialize focus/preview when filtered channels change
    LaunchedEffect(filteredChannels) {
        if (focusedChannel == null || focusedChannel !in filteredChannels) {
            focusedChannel = filteredChannels.firstOrNull()
            debouncedFocusedChannel = focusedChannel
        }
        if (previewChannel == null || previewChannel !in filteredChannels) {
            previewChannel = filteredChannels.firstOrNull()
        }
    }

    // Update selection when returning with a new channel ID
    LaunchedEffect(initialChannelId) {
        if (initialChannelId != null) {
            val channel = TvRepository.channels.find { it.id == initialChannelId }
            if (channel != null) {
                focusedChannel = channel
                previewChannel = channel

                val index = filteredChannels.indexOfFirst { it.id == initialChannelId }
                if (index >= 0) {
                    when (viewMode) {
                        ViewMode.GRID -> gridState.scrollToItem(index)
                        ViewMode.LIST -> listState.scrollToItem(index)
                    }
                    delay(150)
                    focusRequesters[initialChannelId]?.requestFocus()
                }
            }
        }
    }

    // Switch channel with debounce (for fullscreen mode)
    fun switchChannelFullscreen(direction: Int) {
        val now = System.currentTimeMillis()
        if (now - lastChannelSwitchTime < channelSwitchDebounceMs) {
            return
        }
        lastChannelSwitchTime = now

        val currentChannel = previewChannel ?: return
        val currentIndex = filteredChannels.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex < 0) return

        val newIndex = (currentIndex + direction).coerceIn(0, filteredChannels.size - 1)
        if (newIndex != currentIndex) {
            val newChannel = filteredChannels[newIndex]
            previewChannel = newChannel
            focusedChannel = newChannel
            isClickTriggered = true  // Instant play
            showOverlay = true
            Log.d(TAG, "Switched to channel: ${newChannel.name}")
        }
    }

    // Request fullscreen focus when entering fullscreen
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            delay(100)
            fullscreenFocusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // FULLSCREEN PLAYER - always present, visibility controlled
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusRequester(fullscreenFocusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    switchChannelFullscreen(1)
                                    true
                                }
                                Key.DirectionDown -> {
                                    switchChannelFullscreen(-1)
                                    true
                                }
                                Key.DirectionCenter, Key.Enter -> {
                                    showOverlay = !showOverlay
                                    true
                                }
                                Key.Back, Key.Escape -> {
                                    // Return to preview mode
                                    isFullscreen = false
                                    SharedPlayerManager.markReturningFromFullscreen()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                // Player fills the screen
                AndroidView(
                    factory = { ctx ->
                        singletonPlayer.apply {
                            resizeMode = com.example.androidtviptvapp.player.AdaptExoPlayerView.RESIZE_MODE_FIT
                            playerViewRef = this
                        }
                    },
                    update = { /* handled by LaunchedEffect */ },
                    onRelease = { view ->
                        // Don't destroy - just detach
                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Fullscreen overlay with channel info
                AnimatedVisibility(
                    visible = showOverlay,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    ) {
                        // Channel info at top
                        previewChannel?.let { channel ->
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (channel.logo.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(channel.logo)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .padding(8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Column {
                                    Text(
                                        text = channel.name,
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TvRepository.currentPrograms[channel.id]?.let { program ->
                                        Text(
                                            text = program.title ?: "",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Controls hint at bottom
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text("▲ Previous Channel", color = Color.White.copy(alpha = 0.7f))
                            Text("▼ Next Channel", color = Color.White.copy(alpha = 0.7f))
                            Text("← Back to Menu", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }

                // Buffering indicator
                if (playerState.isBuffering) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .padding(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            }
        } else {
            // PREVIEW MODE - Menu with channel list and preview window
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, start = 24.dp)
                    .onKeyEvent { event ->
                        // Debounce for grid navigation
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp, Key.DirectionDown -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastChannelSwitchTime < channelSwitchDebounceMs) {
                                        true // Consume event but don't navigate
                                    } else {
                                        lastChannelSwitchTime = now
                                        false // Let normal focus handling work
                                    }
                                }
                                Key.Back, Key.Escape -> {
                                    onBack()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                // Categories
                CategoryFilter(
                    categories = TvRepository.channelCategories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Split View
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Channel Selection Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 12.dp)
                    ) {
                        if (filteredChannels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No channels found in this category", color = Color.Gray)
                            }
                        } else {
                            val onChannelClickAction: (Channel) -> Unit = { channel ->
                                if (previewChannel == channel) {
                                    // Same channel already playing - go to fullscreen!
                                    isFullscreen = true
                                    showOverlay = true
                                } else {
                                    // Different channel - instant play in preview
                                    isClickTriggered = true
                                    previewChannel = channel
                                }
                            }

                            when (viewMode) {
                                ViewMode.GRID -> {
                                    TvLazyVerticalGrid(
                                        columns = TvGridCells.Fixed(2),
                                        state = gridState,
                                        contentPadding = PaddingValues(
                                            start = 4.dp,
                                            top = 4.dp,
                                            end = 4.dp,
                                            bottom = 8.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            items = filteredChannels,
                                            key = { it.id }
                                        ) { channel ->
                                            val focusRequester = focusRequesters.getOrPut(channel.id) { FocusRequester() }

                                            ChannelCard(
                                                channel = channel,
                                                onClick = { onChannelClickAction(channel) },
                                                width = 120.dp,
                                                modifier = Modifier
                                                    .focusRequester(focusRequester)
                                                    .onFocusChanged {
                                                        if (it.isFocused) {
                                                            focusedChannel = channel
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                                ViewMode.LIST -> {
                                    TvLazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(bottom = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            items = filteredChannels,
                                            key = { it.id }
                                        ) { channel ->
                                            val focusRequester = focusRequesters.getOrPut(channel.id) { FocusRequester() }

                                            ChannelListItem(
                                                channel = channel,
                                                onClick = { onChannelClickAction(channel) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(focusRequester)
                                                    .onFocusChanged {
                                                        if (it.isFocused) {
                                                            focusedChannel = channel
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Preview Area
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .fillMaxHeight()
                            .padding(start = 16.dp, end = 24.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A1A2E).copy(alpha = 0.3f))
                    ) {
                        var schedulePrograms by remember { mutableStateOf<List<com.example.androidtviptvapp.data.api.ScheduleProgramItem>>(emptyList()) }
                        var isLoadingSchedule by remember { mutableStateOf(false) }
                        val infoChannel = debouncedFocusedChannel ?: previewChannel

                        LaunchedEffect(infoChannel?.id) {
                            if (infoChannel != null) {
                                delay(500)
                                isLoadingSchedule = true
                                try {
                                    schedulePrograms = TvRepository.getChannelSchedule(infoChannel.id)
                                } catch (e: Exception) {
                                    schedulePrograms = emptyList()
                                }
                                isLoadingSchedule = false
                            } else {
                                schedulePrograms = emptyList()
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
                        ) {
                            // Video preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.52f)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                if (previewChannel != null) {
                                    ChannelPreview(
                                        channel = previewChannel!!,
                                        isClickTriggered = isClickTriggered,
                                        onPlayStarted = { isClickTriggered = false },
                                        singletonPlayer = singletonPlayer,
                                        onPlayerReady = { playerViewRef = it }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Select a channel to preview",
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Channel Info & Schedule
                            Column(
                                modifier = Modifier
                                    .weight(0.48f)
                                    .fillMaxWidth()
                            ) {
                                if (infoChannel != null) {
                                    Text(
                                        text = infoChannel.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        maxLines = 1
                                    )

                                    if (infoChannel.description.isNotBlank()) {
                                        Text(
                                            text = infoChannel.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.5f),
                                            maxLines = 1
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Program Schedule",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color(0xFF60A5FA)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(1.dp)
                                                .background(Color(0xFF60A5FA).copy(alpha = 0.3f))
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isLoadingSchedule) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Loading...",
                                                color = Color.White.copy(alpha = 0.4f)
                                            )
                                        }
                                    } else if (schedulePrograms.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No schedule available",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.3f)
                                            )
                                        }
                                    } else {
                                        TvLazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentPadding = PaddingValues(bottom = 24.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(
                                                items = schedulePrograms,
                                                key = { it.id ?: it.start ?: it.hashCode() }
                                            ) { program ->
                                                ProgramScheduleItem(
                                                    time = formatProgramTime(program.start),
                                                    title = program.title ?: "Unknown Program",
                                                    duration = calculateDuration(program.start, program.end),
                                                    isLive = program.isLive
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Select a channel",
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ChannelPreview - Video preview component.
 * Uses the SINGLETON player passed from parent.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelPreview(
    channel: Channel,
    isClickTriggered: Boolean = false,
    onPlayStarted: () -> Unit = {},
    singletonPlayer: TvPlayerView,
    onPlayerReady: (TvPlayerView) -> Unit = {}
) {
    val sharedManager = SharedPlayerManager
    var playerViewRef by remember { mutableStateOf<TvPlayerView?>(null) }

    val skipDebounce = remember { sharedManager.shouldSkipDebounce() }
    var isScrolling by remember { mutableStateOf(!isClickTriggered) }
    var currentChannelToPlay by remember { mutableStateOf(channel) }

    LaunchedEffect(channel.id, isClickTriggered) {
        if (isClickTriggered) {
            isScrolling = false
            currentChannelToPlay = channel
            onPlayStarted()
        } else if (skipDebounce && sharedManager.isChannelPlaying(channel.id)) {
            currentChannelToPlay = channel
            isScrolling = false
        } else {
            isScrolling = true
            playerViewRef?.pause = true
            delay(500)
            currentChannelToPlay = channel
            isScrolling = false
        }
    }

    LaunchedEffect(currentChannelToPlay.id, isScrolling) {
        if (!isScrolling && playerViewRef != null) {
            val player = playerViewRef!!
            if (player.streamUrl != currentChannelToPlay.streamUrl) {
                player.playUrl(currentChannelToPlay.streamUrl)
                player.pause = false
                sharedManager.setCurrentChannel(currentChannelToPlay.id, currentChannelToPlay.streamUrl)
                TvRepository.triggerUpdatePrograms(currentChannelToPlay.id)
            } else {
                player.pause = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                singletonPlayer.apply {
                    resizeMode = com.example.androidtviptvapp.player.AdaptExoPlayerView.RESIZE_MODE_FIT
                    playerViewRef = this
                    onPlayerReady(this)
                    pause = true
                }
            },
            update = { /* handled by LaunchedEffect */ },
            onRelease = { view ->
                playerViewRef = null
                (view.parent as? android.view.ViewGroup)?.removeView(view)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isScrolling) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {}
        }
    }
}

@Composable
private fun ProgramScheduleItem(
    time: String,
    title: String,
    duration: String,
    isLive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color(0xFF3B82F6)),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (isLive) Color(0xFF1E3A5F) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isLive) Color(0xFF3B82F6) else Color(0xFF4B5563),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLive) Color(0xFF60A5FA) else Color(0xFF9CA3AF)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }

            if (isLive) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFDC2626), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun formatProgramTime(isoTime: String?): String {
    if (isoTime == null) return "--:--"
    return try {
        val parts = isoTime.split("T")
        if (parts.size >= 2) {
            val timeParts = parts[1].split(":")
            if (timeParts.size >= 2) {
                val hour = timeParts[0].toIntOrNull() ?: 0
                val minute = timeParts[1]
                val period = if (hour >= 12) "PM" else "AM"
                val hour12 = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
                String.format("%02d:%s %s", hour12, minute, period)
            } else isoTime
        } else isoTime
    } catch (e: Exception) {
        "--:--"
    }
}

private fun calculateDuration(startIso: String?, endIso: String?): String {
    if (startIso == null || endIso == null) return ""
    return try {
        val startParts = startIso.split("T")[1].split(":")
        val endParts = endIso.split("T")[1].split(":")
        val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
        var endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
        if (endMinutes < startMinutes) endMinutes += 24 * 60
        val durationMinutes = endMinutes - startMinutes
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        when {
            hours > 0 && minutes > 0 -> "$hours hour${if (hours > 1) "s" else ""} $minutes min"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
            else -> "$minutes min"
        }
    } catch (e: Exception) {
        ""
    }
}
