package com.example.androidtviptvapp.ui.screens

import android.graphics.Rect
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import com.example.androidtviptvapp.player.PlayerView as TvPlayerView
import com.example.androidtviptvapp.player.SharedPlayerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
 * Uses outputRect to constrain player to preview area:
 * - Preview mode: player.outputRect = preview box bounds
 * - Fullscreen mode: player.outputRect = null (fills screen)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewMode: ViewMode = ViewMode.GRID,
    initialChannelId: String? = null,
    onChannelClick: (Channel) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // FULLSCREEN STATE
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

    // Debounce for navigation
    var lastChannelSwitchTime by remember { mutableStateOf(0L) }
    val channelSwitchDebounceMs = 400L

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

    // Get singleton player ONCE
    val singletonPlayer = remember(context) {
        SharedPlayerManager.getOrCreatePlayer(context)
    }
    var playerViewRef by remember { mutableStateOf<TvPlayerView?>(null) }

    // Preview box bounds for outputRect
    var previewBoxRect by remember { mutableStateOf<Rect?>(null) }

    // Player state
    val playerState by SharedPlayerManager.playerState.collectAsState()
    var showOverlay by remember { mutableStateOf(true) }
    var isStreamPlaying by remember { mutableStateOf(false) }

    // Lifecycle
    DisposableEffect(Unit) {
        SharedPlayerManager.startTicking(scope)
        SharedPlayerManager.markAttached()
        onDispose {
            SharedPlayerManager.stopTicking()
            SharedPlayerManager.markDetached()
        }
    }

    // Update outputRect based on fullscreen state
    LaunchedEffect(isFullscreen, previewBoxRect) {
        playerViewRef?.let { player ->
            if (isFullscreen) {
                player.outputRect = null  // Fullscreen
                player.resizeMode = com.example.androidtviptvapp.player.AdaptExoPlayerView.RESIZE_MODE_FIT
            } else {
                previewBoxRect?.let { rect ->
                    player.outputRect = rect  // Preview area
                    player.resizeMode = com.example.androidtviptvapp.player.AdaptExoPlayerView.RESIZE_MODE_FILL
                }
            }
        }
    }

    // Auto-hide overlay in fullscreen
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

    // Initialize channels
    LaunchedEffect(filteredChannels) {
        if (focusedChannel == null || focusedChannel !in filteredChannels) {
            focusedChannel = filteredChannels.firstOrNull()
            debouncedFocusedChannel = focusedChannel
        }
        if (previewChannel == null || previewChannel !in filteredChannels) {
            previewChannel = filteredChannels.firstOrNull()
        }
    }

    // Handle initial channel
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

    // Play channel when preview changes
    LaunchedEffect(previewChannel?.id, isClickTriggered) {
        val channel = previewChannel ?: return@LaunchedEffect
        val player = playerViewRef ?: return@LaunchedEffect

        if (isClickTriggered) {
            if (player.streamUrl != channel.streamUrl) {
                player.playUrl(channel.streamUrl)
                SharedPlayerManager.setCurrentChannel(channel.id, channel.streamUrl)
                TvRepository.triggerUpdatePrograms(channel.id)
            }
            player.pause = false
            isStreamPlaying = true
            isClickTriggered = false
        } else if (!SharedPlayerManager.shouldSkipDebounce()) {
            player.pause = true
            delay(500)
            if (player.streamUrl != channel.streamUrl) {
                player.playUrl(channel.streamUrl)
                SharedPlayerManager.setCurrentChannel(channel.id, channel.streamUrl)
                TvRepository.triggerUpdatePrograms(channel.id)
            }
            player.pause = false
            isStreamPlaying = true
        } else {
            player.pause = false
            isStreamPlaying = true
        }
    }

    // Switch channel in fullscreen
    fun switchChannelFullscreen(direction: Int) {
        val now = System.currentTimeMillis()
        if (now - lastChannelSwitchTime < channelSwitchDebounceMs) return
        lastChannelSwitchTime = now

        val currentChannel = previewChannel ?: return
        val currentIndex = filteredChannels.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex < 0) return

        val newIndex = (currentIndex + direction).coerceIn(0, filteredChannels.size - 1)
        if (newIndex != currentIndex) {
            val newChannel = filteredChannels[newIndex]
            previewChannel = newChannel
            focusedChannel = newChannel
            isClickTriggered = true
            showOverlay = true
        }
    }

    // Focus management
    LaunchedEffect(isFullscreen) {
        delay(100)
        if (isFullscreen) {
            fullscreenFocusRequester.requestFocus()
        }
    }

    // Root container
    Box(modifier = Modifier.fillMaxSize()) {

        // ========== PLAYER - ALWAYS AT ROOT, USES outputRect ==========
        AndroidView(
            factory = { ctx ->
                singletonPlayer.apply {
                    playerViewRef = this
                }
            },
            update = { },
            onRelease = { },
            modifier = Modifier.fillMaxSize()
        )

        // ========== FULLSCREEN MODE OVERLAY ==========
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(fullscreenFocusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp -> { switchChannelFullscreen(-1); true }
                                Key.DirectionDown -> { switchChannelFullscreen(1); true }
                                Key.DirectionCenter, Key.Enter -> { showOverlay = !showOverlay; true }
                                Key.Back, Key.Escape -> {
                                    isFullscreen = false
                                    SharedPlayerManager.markReturningFromFullscreen()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                AnimatedVisibility(visible = showOverlay, enter = fadeIn(), exit = fadeOut()) {
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
                        previewChannel?.let { channel ->
                            Row(
                                modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (channel.logo.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(channel.logo).build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Column {
                                    Text(channel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    TvRepository.currentPrograms[channel.id]?.let {
                                        Text(it.title ?: "", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text("▲ Previous", color = Color.White.copy(alpha = 0.7f))
                            Text("▼ Next", color = Color.White.copy(alpha = 0.7f))
                            Text("← Back", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }

                if (playerState.isBuffering) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(48.dp).border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape))
                    }
                }
            }
        }

        // ========== PREVIEW MODE - YOUR ORIGINAL UI ==========
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, start = 24.dp)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp, Key.DirectionDown -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastChannelSwitchTime < channelSwitchDebounceMs) true
                                    else { lastChannelSwitchTime = now; false }
                                }
                                Key.Back, Key.Escape -> { onBack(); true }
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

                // Split View - YOUR ORIGINAL LAYOUT
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
                                if (previewChannel == channel && isStreamPlaying) {
                                    isFullscreen = true
                                    showOverlay = true
                                } else {
                                    isClickTriggered = true
                                    previewChannel = channel
                                }
                            }

                            when (viewMode) {
                                ViewMode.GRID -> {
                                    TvLazyVerticalGrid(
                                        columns = TvGridCells.Fixed(2),
                                        state = gridState,
                                        contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filteredChannels, key = { it.id }) { channel ->
                                            val focusRequester = focusRequesters.getOrPut(channel.id) { FocusRequester() }
                                            ChannelCard(
                                                channel = channel,
                                                onClick = { onChannelClickAction(channel) },
                                                width = 120.dp,
                                                modifier = Modifier
                                                    .focusRequester(focusRequester)
                                                    .onFocusChanged { if (it.isFocused) focusedChannel = channel }
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
                                        items(filteredChannels, key = { it.id }) { channel ->
                                            val focusRequester = focusRequesters.getOrPut(channel.id) { FocusRequester() }
                                            ChannelListItem(
                                                channel = channel,
                                                onClick = { onChannelClickAction(channel) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(focusRequester)
                                                    .onFocusChanged { if (it.isFocused) focusedChannel = channel }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Preview Area - YOUR ORIGINAL DESIGN
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
                            // Video Preview Box - capture position for outputRect
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.52f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black, RoundedCornerShape(16.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    .onGloballyPositioned { coordinates ->
                                        // Capture preview box position for outputRect
                                        val bounds = coordinates.boundsInRoot()
                                        previewBoxRect = Rect(
                                            bounds.left.toInt(),
                                            bounds.top.toInt(),
                                            bounds.right.toInt(),
                                            bounds.bottom.toInt()
                                        )
                                    }
                            ) {
                                // Player renders here via outputRect
                                if (previewChannel == null) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Select a channel to preview", color = Color.White.copy(alpha = 0.5f))
                                    }
                                }

                                // Buffering indicator
                                if (playerState.isBuffering && previewChannel != null) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Box(modifier = Modifier.size(32.dp).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Channel Info & Schedule - YOUR ORIGINAL DESIGN
                            Column(modifier = Modifier.weight(0.48f).fillMaxWidth()) {
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
                                            Text("Loading...", color = Color.White.copy(alpha = 0.4f))
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
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            contentPadding = PaddingValues(bottom = 24.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(schedulePrograms, key = { it.id ?: it.start ?: it.hashCode() }) { program ->
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
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Select a channel", color = Color.White.copy(alpha = 0.4f))
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

@Composable
private fun ProgramScheduleItem(time: String, title: String, duration: String, isLive: Boolean = false) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { },
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color(0xFF3B82F6)), shape = RoundedCornerShape(8.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(if (isLive) Color(0xFF1E3A5F) else Color.Transparent, RoundedCornerShape(4.dp))
                    .border(1.dp, if (isLive) Color(0xFF3B82F6) else Color(0xFF4B5563), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(time, style = MaterialTheme.typography.labelMedium, color = if (isLive) Color(0xFF60A5FA) else Color(0xFF9CA3AF))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1)
                Text(duration, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9CA3AF))
            }

            if (isLive) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFDC2626), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White)
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
    } catch (e: Exception) { "--:--" }
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
    } catch (e: Exception) { "" }
}
