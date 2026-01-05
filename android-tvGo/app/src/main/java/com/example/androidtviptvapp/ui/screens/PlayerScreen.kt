package com.example.androidtviptvapp.ui.screens

import com.example.androidtviptvapp.data.PlaybackManager
import com.example.androidtviptvapp.data.ChannelPlaybackSource
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.data.ChannelsCache
import com.example.androidtviptvapp.player.PlayerView
import com.example.androidtviptvapp.player.AdaptExoPlayerView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * PlayerScreen using View-based PlayerView (OnTV-main pattern)
 */
@Composable
fun PlayerScreen(
    videoUrl: String,
    channelId: String? = null,
    onChannelChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
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
    
    // Overlay visibility
    var showOverlay by remember { mutableStateOf(true) }
    
    // Auto-hide overlay after 4 seconds
    LaunchedEffect(showOverlay, currentSource) {
        if (showOverlay) {
            delay(4000)
            showOverlay = false
        }
    }

    // Focus requester for key capture
    val focusRequester = remember { FocusRequester() }
    
    // PlayerView reference
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Switch channel
    fun switchChannel(direction: Int) {
        val next = playerView?.jumpChannel(direction)
        if (next != null) {
            currentSource = next
            onChannelChanged(next.channel.id)
            showOverlay = true
        }
    }

    // Save initial channel
    LaunchedEffect(Unit) {
        channelId?.let { 
            onChannelChanged(it)
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
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            switchChannel(1)
                            true
                        }
                        Key.DirectionDown -> {
                            switchChannel(-1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            showOverlay = !showOverlay
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Request focus for key capture
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        // View-based PlayerView (OnTV-main pattern)
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
                    
                    // Register with PlaybackManager
                    PlaybackManager.registerPlayerView(this)
                    
                    // Store reference
                    playerView = this
                    
                    // Start playback
                    val source = initialSource
                    if (source != null) {
                        play(source)
                    } else {
                        playUrl(videoUrl)
                    }
                }
            },
            update = { view ->
                // Update if needed
            },
            onRelease = { view ->
                // Cleanup
                PlaybackManager.unregisterPlayerView(view)
                view.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Channel info overlay
        AnimatedVisibility(
            visible = showOverlay && currentSource != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            ChannelInfoOverlay(
                source = currentSource!!,
                modifier = Modifier.padding(24.dp)
            )
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
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
            .padding(16.dp)
    ) {
        Row(
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
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column {
                // Channel name
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Current program
                if (currentProgram != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentProgram.title ?: "No program info",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    
                    if (currentProgram.start != null && currentProgram.end != null) {
                        Text(
                            text = "${formatTime(currentProgram.start)} - ${formatTime(currentProgram.end)}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
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
