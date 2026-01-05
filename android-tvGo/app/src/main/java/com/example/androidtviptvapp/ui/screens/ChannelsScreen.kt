package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.example.androidtviptvapp.data.PlaybackManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.ui.components.CategoryFilter
import com.example.androidtviptvapp.ui.components.ChannelCard
import com.example.androidtviptvapp.ui.components.ChannelListItem
import com.example.androidtviptvapp.ui.components.ViewMode

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ChannelsScreen(
    viewMode: ViewMode = ViewMode.GRID,
    initialChannelId: String? = null,
    onChannelClick: (Channel) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("all") }
    // Initialize focus/preview with initialChannelId if provided
    var focusedChannel by remember { 
        mutableStateOf(
            if (initialChannelId != null) TvRepository.channels.find { it.id == initialChannelId } 
            else null
        ) 
    }
    var previewChannel by remember { 
        mutableStateOf(
            if (initialChannelId != null) TvRepository.channels.find { it.id == initialChannelId }
            else null
        ) 
    }
    
    // Map of FocusRequesters for each channel to enable programmatic focus
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    
    // Grid and list state for scrolling to specific channel
    val gridState = androidx.tv.foundation.lazy.grid.rememberTvLazyGridState()
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()
    
    val filteredChannels = remember(selectedCategory) {
        if (selectedCategory == "all") {
            TvRepository.channels
        } else {
            TvRepository.channels.filter { it.category == selectedCategory }
        }
    }

    LaunchedEffect(filteredChannels) {
        if (focusedChannel == null || focusedChannel !in filteredChannels) {
            focusedChannel = filteredChannels.firstOrNull()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 24.dp) // Main padding
    ) {
        // 1. Categories on top - fixed height
        CategoryFilter(
            categories = TvRepository.channelCategories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
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
                            onChannelClick(channel)
                        } else {
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
                                    key = { it.id }  // Stable key for efficient diffing
                                ) { channel ->
                                    // Get or create a FocusRequester for this channel
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
                // State for schedule programs
                var schedulePrograms by remember { mutableStateOf<List<com.example.androidtviptvapp.data.api.ScheduleProgramItem>>(emptyList()) }
                var isLoadingSchedule by remember { mutableStateOf(false) }

                val infoChannel = focusedChannel ?: previewChannel

                // Fetch schedule when channel changes - uses cached schedule from TvRepository
                LaunchedEffect(infoChannel?.id) {
                    if (infoChannel != null) {
                        isLoadingSchedule = true
                        try {
                            // Use cached schedule from TvRepository
                            schedulePrograms = TvRepository.getChannelSchedule(infoChannel.id)
                        } catch (e: Exception) {
                            android.util.Log.e("ChannelsScreen", "Failed to load schedule: ${e.message}")
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
                        if (previewChannel != null) {
                            ChannelPreview(channel = previewChannel!!)
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
                                    items(
                                        items = schedulePrograms,
                                        key = { it.id ?: it.start ?: it.hashCode() }  // Use id or start time as key
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

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ChannelPreview(channel: Channel) {
    val context = LocalContext.current
    
    // Use shared PlaybackManager
    val exoPlayer = remember { PlaybackManager.getPlayer(context) }

    LaunchedEffect(channel) {
        // Use debounced playback to prevent thrashing on rapid focus changes
        PlaybackManager.playUrlDebounced(context, channel.streamUrl)
    }

    // Don't release player here, it's shared!

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)) // Defines the clip shape for Compose
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                // Inflate layout with TextureView for proper rounded corner support
                val view = android.view.LayoutInflater.from(ctx)
                    .inflate(com.example.androidtviptvapp.R.layout.preview_player, null) as PlayerView
                
                view.apply {
                    setKeepScreenOn(true)
                    // Ensure view itself is clipped to outline for rounded corners
                    clipToOutline = true
                    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 16f * ctx.resources.displayMetrics.density
                        setColor(android.graphics.Color.BLACK)
                    }
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
