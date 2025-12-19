package com.example.androidtviptvapp.ui.screens

import com.example.androidtviptvapp.data.PlaybackManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    channelId: String? = null,
    onChannelChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // State for current surfing channel (starts with the passed one)
    val currentChannelId = remember(channelId) { mutableStateOf(channelId) }
    val currentUrl = remember(videoUrl) { mutableStateOf(videoUrl) }
    
    // Get shared player
    val exoPlayer = remember { PlaybackManager.getPlayer(context) }

    // Logic to find next/prev channel
    fun switchChannel(direction: Int) { // 1 for next, -1 for prev
        val cId = currentChannelId.value ?: return
        val channels = com.example.androidtviptvapp.data.TvRepository.channels
        val currentIndex = channels.indexOfFirst { it.id == cId }
        
        if (currentIndex != -1) {
            val ensureIndex = (currentIndex + direction + channels.size) % channels.size
            val nextChannel = channels[ensureIndex]
            
            android.util.Log.d("PlayerScreen", "Switching to: ${nextChannel.name}")
            currentChannelId.value = nextChannel.id
            currentUrl.value = nextChannel.streamUrl
            onChannelChanged(nextChannel.id)
        }
    }

    // Save the initial channel ID immediately when player opens
    // This ensures the channel persists even without switching
    LaunchedEffect(Unit) {
        channelId?.let { 
            onChannelChanged(it)
            android.util.Log.d("PlayerScreen", "Initial channel saved: $it")
        }
    }

    // Capture play/pause/navigation events
    val focusRequester = remember { FocusRequester() }
    
    // Track if we're retrying
    var isRetrying by remember { mutableStateOf(false) }

    // Load media (PlaybackManager handles "don't reload if same")
    LaunchedEffect(currentUrl.value) {
        val url = currentUrl.value
        try {
            android.util.Log.d("PlayerScreen", "Ensuring playback for: $url")
            errorMessage = null // Clear any previous error
            isRetrying = false
            exoPlayer.volume = 1.0f 
            PlaybackManager.playUrl(context, url)
        } catch (e: Exception) {
            android.util.Log.e("PlayerScreen", "Error loading media", e)
            errorMessage = "Load Error: ${e.message}"
        }
    }
    
    // Connect to PlaybackManager's error callback
    DisposableEffect(Unit) {
        PlaybackManager.onPlaybackError = { message, retrying ->
            // Empty message means playback recovered - clear error
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
                            false 
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Request focus so we can capture keys
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setKeepScreenOn(true)
                }
            },
            update = { playerView ->
                // Important: Switch playerview's player to the shared one
                // The previous view (in ChannelPreview) will be detached automatically when its Activity/Fragment/View is destroyed/removed
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )
        
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
