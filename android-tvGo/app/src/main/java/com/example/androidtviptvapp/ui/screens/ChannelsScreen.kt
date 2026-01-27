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
import androidx.compose.animation.core.tween
import androidx.tv.foundation.PivotOffsets
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

    // OPTIMIZATION: Use derivedStateOf for filtered channels - only recomputes when dependencies change
    val allChannels = TvRepository.channels
    val filteredChannels by remember(selectedCategory, isBabyModeActive) {
        androidx.compose.runtime.derivedStateOf {
            when {
                isBabyModeActive -> allChannels.filter { it.category.equals("Uşaq", ignoreCase = true) }
                selectedCategory == "all" -> allChannels.toList()
                else -> allChannels.filter { it.category == selectedCategory }
            }
        }
    }

    // OPTIMIZATION: Use derivedStateOf for available categories
    val allCategories = TvRepository.channelCategories
    val availableCategories by remember(isBabyModeActive) {
        androidx.compose.runtime.derivedStateOf {
            if (isBabyModeActive) {
                allCategories.filter { it.id.equals("Uşaq", ignoreCase = true) }
            } else {
                allCategories.toList()
            }
        }
    }

    // ==========================================================================
    // TV SCROLLING BEST PRACTICES - Smooth one-item-at-a-time scrolling
    // ==========================================================================

    // Track scroll state - but DON'T update on every focus during rapid scroll
    var isActivelyScrolling by remember { mutableStateOf(false) }
    var lastFocusChangeTime by remember { mutableStateOf(0L) }

    // The currently focused channel (updates on every focus for navigation)
    var focusedChannel by remember {
        mutableStateOf<Channel?>(
            if (initialChannelId != null) {
                TvRepository.channels.find { it.id == initialChannelId }
            } else {
                null  // Will be set in LaunchedEffect
            }
        )
    }

    // DEBOUNCED focused channel for INFO DISPLAY ONLY - doesn't update during scroll
    var debouncedFocusedChannel by remember { mutableStateOf(focusedChannel) }

    // The channel being PREVIEWED (video playing) - separate from focus
    var previewChannel by remember {
        mutableStateOf(
            if (initialChannelId != null) TvRepository.channels.find { it.id == initialChannelId }
            else null
        )
    }

    // Track if user CLICKED (not just focused) - for instant playback
    var isClickTriggered by remember { mutableStateOf(false) }

    // FocusRequesters for programmatic focus
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // Grid and list state
    val gridState = androidx.tv.foundation.lazy.grid.rememberTvLazyGridState()
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()

    // ==========================================================================
    // SCROLL DETECTION - Detect rapid focus changes (key held down)
    // ==========================================================================
    LaunchedEffect(focusedChannel?.id) {
        val now = System.currentTimeMillis()
        val timeSinceLastFocus = now - lastFocusChangeTime
        lastFocusChangeTime = now

        // Rapid focus changes (< 200ms) = key is held down
        if (timeSinceLastFocus < 200 && timeSinceLastFocus > 0) {
            isActivelyScrolling = true
        }

        // Wait 400ms to see if more focus changes come
        kotlinx.coroutines.delay(400)

        // If no more changes in 400ms, scrolling stopped
        if (System.currentTimeMillis() - lastFocusChangeTime >= 400) {
            isActivelyScrolling = false
            // NOW update the debounced channel for info display
            focusedChannel?.let {
                debouncedFocusedChannel = it
            }
        }
    }

    // Number input for direct channel jump (remote number keys)
    var enteredNumber by remember { mutableStateOf("") }
    val numberInputTimeoutMs = 1500L  // Auto-jump after 1.5 seconds

    // KEY THROTTLING - Disabled for maximum scroll speed
    // Only re-enable if crash issues occur on detached nodes
    var lastNavKeyTime by remember { mutableStateOf(0L) }
    val navKeyThrottleMs = 50L  // Minimum time between nav key presses

    // Track which channel index should be focused after scroll/composition
    var targetFocusIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-jump to channel after number input timeout
    LaunchedEffect(enteredNumber) {
        if (enteredNumber.isNotEmpty()) {
            kotlinx.coroutines.delay(numberInputTimeoutMs)
            val orderNumber = enteredNumber.toIntOrNull()
            if (orderNumber != null) {
                val targetChannel = TvRepository.getChannelByOrder(orderNumber)
                if (targetChannel != null) {
                    android.util.Log.d("ChannelsScreen", "Number jump to channel: ${targetChannel.name} (id=${targetChannel.id})")

                    // Find the index in filtered list
                    val targetIndex = filteredChannels.indexOfFirst { it.id == targetChannel.id }
                    if (targetIndex >= 0) {
                        android.util.Log.d("ChannelsScreen", "Found channel at index $targetIndex")

                        // Update state variables
                        focusedChannel = targetChannel
                        debouncedFocusedChannel = targetChannel
                        previewChannel = targetChannel
                        // DON'T auto-play - let user explicitly click to play and store as last played
                        // isClickTriggered = true

                        // Set target index for scrolling
                        targetFocusIndex = targetIndex
                    } else {
                        android.util.Log.w("ChannelsScreen", "Channel ${targetChannel.name} not found in filtered list")
                    }
                }
            }
            enteredNumber = ""
        }
    }

    // Handle scrolling and focus when target index changes
    LaunchedEffect(targetFocusIndex) {
        val index = targetFocusIndex
        if (index != null && index >= 0 && index < filteredChannels.size) {
            try {
                android.util.Log.d("ChannelsScreen", "Scrolling to index $index in view mode $viewMode")

                // Scroll the list/grid to make the item visible
                when (viewMode) {
                    ViewMode.GRID -> {
                        gridState.scrollToItem(index)
                    }
                    ViewMode.LIST -> {
                        listState.scrollToItem(index)
                    }
                }

                // Wait for scroll to complete and item to be composed
                kotlinx.coroutines.delay(200)

                // Now request focus on the item
                val channel = filteredChannels[index]
                val requester = focusRequesters[channel.id]
                if (requester != null) {
                    android.util.Log.d("ChannelsScreen", "Requesting focus for ${channel.name} after scroll")
                    requester.requestFocus()
                } else {
                    android.util.Log.w("ChannelsScreen", "No focus requester for ${channel.id} after scroll")
                }

                // Clear the target
                targetFocusIndex = null
            } catch (e: Exception) {
                android.util.Log.e("ChannelsScreen", "Error during scroll and focus: ${e.message}")
                targetFocusIndex = null
            }
        }
    }
    
    // INFO AREA UPDATE - Only when scrolling stops
    // The debounced channel is already updated in the scroll detection above
    // This just handles the case of single-step navigation (not holding key)
    LaunchedEffect(focusedChannel?.id) {
        if (!isActivelyScrolling) {
            // Single press - update info area after short delay
            kotlinx.coroutines.delay(100)
            focusedChannel?.let {
                debouncedFocusedChannel = it
            }
        }
        // If scrolling, the scroll detection LaunchedEffect handles it
    }
    
    // Track when we enter the screen to restore focus
    var hasInitialized by remember { mutableStateOf(false) }

    // Initialize focus/preview when entering the screen or when filtered channels change
    LaunchedEffect(filteredChannels, initialChannelId) {
        // Determine which channel to focus on
        val channelToFocus = if (initialChannelId != null) {
            // Coming from fullscreen player or number jump - focus on that channel
            var channel = filteredChannels.find { it.id == initialChannelId }

            // If channel not in filtered list, switch to "all" category to show it
            if (channel == null && !isBabyModeActive) {
                val fullChannel = allChannels.find { it.id == initialChannelId }
                if (fullChannel != null) {
                    android.util.Log.d("ChannelsScreen", "Channel $initialChannelId not in current category, switching to 'all'")
                    selectedCategory = "all"
                    // Wait for recomposition with new filter
                    kotlinx.coroutines.delay(50)
                    channel = filteredChannels.find { it.id == initialChannelId }
                }
            }

            channel
        } else if (!hasInitialized || focusedChannel == null || focusedChannel !in filteredChannels) {
            // Check if we have a currently playing preview channel first
            if (previewChannel != null && previewChannel in filteredChannels) {
                android.util.Log.d("ChannelsScreen", "Restoring focus to currently playing preview channel: ${previewChannel?.name}")
                previewChannel
            } else {
                // Check if we have a last played channel stored
                val lastChannelId = ChannelFocusManager.getLastFocusedChannel()
                if (lastChannelId != null) {
                    // Returning from another section - restore last played channel
                    var channel = filteredChannels.find { it.id == lastChannelId }

                    // If last played channel not in filtered list, switch to "all" category
                    if (channel == null && !isBabyModeActive) {
                        val fullChannel = allChannels.find { it.id == lastChannelId }
                        if (fullChannel != null) {
                            android.util.Log.d("ChannelsScreen", "Last played channel not in current category, switching to 'all'")
                            selectedCategory = "all"
                            // Wait for recomposition with new filter
                            kotlinx.coroutines.delay(50)
                            channel = filteredChannels.find { it.id == lastChannelId }
                        }
                    }

                    channel
                } else {
                    // First time entering - start at first channel
                    null
                }
            }
        } else {
            // Keep current focused channel if still valid
            null
        }

        // Update focused channel if we found a target
        if (channelToFocus != null) {
            focusedChannel = channelToFocus
            debouncedFocusedChannel = channelToFocus

            // Find index and trigger focus
            val targetIndex = filteredChannels.indexOfFirst { it.id == channelToFocus.id }
            if (targetIndex >= 0) {
                android.util.Log.d("ChannelsScreen", "Initialization: setting target index $targetIndex for ${channelToFocus.name}")
                targetFocusIndex = targetIndex
            }
        } else if (!hasInitialized) {
            // First time - just focus on first channel
            val firstChannel = filteredChannels.firstOrNull()
            if (firstChannel != null) {
                focusedChannel = firstChannel
                debouncedFocusedChannel = firstChannel
                android.util.Log.d("ChannelsScreen", "First initialization: setting target index 0")
                targetFocusIndex = 0
            }
        }

        // Ensure preview channel is set
        if (previewChannel == null || previewChannel !in filteredChannels) {
            previewChannel = focusedChannel ?: filteredChannels.firstOrNull()
        }

        hasInitialized = true
    }
    
    // NOTE: Don't release player here! It's shared with PlayerScreen (fullscreen).
    // The player will be released when leaving the entire playback flow.

    // Get SharedPlayerManager to check returning from fullscreen state
    val sharedPlayerManager = com.example.androidtviptvapp.player.SharedPlayerManager

    // Update selection when returning with a new channel ID or jumping via number keys
    LaunchedEffect(initialChannelId) {
        if (initialChannelId != null) {
            val channel = TvRepository.channels.find { it.id == initialChannelId }
            if (channel != null) {
                android.util.Log.d("ChannelsScreen", "InitialChannelId jump to: ${channel.name} (id=$initialChannelId)")
                val isReturningFromFullscreen = sharedPlayerManager.returningFromFullscreen

                focusedChannel = channel
                previewChannel = channel
                debouncedFocusedChannel = channel

                // Find index and trigger scroll + focus
                val targetIndex = filteredChannels.indexOfFirst { it.id == initialChannelId }
                if (targetIndex >= 0) {
                    android.util.Log.d("ChannelsScreen", "InitialChannelId: setting target index $targetIndex")
                    targetFocusIndex = targetIndex
                }

                // DON'T auto-play on number jumps - let user explicitly click to play
                // Only the preview will update, not trigger full playback
                // When returning from fullscreen, the preview channel is already playing
                // so no need to trigger click
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // No throttling - let all navigation events through for maximum scroll speed
                false
            }
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
                    // ==========================================================
                    // CLICK ACTION - Two-stage: first click = preview, second = fullscreen
                    // ==========================================================
                    val onChannelClickAction: (Channel) -> Unit = { channel ->
                        if (previewChannel?.id == channel.id) {
                            // SECOND CLICK on same channel = go fullscreen
                            onChannelClick(channel)
                        } else {
                            // FIRST CLICK = play in preview area
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
                                verticalArrangement = Arrangement.spacedBy(2.dp),  // Minimal spacing
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                // Pivot offsets - focused item stays fixed, content scrolls (carousel mode)
                                pivotOffsets = PivotOffsets(
                                    parentFraction = 0.4f,
                                    childFraction = 0.0f
                                ),
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
                                verticalArrangement = Arrangement.spacedBy(0.dp),  // No spacing for fastest scroll
                                // Pivot offsets - focused item stays fixed, content scrolls (carousel mode)
                                pivotOffsets = PivotOffsets(
                                    parentFraction = 0.4f,
                                    childFraction = 0.0f
                                ),
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

                // OPTIMIZATION: Only load schedule when NOT actively scrolling
                // Wait for scroll to stop completely before loading
                LaunchedEffect(infoChannel?.id, isActivelyScrolling) {
                    if (infoChannel != null && !isActivelyScrolling) {
                        // Extra delay to ensure scrolling has truly stopped
                        kotlinx.coroutines.delay(400)

                        // Re-check that channel hasn't changed and we're not scrolling
                        if (debouncedFocusedChannel?.id == infoChannel.id && !isActivelyScrolling) {
                            // Check if we already have cached programs
                            val cached = TvRepository.getCachedPrograms(infoChannel.id)
                            if (cached != null) {
                                schedulePrograms = cached
                            } else {
                                isLoadingSchedule = true
                                try {
                                    schedulePrograms = TvRepository.getChannelSchedule(infoChannel.id)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    schedulePrograms = emptyList()
                                }
                                isLoadingSchedule = false
                            }
                        }
                    } else if (infoChannel == null) {
                        schedulePrograms = emptyList()
                    }
                    // When actively scrolling, don't update schedule at all
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
                        if (isLive) Color(0xFFE0E0E0) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isLive) Color(0xFFE0E0E0) else Color(0xFF4B5563),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLive) Color.Black else Color(0xFF9CA3AF)
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
 * ChannelPreview - Video preview with SIMPLE scroll optimization.
 *
 * STRATEGY:
 * - On click (isClickTriggered) -> instant play
 * - On focus change -> wait 500ms then play
 * - Player stays paused during the wait
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelPreview(
    channel: Channel,
    isClickTriggered: Boolean = false,
    onPlayStarted: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val sharedManager = com.example.androidtviptvapp.player.SharedPlayerManager

    // Lifecycle
    DisposableEffect(Unit) {
        sharedManager.startTicking(scope)
        onDispose { sharedManager.stopTicking() }
    }

    // Player reference
    var playerViewRef by remember { mutableStateOf<TvPlayerView?>(null) }

    // Current channel being played (may lag behind focused channel)
    var playingChannel by remember { mutableStateOf(channel) }

    // Simple debounced channel loading
    LaunchedEffect(channel.id, isClickTriggered) {
        if (isClickTriggered) {
            // INSTANT play on click
            playingChannel = channel
            playerViewRef?.let { player ->
                if (player.streamUrl != channel.streamUrl) {
                    player.playUrl(channel.streamUrl)
                }
                player.pause = false
            }
            sharedManager.setCurrentChannel(channel.id, channel.streamUrl)
            // Store as last played channel
            ChannelFocusManager.updatePlayedChannel(channel.id)
            onPlayStarted()
        } else {
            // Focus change - pause current and wait
            playerViewRef?.pause = true

            // Wait 500ms for scrolling to stop
            kotlinx.coroutines.delay(500)

            // Now play the new channel
            playingChannel = channel
            playerViewRef?.let { player ->
                if (player.streamUrl != channel.streamUrl) {
                    player.playUrl(channel.streamUrl)
                }
                player.pause = false
            }
            sharedManager.setCurrentChannel(channel.id, channel.streamUrl)
            // Store as last played channel
            ChannelFocusManager.updatePlayedChannel(channel.id)
            TvRepository.triggerUpdatePrograms(channel.id)
        }
    }

    // Track if player is paused (for overlay)
    var isPaused by remember { mutableStateOf(true) }

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
                    pause = true
                    isPaused = true
                }
            },
            update = { view ->
                isPaused = view.pause
            },
            onRelease = { view ->
                playerViewRef = null
                view.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Show overlay while paused/loading
        if (isPaused || playingChannel.id != channel.id) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                // Show channel name while loading
                if (playingChannel.id != channel.id) {
                    Text(
                        text = channel.name,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

