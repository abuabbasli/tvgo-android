package com.example.androidtviptvapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.zIndex
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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ChannelsScreen(
    viewMode: ViewMode = ViewMode.LIST,
    initialChannelId: String? = null,
    onChannelClick: (Channel) -> Unit
) {
    // Read channel view size preference
    val channelViewSize = com.example.androidtviptvapp.data.UIPreferencesManager.channelViewSize

    // Check if baby mode is active - filter to kids content only
    val isBabyModeActive = BabyLockManager.isBabyModeActive

    // When baby mode is active, force "Uşaq" (Kids) category
    // Otherwise, restore the last selected category from ChannelFocusManager
    var selectedCategory by remember {
        mutableStateOf(
            if (isBabyModeActive) "Uşaq"
            else ChannelFocusManager.getLastCategory()
        )
    }

    // Update selected category when baby mode changes
    LaunchedEffect(isBabyModeActive) {
        selectedCategory = if (isBabyModeActive) "Uşaq" else ChannelFocusManager.getLastCategory()
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

    // ==========================================================================
    // FULLSCREEN MODE - Toggle in-place instead of navigating to PlayerScreen
    // ==========================================================================
    var isFullscreen by remember { mutableStateOf(false) }
    var fullscreenShowOverlay by remember { mutableStateOf(true) }
    var fullscreenShowControls by remember { mutableStateOf(false) }
    val fullscreenFocusRequester = remember { FocusRequester() }

    // Channel switching timing control for fullscreen
    var lastChannelSwitchTime by remember { mutableStateOf(0L) }
    val channelSwitchIntervalMs = 500L

    // Auto-hide overlay after 8 seconds
    LaunchedEffect(fullscreenShowOverlay, fullscreenShowControls, isFullscreen) {
        if (isFullscreen && (fullscreenShowOverlay || fullscreenShowControls)) {
            kotlinx.coroutines.delay(8000)
            fullscreenShowOverlay = false
            fullscreenShowControls = false
        }
    }

    // FocusRequesters for programmatic focus
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // Grid and list state
    val gridState = androidx.tv.foundation.lazy.grid.rememberTvLazyGridState()
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()

    // Pause player when leaving channels screen or app
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val sharedManager = com.example.androidtviptvapp.player.SharedPlayerManager
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> sharedManager.pause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    // Only resume if we have a channel playing
                    if (ChannelFocusManager.lastPlayedChannelId != null) {
                        sharedManager.resume()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Pause when composable leaves composition (navigating away)
            com.example.androidtviptvapp.player.SharedPlayerManager.pause()
        }
    }

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

    // Number input is handled globally by MainActivity via initialChannelId
    // This prevents duplicate handling and ensures consistent behavior

    // KEY THROTTLING - Disabled for maximum scroll speed
    // Only re-enable if crash issues occur on detached nodes
    var lastNavKeyTime by remember { mutableStateOf(0L) }
    val navKeyThrottleMs = 50L  // Minimum time between nav key presses

    // Track which channel index should be focused after scroll/composition
    var targetFocusIndex by remember { mutableStateOf<Int?>(null) }
    // When true, skip scrolling and just restore focus (e.g. returning from another section)
    var focusOnlyMode by remember { mutableStateOf(false) }

    // Handle scrolling and focus when target index changes
    // Uses retry mechanism for reliable backward scrolling
    LaunchedEffect(targetFocusIndex) {
        val index = targetFocusIndex ?: return@LaunchedEffect
        android.util.Log.d("ChannelsScreen", "LaunchedEffect(targetFocusIndex) triggered with index: $index, focusOnly=$focusOnlyMode")

        if (index < 0 || index >= filteredChannels.size) {
            android.util.Log.w("ChannelsScreen", "Index $index out of bounds (filteredChannels.size=${filteredChannels.size})")
            targetFocusIndex = null
            focusOnlyMode = false
            return@LaunchedEffect
        }

        try {
            val channel = filteredChannels[index]

            // Step 1: Wait a frame for composition
            kotlinx.coroutines.delay(50)

            // Step 2: Scroll to item (skip if focusOnlyMode)
            if (!focusOnlyMode) {
                try {
                    when (viewMode) {
                        ViewMode.GRID -> gridState.scrollToItem(index)
                        ViewMode.LIST -> listState.scrollToItem(index)
                    }
                } catch (e: IllegalStateException) {
                    kotlinx.coroutines.delay(100)
                    try {
                        when (viewMode) {
                            ViewMode.GRID -> gridState.scrollToItem(index)
                            ViewMode.LIST -> listState.scrollToItem(index)
                        }
                    } catch (e2: Exception) {
                        android.util.Log.e("ChannelsScreen", "scrollToItem retry failed: ${e2.message}")
                    }
                }
            }

            // Step 3: Wait for composition and retry focus request
            var attempts = 0
            var focusSucceeded = false

            while (attempts < 15 && !focusSucceeded) {
                kotlinx.coroutines.delay(50)
                val requester = focusRequesters[channel.id]
                if (requester != null) {
                    try {
                        requester.requestFocus()
                        focusSucceeded = true
                    } catch (e: Exception) {
                        // retry
                    }
                }
                attempts++
            }
        } catch (e: Exception) {
            android.util.Log.e("ChannelsScreen", "Error during scroll and focus: ${e.message}", e)
        } finally {
            targetFocusIndex = null
            focusOnlyMode = false
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

    // Track user-initiated category change to avoid auto-switching back
    var userChangedCategory by remember { mutableStateOf(false) }

    // Initialize focus/preview when entering the screen or when filtered channels change
    LaunchedEffect(filteredChannels, initialChannelId) {
        // Determine which channel to focus on
        val channelToFocus = if (initialChannelId != null) {
            // Coming from fullscreen player or number jump - focus on that channel
            var channel = filteredChannels.find { it.id == initialChannelId }

            // If channel not in filtered list, switch to "all" category to show it
            // But only if user didn't explicitly change the category
            if (channel == null && !isBabyModeActive && !userChangedCategory) {
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
        } else if (!hasInitialized) {
            // First time initialization only - check for last played channel
            // The category is already restored from ChannelFocusManager.getLastCategory()
            // Check if we have a currently playing preview channel first
            if (previewChannel != null && previewChannel in filteredChannels) {
                android.util.Log.d("ChannelsScreen", "Restoring focus to currently playing preview channel: ${previewChannel?.name}")
                previewChannel
            } else {
                // Check if we have a last played channel stored
                val lastChannelId = ChannelFocusManager.getLastFocusedChannel()
                if (lastChannelId != null) {
                    // Returning from another section - restore last played channel
                    // The category was already restored, so the channel should be in filteredChannels
                    val channel = filteredChannels.find { it.id == lastChannelId }
                    if (channel != null) {
                        android.util.Log.d("ChannelsScreen", "Restored last played channel: ${channel.name} in category: $selectedCategory")
                    } else {
                        android.util.Log.w("ChannelsScreen", "Last played channel $lastChannelId not found in category $selectedCategory")
                    }
                    channel
                } else {
                    // First time entering - start at first channel
                    null
                }
            }
        } else if (userChangedCategory) {
            // User changed category - focus on first channel in the new category
            android.util.Log.d("ChannelsScreen", "User changed category, focusing on first channel")
            userChangedCategory = false  // Reset the flag
            filteredChannels.firstOrNull()
        } else if (focusedChannel != null && focusedChannel in filteredChannels) {
            // Keep current focused channel if still valid in filtered list
            null
        } else {
            // Current focused channel not in filtered list - focus on first channel
            filteredChannels.firstOrNull()
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
        android.util.Log.d("ChannelsScreen", "LaunchedEffect(initialChannelId) triggered with: $initialChannelId")
        if (initialChannelId != null) {
            val channel = TvRepository.channels.find { it.id == initialChannelId }
            if (channel != null) {
                android.util.Log.d("ChannelsScreen", "InitialChannelId jump to: ${channel.name} (id=$initialChannelId, order=${channel.order})")

                focusedChannel = channel
                previewChannel = channel
                debouncedFocusedChannel = channel

                // Find index and trigger scroll + focus
                val targetIndex = filteredChannels.indexOfFirst { it.id == initialChannelId }
                android.util.Log.d("ChannelsScreen", "Target index in filteredChannels: $targetIndex (filteredChannels.size=${filteredChannels.size})")
                if (targetIndex >= 0) {
                    android.util.Log.d("ChannelsScreen", "InitialChannelId: setting target index $targetIndex for ${channel.name}")
                    targetFocusIndex = targetIndex
                } else {
                    android.util.Log.w("ChannelsScreen", "Channel ${channel.name} NOT found in filteredChannels!")
                }
            } else {
                android.util.Log.w("ChannelsScreen", "Channel with id $initialChannelId not found in TvRepository.channels")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // No throttling - let all navigation events through for maximum scroll speed
                // Number keys are handled by MainActivity globally
                false
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
            onCategorySelected = {
                if (!isBabyModeActive) {
                    userChangedCategory = true  // Mark as user-initiated category change
                    selectedCategory = it
                }
            },
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
                            // SECOND CLICK on same channel = go fullscreen IN-PLACE
                            // No navigation needed - just toggle fullscreen mode
                            com.example.androidtviptvapp.player.SharedPlayerManager.setFullscreen(true)
                            isFullscreen = true
                            fullscreenShowOverlay = true
                            fullscreenShowControls = false
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
                                        isPlaying = channel.id == ChannelFocusManager.lastPlayedChannelId,
                                        viewSize = channelViewSize,
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
                                selectedCategory = selectedCategory,
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
                                modifier = Modifier.fillMaxWidth(0.95f),
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
                                        .weight(1f)
                                        .focusProperties {
                                            exit = { direction ->
                                                // Allow left to go back to channel list, block up/down/right at boundaries
                                                if (direction == androidx.compose.ui.focus.FocusDirection.Left) {
                                                    FocusRequester.Default
                                                } else {
                                                    FocusRequester.Cancel
                                                }
                                            }
                                        },
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    itemsIndexed(
                                        items = schedulePrograms,
                                        key = { index, item -> "${index}_${item.id ?: item.start ?: item.hashCode()}" }  // Include index to guarantee uniqueness
                                    ) { index, program ->
                                        ProgramScheduleItem(
                                            time = formatProgramTime(program.start),
                                            title = program.title ?: "Unknown Program",
                                            duration = calculateDuration(program.start, program.end),
                                            isLive = program.isLive,
                                            viewSize = channelViewSize
                                        )
                                        // Divider line matching preview width
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.95f)
                                                .height(1.dp)
                                                .background(Color.White.copy(alpha = 0.1f))
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

        // Number input display is handled by MainActivity globally

        // ==========================================================================
        // FULLSCREEN OVERLAY - Shown on top of everything when fullscreen is active
        // Player is already fullscreen via GlobalPlayerOverlay, this handles UI/controls
        // ==========================================================================
        if (isFullscreen) {
            val playerView = remember { sharedPlayerManager.getPlayer() }

            // Channel switch function
            fun switchChannelWithRepeat(direction: Int, repeatCount: Int) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastSwitch = currentTime - lastChannelSwitchTime
                val shouldSwitch = if (repeatCount == 0) true
                    else timeSinceLastSwitch >= channelSwitchIntervalMs

                if (shouldSwitch) {
                    lastChannelSwitchTime = currentTime
                    val next = playerView?.jumpChannel(direction)
                    if (next != null) {
                        val newChannel = TvRepository.channels.find { it.id == next.channel.id }
                        if (newChannel != null) {
                            previewChannel = newChannel
                            ChannelFocusManager.updatePlayedChannel(newChannel.id, selectedCategory)
                        }
                        fullscreenShowOverlay = true
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(50f)
                    .background(Color.Black)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            val repeatCount = event.nativeKeyEvent.repeatCount
                            when (event.key) {
                                Key.DirectionUp -> {
                                    switchChannelWithRepeat(1, repeatCount); true
                                }
                                Key.DirectionDown -> {
                                    switchChannelWithRepeat(-1, repeatCount); true
                                }
                                Key.DirectionCenter, Key.Enter -> {
                                    if (!fullscreenShowControls) {
                                        fullscreenShowOverlay = true
                                        fullscreenShowControls = true
                                    }
                                    true
                                }
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    if (!fullscreenShowControls) {
                                        fullscreenShowOverlay = true
                                        fullscreenShowControls = true
                                        true
                                    } else false
                                }
                                Key.MediaPlayPause -> {
                                    sharedPlayerManager.togglePlayPause()
                                    true
                                }
                                Key.Back, Key.Escape -> {
                                    if (fullscreenShowControls) {
                                        fullscreenShowControls = false
                                        fullscreenShowOverlay = false
                                    } else {
                                        // EXIT fullscreen - instant, no navigation
                                        sharedPlayerManager.setFullscreen(false)
                                        isFullscreen = false
                                        // Restore focus to the channel item without scrolling
                                        val channelId = previewChannel?.id
                                        if (channelId != null) {
                                            focusRequesters[channelId]?.requestFocus()
                                        }
                                    }
                                    true
                                }
                                Key.Menu -> {
                                    fullscreenShowOverlay = true
                                    fullscreenShowControls = !fullscreenShowControls
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .focusRequester(fullscreenFocusRequester)
                    .focusable()
            ) {
                // Request focus for key capture
                LaunchedEffect(isFullscreen) {
                    if (isFullscreen) {
                        fullscreenFocusRequester.requestFocus()
                    }
                }

                // Top gradient for info overlay
                AnimatedVisibility(
                    visible = fullscreenShowOverlay,
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
                AnimatedVisibility(
                    visible = fullscreenShowOverlay && previewChannel != null,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    previewChannel?.let { ch ->
                        FullscreenChannelInfoOverlay(
                            channel = ch,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }

                // Bottom gradient for controls
                AnimatedVisibility(
                    visible = fullscreenShowControls,
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

                // Buffering indicator
                val playerState by sharedPlayerManager.playerState.collectAsState()
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

                // Error display
                if (playerState.error != null) {
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
                            text = playerState.error!!,
                            color = if (playerState.isRetrying) Color(0xFFFFA500) else Color.Red
                        )
                    }
                }

                // Player controls (bottom) - play/pause, rewind, forward, prev/next
                AnimatedVisibility(
                    visible = fullscreenShowControls,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    FullscreenPlayerControlsBar(
                        isPlaying = playerState.isPlaying,
                        onPlayPause = { sharedPlayerManager.togglePlayPause() },
                        onPrev = { switchChannelWithRepeat(-1, 0) },
                        onNext = { switchChannelWithRepeat(1, 0) },
                        onBackward = { sharedPlayerManager.seekRelative(-10_000) },
                        onForward = { sharedPlayerManager.seekRelative(10_000) },
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }  // End Box
}

/**
 * Channel info overlay for fullscreen mode
 */
@Composable
private fun FullscreenChannelInfoOverlay(
    channel: Channel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentProgram = TvRepository.currentPrograms[channel.id]

    Row(
        modifier = modifier,
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
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
        }

        Column {
            Text(
                text = channel.displayName,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (currentProgram != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentProgram.title ?: "No program info",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp
                )

                if (currentProgram.start != null && currentProgram.end != null) {
                    Text(
                        text = "${formatProgramTime(currentProgram.start)} - ${formatProgramTime(currentProgram.end)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Player controls bar for fullscreen mode
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FullscreenPlayerControlsBar(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackward: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // LIVE indicator
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

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous channel
            FullscreenControlButton(
                icon = Icons.Filled.SkipPrevious,
                onClick = onPrev,
                contentDescription = "Previous"
            )

            // Rewind 10s
            FullscreenControlButton(
                icon = Icons.Filled.FastRewind,
                onClick = onBackward,
                contentDescription = "Rewind"
            )

            // Play/Pause (main button)
            FullscreenControlButton(
                icon = if (isPlaying) Icons.Filled.Pause
                       else Icons.Filled.PlayArrow,
                onClick = onPlayPause,
                contentDescription = if (isPlaying) "Pause" else "Play",
                isMain = true
            )

            // Forward 10s
            FullscreenControlButton(
                icon = Icons.Filled.FastForward,
                onClick = onForward,
                contentDescription = "Forward"
            )

            // Next channel
            FullscreenControlButton(
                icon = Icons.Filled.SkipNext,
                onClick = onNext,
                contentDescription = "Next"
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FullscreenControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    isMain: Boolean = false
) {
    val size = if (isMain) 64.dp else 48.dp
    val iconSize = if (isMain) 36.dp else 24.dp

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (isMain) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.3f)
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = BorderStroke(2.dp, Color.White),
                shape = CircleShape
            )
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun ProgramScheduleItem(
    time: String,
    title: String,
    duration: String,
    isLive: Boolean = false,
    viewSize: com.example.androidtviptvapp.data.ChannelViewSize = com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM
) {
    val rowVerticalPadding = when (viewSize) {
        com.example.androidtviptvapp.data.ChannelViewSize.SMALL -> 6.dp
        com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM -> 10.dp
        com.example.androidtviptvapp.data.ChannelViewSize.LARGE -> 14.dp
    }
    val timeTextStyle = when (viewSize) {
        com.example.androidtviptvapp.data.ChannelViewSize.SMALL -> MaterialTheme.typography.labelSmall
        com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM -> MaterialTheme.typography.labelMedium
        com.example.androidtviptvapp.data.ChannelViewSize.LARGE -> MaterialTheme.typography.bodyMedium
    }
    val titleTextStyle = when (viewSize) {
        com.example.androidtviptvapp.data.ChannelViewSize.SMALL -> MaterialTheme.typography.bodyMedium
        com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM -> MaterialTheme.typography.bodyLarge
        com.example.androidtviptvapp.data.ChannelViewSize.LARGE -> MaterialTheme.typography.titleMedium
    }
    val durationTextStyle = when (viewSize) {
        com.example.androidtviptvapp.data.ChannelViewSize.SMALL -> MaterialTheme.typography.labelSmall
        com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM -> MaterialTheme.typography.bodySmall
        com.example.androidtviptvapp.data.ChannelViewSize.LARGE -> MaterialTheme.typography.bodyMedium
    }
    val badgePaddingH = when (viewSize) {
        com.example.androidtviptvapp.data.ChannelViewSize.SMALL -> 6.dp
        com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM -> 8.dp
        com.example.androidtviptvapp.data.ChannelViewSize.LARGE -> 10.dp
    }
    val badgePaddingV = when (viewSize) {
        com.example.androidtviptvapp.data.ChannelViewSize.SMALL -> 2.dp
        com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM -> 4.dp
        com.example.androidtviptvapp.data.ChannelViewSize.LARGE -> 6.dp
    }

    androidx.tv.material3.Surface(
        onClick = { /* No action needed */ },
        modifier = Modifier
            .fillMaxWidth(0.95f),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(6.dp)
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF2A2A2A)
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(6.dp)
            )
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(
            focusedScale = 1.0f
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = rowVerticalPadding),
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
                    .padding(horizontal = badgePaddingH, vertical = badgePaddingV)
            ) {
                Text(
                    text = time,
                    style = timeTextStyle,
                    color = if (isLive) Color.Black else Color(0xFF9CA3AF)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title and duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = titleTextStyle,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = duration,
                    style = durationTextStyle,
                    color = Color(0xFF9CA3AF)
                )
            }

            // LIVE badge
            if (isLive) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                        .padding(horizontal = badgePaddingH, vertical = badgePaddingV)
                ) {
                    Text(
                        text = "LIVE",
                        style = timeTextStyle,
                        color = Color(0xFFE0E0E0)
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
 * ChannelPreview - Reports preview bounds to GlobalPlayerOverlay.
 *
 * STRATEGY (GLOBAL OVERLAY MODE):
 * - Does NOT embed the player view directly
 * - Reports its position/size to SharedPlayerManager
 * - GlobalPlayerOverlay (at MainActivity level) shows the player at those bounds
 * - Player NEVER moves between containers, just changes size/position via animation
 * - This prevents MediaCodec errors and enables seamless fullscreen transitions
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelPreview(
    channel: Channel,
    isClickTriggered: Boolean = false,
    selectedCategory: String = "all",
    onPlayStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sharedManager = com.example.androidtviptvapp.player.SharedPlayerManager

    // Lifecycle - start ticking for player health monitoring
    DisposableEffect(Unit) {
        sharedManager.startTicking(scope)
        onDispose { sharedManager.stopTicking() }
    }

    // Current channel being played (may lag behind focused channel)
    var playingChannel by remember { mutableStateOf(channel) }

    // Track if player is paused (for overlay)
    var isPaused by remember { mutableStateOf(true) }

    // Check if we're returning from fullscreen - skip debounce and use existing playback
    val isReturningFromFullscreen = sharedManager.returningFromFullscreen

    // Handle channel loading with debounce
    LaunchedEffect(channel.id, isClickTriggered) {
        // Clear the returning from fullscreen flag after first use
        if (isReturningFromFullscreen) {
            sharedManager.clearReturningFromFullscreen()
            // If returning from fullscreen and same channel is playing, just update state
            if (sharedManager.isChannelPlaying(channel.id)) {
                playingChannel = channel
                sharedManager.resume()
                isPaused = false
                onPlayStarted()
                return@LaunchedEffect
            }
        }

        if (isClickTriggered) {
            // INSTANT play on click
            playingChannel = channel
            // Use SharedPlayerManager.playChannel which skips reload if same channel
            sharedManager.playChannel(channel.id, channel.name, channel.streamUrl)
            sharedManager.resume()
            isPaused = false
            // Store as last played channel and category
            ChannelFocusManager.updatePlayedChannel(channel.id, selectedCategory)
            onPlayStarted()
        } else {
            // Focus change - pause current and wait
            sharedManager.pause()
            isPaused = true

            // Wait 500ms for scrolling to stop
            kotlinx.coroutines.delay(500)

            // Now play the new channel
            playingChannel = channel
            // Use SharedPlayerManager.playChannel which skips reload if same channel
            sharedManager.playChannel(channel.id, channel.name, channel.streamUrl)
            sharedManager.resume()
            isPaused = false
            // Store as last played channel and category
            ChannelFocusManager.updatePlayedChannel(channel.id, selectedCategory)
            TvRepository.triggerUpdatePrograms(channel.id)
        }
    }

    // The preview area - report its bounds to GlobalPlayerOverlay
    // Using 95% width to avoid covering channel names underneath
    Box(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .aspectRatio(16f / 9f)
            .background(Color.Black, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .onGloballyPositioned { coordinates ->
                // Report preview bounds to SharedPlayerManager for GlobalPlayerOverlay
                val position = coordinates.positionInRoot()
                val size = coordinates.size
                with(density) {
                    sharedManager.setPreviewBounds(
                        x = position.x.toDp().value,
                        y = position.y.toDp().value,
                        width = size.width.toDp().value,
                        height = size.height.toDp().value
                    )
                }
            }
    ) {
        // Just a placeholder - the actual player is shown by GlobalPlayerOverlay
        // Show overlay while loading
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

