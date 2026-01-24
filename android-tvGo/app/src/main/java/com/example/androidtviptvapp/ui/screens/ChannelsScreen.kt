package com.example.androidtviptvapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import com.example.androidtviptvapp.data.PlaybackManager
import com.example.androidtviptvapp.player.PlayerView as TvPlayerView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
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
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.ui.components.CategoryFilter
import com.example.androidtviptvapp.ui.components.ChannelCard
import com.example.androidtviptvapp.ui.components.ChannelListItem
import com.example.androidtviptvapp.ui.components.ViewMode
import com.example.androidtviptvapp.ui.screens.BabyLockManager

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewMode: ViewMode = ViewMode.LIST,
    initialChannelId: String? = null,
    onChannelClick: (Channel) -> Unit
) {
    // Check if baby mode is active - filter to kids content only
    val isBabyModeActive = BabyLockManager.isBabyModeActive

    // When baby mode is active, force "Uşaq" (Kids) category
    var selectedCategory by remember { mutableStateOf(if (isBabyModeActive) "Uşaq" else "all") }

    // Update selected category when baby mode changes
    LaunchedEffect(isBabyModeActive) {
        selectedCategory = if (isBabyModeActive) "Uşaq" else "all"
    }
    // Initialize focus/preview with initialChannelId if provided
    var focusedChannel by remember { 
        mutableStateOf(
            if (initialChannelId != null) TvRepository.channels.find { it.id == initialChannelId } 
            else null
        ) 
    }
    
    // DEBOUNCED focused channel for info display - doesn't update immediately during scroll
    var debouncedFocusedChannel by remember { mutableStateOf(focusedChannel) }
    
    var previewChannel by remember { 
        mutableStateOf(
            if (initialChannelId != null) TvRepository.channels.find { it.id == initialChannelId }
            else null
        ) 
    }
    
    // Track if preview change was triggered by click (instant play) vs focus (debounce)
    var isClickTriggered by remember { mutableStateOf(false) }
    
    // Map of FocusRequesters for each channel to enable programmatic focus
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    
    // Grid and list state for scrolling to specific channel
    val gridState = androidx.tv.foundation.lazy.grid.rememberTvLazyGridState()
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()
    
    val filteredChannels = remember(selectedCategory, isBabyModeActive) {
        val baseChannels = if (isBabyModeActive) {
            // Baby mode: only kids channels (Uşaq category)
            TvRepository.channels.filter { it.category.equals("Uşaq", ignoreCase = true) }
        } else if (selectedCategory == "all") {
            TvRepository.channels
        } else {
            TvRepository.channels.filter { it.category == selectedCategory }
        }
        baseChannels
    }

    // Available categories - limited in baby mode
    val availableCategories = remember(isBabyModeActive) {
        if (isBabyModeActive) {
            // Only show Kids category in baby mode
            TvRepository.channelCategories.filter { it.id.equals("Uşaq", ignoreCase = true) }
        } else {
            TvRepository.channelCategories
        }
    }

    // Number input for direct channel jump (remote number keys)
    var enteredNumber by remember { mutableStateOf("") }
    val numberInputTimeoutMs = 1500L  // Auto-jump after 1.5 seconds

    // Auto-jump to channel after number input timeout
    LaunchedEffect(enteredNumber) {
        if (enteredNumber.isNotEmpty()) {
            kotlinx.coroutines.delay(numberInputTimeoutMs)
            val orderNumber = enteredNumber.toIntOrNull()
            if (orderNumber != null) {
                val targetChannel = TvRepository.getChannelByOrder(orderNumber)
                if (targetChannel != null) {
                    // Update focus and preview
                    focusedChannel = targetChannel
                    previewChannel = targetChannel
                    isClickTriggered = true

                    // Find index and scroll to it
                    val index = filteredChannels.indexOfFirst { it.id == targetChannel.id }
                    if (index >= 0) {
                        when (viewMode) {
                            ViewMode.GRID -> gridState.scrollToItem(index)
                            ViewMode.LIST -> listState.scrollToItem(index)
                        }
                        kotlinx.coroutines.delay(100)
                        focusRequesters[targetChannel.id]?.requestFocus()
                    }
                }
            }
            enteredNumber = ""
        }
    }
    
    // Debounce focus updates - only update info area after 150ms of stability
    // This prevents recomposition during fast scrolling
    LaunchedEffect(focusedChannel?.id) {
        focusedChannel?.let { channel ->
            kotlinx.coroutines.delay(150) // Short debounce for info area
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
    
    // NOTE: Don't release player here! It's shared with PlayerScreen (fullscreen).
    // The player will be released when leaving the entire playback flow.
    
    // Update selection when returning with a new channel ID - scroll to it and focus
    LaunchedEffect(initialChannelId) {
        if (initialChannelId != null) {
            val channel = TvRepository.channels.find { it.id == initialChannelId }
            if (channel != null) {
                focusedChannel = channel
                previewChannel = channel
                
                // Find index of the channel in filtered list
                val index = filteredChannels.indexOfFirst { it.id == initialChannelId }
                if (index >= 0) {
                    // Scroll to the channel first
                    when (viewMode) {
                        ViewMode.GRID -> {
                            // In grid with 2 columns, we need to scroll to the row
                            gridState.scrollToItem(index)
                        }
                        ViewMode.LIST -> {
                            listState.scrollToItem(index)
                        }
                    }
                    
                    // Wait for scroll and composition, then request focus
                    kotlinx.coroutines.delay(150)
                    focusRequesters[initialChannelId]?.requestFocus()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                // Handle number keys for direct channel jump
                if (event.type == KeyEventType.KeyDown) {
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, start = 24.dp) // Main padding
        ) {
        // 1. Categories on top - fixed height (limited in baby mode)
        CategoryFilter(
            categories = availableCategories,
            selectedCategory = selectedCategory,
            onCategorySelected = { if (!isBabyModeActive) selectedCategory = it },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 2. Split View (List/Grid + Preview) - takes remaining space
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            
            // Channel Selection Area (Left)
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
                            // Same channel - go to fullscreen
                            onChannelClick(channel)
                        } else {
                            // Different channel - instant play (no debounce)
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
                                    start = 8.dp,
                                    top = 8.dp,
                                    end = 8.dp,
                                    bottom = 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = filteredChannels,
                                    key = { it.id }  // Stable key for efficient diffing
                                ) { channel ->
                                    // Get or create a FocusRequester for this channel
                                    val focusRequester = focusRequesters.getOrPut(channel.id) { FocusRequester() }

                                    ChannelCard(
                                        channel = channel,
                                        onClick = { onChannelClickAction(channel) },
                                        width = 160.dp,
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
                                    key = { it.id }  // Stable key for efficient diffing
                                ) { channel ->
                                    // Get or create a FocusRequester for this channel
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

            // Preview Area (Right) - with visual separation
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1A2E).copy(alpha = 0.3f))
            ) {
                // State for schedule programs - DEBOUNCED to not load during scroll
                var schedulePrograms by remember { mutableStateOf<List<com.example.androidtviptvapp.data.api.ScheduleProgramItem>>(emptyList()) }
                var isLoadingSchedule by remember { mutableStateOf(false) }

                // Use debounced channel for info display - prevents recomposition during scroll
                val infoChannel = debouncedFocusedChannel ?: previewChannel

                // DEBOUNCED schedule loading - only load after 500ms of stability
                // This prevents lag during scroll
                LaunchedEffect(infoChannel?.id) {
                    if (infoChannel != null) {
                        // Cancel any pending load and wait 500ms (scroll debounce)
                        kotlinx.coroutines.delay(500)
                        
                        isLoadingSchedule = true
                        try {
                            // Use cached schedule from TvRepository
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
                    // Video preview - proportional height (52% of available space)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.52f)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        // Use local variable to avoid race conditions with mutable state
                        val channelToPreview = previewChannel
                        if (channelToPreview != null) {
                            ChannelPreview(
                                channel = channelToPreview,
                                isClickTriggered = isClickTriggered,
                                onPlayStarted = { isClickTriggered = false }
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

                    // Channel Info & Schedule - remaining space
                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxWidth()
                    ) {
                        if (infoChannel != null) {
                            // Channel Name - more prominent
                            Text(
                                text = infoChannel.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                maxLines = 1
                            )
                            
                            // Channel Description
                            if (infoChannel.description.isNotBlank()) {
                                Text(
                                    text = infoChannel.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Program Schedule Header with divider effect
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
                            
                            // Scrollable Program List
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
                                // Scrollable list of programs
                                TvLazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    itemsIndexed(
                                        items = schedulePrograms,
                                        key = { index, item -> "${index}_${item.id ?: item.start ?: item.hashCode()}" }  // Include index to guarantee uniqueness
                                    ) { index, program ->
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
                            // No channel selected state
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
        }  // End Column

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
    }  // End Box
}

@Composable
private fun ProgramScheduleItem(
    time: String,
    title: String,
    duration: String,
    isLive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    
    androidx.tv.material3.Surface(
        onClick = { /* No action needed */ },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = BorderStroke(2.dp, Color(0xFF3B82F6)),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(
            focusedScale = 1.02f
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time badge
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
            
            // Title and duration
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
            
            // LIVE badge
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

// Helper functions to format program times
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
        if (endMinutes < startMinutes) endMinutes += 24 * 60 // Handle overnight
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

/**
 * ChannelPreview - Video preview with OnTV-style scroll optimization.
 * 
 * CRITICAL PATTERN (from OnTV-main):
 * - Player is PAUSED during scroll to prevent frame drops
 * - Only resumes playback after user stops scrolling for 500ms
 * - EXCEPT when isClickTriggered=true (user clicked) -> instant play
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelPreview(
    channel: Channel,
    isClickTriggered: Boolean = false,
    onPlayStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val sharedManager = com.example.androidtviptvapp.player.SharedPlayerManager
    
    // Lifecycle - start/stop ticking
    DisposableEffect(Unit) {
        sharedManager.startTicking(scope)
        onDispose {
            sharedManager.stopTicking()
        }
    }
    
    val skipDebounce = remember { sharedManager.shouldSkipDebounce() }
    
    // Scroll state - tracks if user is actively scrolling
    var isScrolling by remember { mutableStateOf(!isClickTriggered) }
    var currentChannelToPlay by remember { mutableStateOf(channel) }
    
    // Player reference
    var playerViewRef by remember { mutableStateOf<TvPlayerView?>(null) }
    
    // SCROLL DEBOUNCE: 500ms (matches OnTV's DELAY_BEFORE_OPEN_PROGRAMS)
    // BUT: skip debounce if user clicked (isClickTriggered) or returning from fullscreen
    LaunchedEffect(channel.id, isClickTriggered) {
        if (isClickTriggered) {
            // User clicked - instant play, no debounce!
            isScrolling = false
            currentChannelToPlay = channel
            onPlayStarted() // Reset the flag
        } else if (skipDebounce && sharedManager.isChannelPlaying(channel.id)) {
            // Returning from fullscreen - instant play
            currentChannelToPlay = channel
            isScrolling = false
        } else {
            // Focus change (scrolling) - pause and debounce
            isScrolling = true
            playerViewRef?.pause = true
            
            kotlinx.coroutines.delay(500)
            currentChannelToPlay = channel
            isScrolling = false
        }
    }
    
    // PLAY only when not scrolling
    LaunchedEffect(currentChannelToPlay.id, isScrolling) {
        if (!isScrolling) {
            // Use local variable to avoid race conditions
            val player = playerViewRef ?: return@LaunchedEffect
            if (player.streamUrl != currentChannelToPlay.streamUrl) {
                // Load new stream and RESUME playback
                player.playUrl(currentChannelToPlay.streamUrl)
                player.pause = false // Critical: resume after loading!
                sharedManager.setCurrentChannel(currentChannelToPlay.id, currentChannelToPlay.streamUrl)
                TvRepository.triggerUpdatePrograms(currentChannelToPlay.id)
            } else {
                // Same stream - just resume
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
                TvPlayerView(ctx).apply {
                    resizeMode = com.example.androidtviptvapp.player.AdaptExoPlayerView.RESIZE_MODE_FIT
                    init()
                    playerViewRef = this
                    // Start paused until debounce completes
                    pause = true
                }
            },
            update = { /* handled by LaunchedEffect */ },
            onRelease = { view ->
                playerViewRef = null
                view.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Show subtle overlay while scrolling (player is paused)
        if (isScrolling) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                // Empty - just dims the paused frame
            }
        }
    }
}

