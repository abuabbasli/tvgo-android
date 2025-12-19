package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
    
    // Update selection when returning with a new channel ID
    LaunchedEffect(initialChannelId) {
        if (initialChannelId != null) {
            val channel = TvRepository.channels.find { it.id == initialChannelId }
            if (channel != null) {
                focusedChannel = channel
                previewChannel = channel
                // Request focus on the channel card after a short delay to ensure it's composed
                kotlinx.coroutines.delay(100)
                focusRequesters[initialChannelId]?.requestFocus()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 24.dp) // Main padding
    ) {
        // 1. Categories on top
        CategoryFilter(
            categories = TvRepository.channelCategories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // 2. Split View (List/Grid + Preview)
        Row(modifier = Modifier.fillMaxSize()) {
            
            // Channel Selection Area (Left)
            Box(
                modifier = Modifier
                    .weight(1f)
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
                                items(filteredChannels) { channel ->
                                    val isFocused = focusedChannel == channel
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
                                            .zIndex(if (isFocused) 1f else 0f)
                                    )
                                }
                            }
                        }
                        ViewMode.LIST -> {
                            TvLazyColumn(
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredChannels) { channel ->
                                    ChannelListItem(
                                        channel = channel,
                                        onClick = { onChannelClickAction(channel) },
                                        modifier = Modifier
                                            .fillMaxWidth()
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

            // Preview Area (Right) - Bigger
            Box(
                modifier = Modifier
                    .weight(1.5f) // Increased weight to make it bigger
                    .fillMaxHeight()
                    .padding(start = 12.dp, end = 24.dp, bottom = 24.dp)
            ) {
                 Column(modifier = Modifier.fillMaxWidth()) {
                    if (previewChannel != null) {
                        ChannelPreview(channel = previewChannel!!)
                    } else {
                         Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f/9f)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Select a channel to preview")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    val infoChannel = focusedChannel ?: previewChannel
                    if (infoChannel != null) {
                        Text(
                            text = infoChannel.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = infoChannel.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        val currentProgram = infoChannel.schedule.find { it.isLive }
                        if (currentProgram != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LIVE",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = currentProgram.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                             Text(
                                text = "${currentProgram.time} • ${currentProgram.duration}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
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
        // No debounce needed for explicit click
        PlaybackManager.playUrl(context, channel.streamUrl)
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
