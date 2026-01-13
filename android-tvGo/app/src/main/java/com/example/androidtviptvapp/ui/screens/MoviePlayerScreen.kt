package com.example.androidtviptvapp.ui.screens

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
import com.example.androidtviptvapp.data.Movie
import com.example.androidtviptvapp.data.MoviePlaybackSource
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.player.AdaptExoPlayerView
import com.example.androidtviptvapp.player.PlayerView
import kotlinx.coroutines.delay

/**
 * MoviePlayerScreen - VOD player with OnTV-style controls
 * Features: seek bar, time display, forward/backward, resume position
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MoviePlayerScreen(
    movieId: String,
    videoUrl: String,
    resumePosition: Long = 0L,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Get movie from repository
    val movie = remember(movieId) {
        TvRepository.movies.find { it.id == movieId }
    }
    
    // Player state
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    
    // Overlay states
    var showOverlay by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    
    // Seek state
    var seekDirection by remember { mutableStateOf(0) } // -1 = backward, 0 = none, 1 = forward
    
    // Focus requester
    val focusRequester = remember { FocusRequester() }

    // Auto-hide overlay after 8 seconds
    LaunchedEffect(showOverlay, showControls, seekDirection) {
        if ((showOverlay || showControls) && seekDirection == 0) {
            delay(8000)
            showOverlay = false
            showControls = false
        }
    }
    
    // Update position periodically
    LaunchedEffect(playerView) {
        while (true) {
            delay(500)
            playerView?.let { pv ->
                currentPosition = pv.seek
                duration = pv.duration
                isPlaying = !pv.pause
                isBuffering = pv.isBuffering
            }
        }
    }
    
    // Seek handling
    LaunchedEffect(seekDirection) {
        while (seekDirection != 0) {
            playerView?.let { pv ->
                val seekAmount = 10000L // 10 seconds
                val newPos = (pv.seek + (seekDirection * seekAmount)).coerceIn(0, pv.duration)
                pv.seek = newPos
                currentPosition = newPos
            }
            delay(300) // Repeat seek every 300ms while held
        }
    }

    // Toggle play/pause
    fun togglePlayPause() {
        playerView?.let { pv ->
            pv.pause = !pv.pause
            isPlaying = !pv.pause
        }
    }
    
    // Seek forward
    fun seekForward() {
        playerView?.let { pv ->
            val newPos = (pv.seek + 10000L).coerceAtMost(pv.duration)
            pv.seek = newPos
            currentPosition = newPos
        }
    }
    
    // Seek backward
    fun seekBackward() {
        playerView?.let { pv ->
            val newPos = (pv.seek - 10000L).coerceAtLeast(0)
            pv.seek = newPos
            currentPosition = newPos
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (!showControls) {
                                showOverlay = true
                                showControls = true
                            } else {
                                togglePlayPause()
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (!showControls) {
                                showOverlay = true
                                showControls = true
                            }
                            seekBackward()
                            true
                        }
                        Key.DirectionRight -> {
                            if (!showControls) {
                                showOverlay = true
                                showControls = true
                            }
                            seekForward()
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (!showControls) {
                                showOverlay = true
                                showControls = true
                            }
                            true
                        }
                        Key.MediaPlayPause -> {
                            togglePlayPause()
                            true
                        }
                        Key.MediaRewind -> {
                            seekBackward()
                            showOverlay = true
                            true
                        }
                        Key.MediaFastForward -> {
                            seekForward()
                            showOverlay = true
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (showControls) {
                                showControls = false
                                showOverlay = false
                                true
                            } else {
                                // Save position before exiting
                                playerView?.let { pv ->
                                    TvRepository.saveMoviePosition(movieId, pv.seek)
                                }
                                onBack()
                                true
                            }
                        }
                        Key.Menu -> {
                            showOverlay = true
                            showControls = !showControls
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Request focus
        LaunchedEffect(showControls) {
            if (!showControls) {
                focusRequester.requestFocus()
            }
        }

        // Player view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = AdaptExoPlayerView.RESIZE_MODE_FIT
                    init()
                    playerView = this
                    
                    // Play as VOD with resume position
                    if (resumePosition > 0) {
                        // Start at resume position
                        // Note: openStream with startPosition will be used
                    }
                    playUrl(videoUrl, vod = true)
                    
                    // Seek to resume position after playback starts
                    if (resumePosition > 0) {
                        onPlaybackReady = {
                            seek = resumePosition
                        }
                    }
                }
            },
            onRelease = { view ->
                // Save position before release
                TvRepository.saveMoviePosition(movieId, view.seek)
                view.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Top gradient
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
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
        
        // Movie info overlay (top)
        AnimatedVisibility(
            visible = showOverlay && movie != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            MovieInfoOverlay(
                movie = movie!!,
                modifier = Modifier.padding(24.dp)
            )
        }
        
        // Bottom gradient
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
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
        
        // VOD controls (bottom)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VODControlsBar(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onPlayPause = { togglePlayPause() },
                onBackward = { seekBackward() },
                onForward = { seekForward() },
                onSeek = { newPos ->
                    playerView?.seek = newPos
                    currentPosition = newPos
                },
                modifier = Modifier.padding(24.dp)
            )
        }
        
        // Buffering indicator
        if (isBuffering) {
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
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(text = errorMessage!!, color = Color.Red)
            }
        }
    }
}

/**
 * VOD controls with seek bar
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VODControlsBar(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onBackward: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                text = formatDuration(duration),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Seek bar
        SeekBar(
            progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onSeek = { progress ->
                onSeek((progress * duration).toLong())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backward 10s
            VODControlButton(
                icon = Icons.Default.Replay10,
                onClick = onBackward,
                contentDescription = "Rewind 10s"
            )
            
            // Play/Pause (main)
            VODControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                onClick = onPlayPause,
                contentDescription = if (isPlaying) "Pause" else "Play",
                isMain = true
            )
            
            // Forward 10s
            VODControlButton(
                icon = Icons.Default.Forward10,
                onClick = onForward,
                contentDescription = "Forward 10s"
            )
        }
    }
}

/**
 * Simple seek bar
 */
@Composable
private fun SeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(Color.White)
        )
        
        // Thumb
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = (progress.coerceIn(0f, 1f) * 100).dp) // Approximate
                .width(4.dp)
                .background(Color.White)
        )
    }
}

/**
 * VOD control button
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VODControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    isMain: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val size = if (isMain) 64.dp else 48.dp
    val iconSize = if (isMain) 36.dp else 28.dp
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isMain) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.3f)
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
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Movie info overlay
 */
@Composable
private fun MovieInfoOverlay(
    movie: Movie,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Movie poster
        if (movie.thumbnail.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.thumbnail)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(100.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        Column {
            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            if (movie.year > 0) {
                Text(
                    text = "${movie.year} • ${movie.runtime} min",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
