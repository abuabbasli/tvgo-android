package com.example.androidtviptvapp.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.*

/**
 * Optimized PlaybackManager for TV boxes:
 * - Uses hardware decoders for better performance
 * - Configurable buffer settings via AppConfig
 * - Intelligent retry mechanism
 * - Proper resource cleanup
 */

@OptIn(UnstableApi::class)
object PlaybackManager {
    private var exoPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    private var retryCount = 0
    private var retryJob: Job? = null
    
    private const val SILENT_RETRIES = 3      // First 3 retries are completely silent
    private const val MAX_RETRIES = 5         // Total retries before giving up
    private const val RETRY_DELAY_MS = 800L   // Faster retry for seamless experience
    
    // Error callback for UI to display messages
    var onPlaybackError: ((String, Boolean) -> Unit)? = null // (message, isRetrying)

    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = createOptimizedPlayer(context)
            setupErrorListener()
        }
        return exoPlayer!!
    }
    
    private fun setupErrorListener() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }
            
            override fun onPlayerErrorChanged(error: PlaybackException?) {
                // Only handle if there's an actual error
                error?.let { handlePlaybackError(it) }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Clear error message when playback recovers successfully
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    if (retryCount > 0) {
                        android.util.Log.d("PlaybackManager", "Playback recovered after $retryCount retries")
                        retryCount = 0
                        // Clear error message from UI
                        onPlaybackError?.invoke("", false)
                    }
                }
            }
        })
    }
    
    private fun handlePlaybackError(error: PlaybackException) {
        val errorMessage = getErrorMessage(error)
        android.util.Log.e("PlaybackManager", "Playback error ($retryCount): $errorMessage", error)
        
        // Always retry (silently at first for seamless experience)
        if (retryCount < MAX_RETRIES) {
            retryCount++
            
            // Only notify user after silent retries are exhausted
            if (retryCount > SILENT_RETRIES) {
                val message = "Reconnecting... (${retryCount - SILENT_RETRIES}/${MAX_RETRIES - SILENT_RETRIES})"
                onPlaybackError?.invoke(message, true)
            }
            
            // Retry with quick backoff
            retryJob?.cancel()
            retryJob = CoroutineScope(Dispatchers.Main).launch {
                delay(RETRY_DELAY_MS * minOf(retryCount, 3)) // Cap backoff at 3x
                
                // Full URL refresh - reload media item completely for fresh connection
                currentUrl?.let { url ->
                    android.util.Log.d("PlaybackManager", "Silent retry ${retryCount}/$MAX_RETRIES for: $url")
                    exoPlayer?.let { player ->
                        try {
                            // Stop and clear for fresh start
                            player.stop()
                            player.clearMediaItems()
                            
                            // Rebuild media item with fresh settings
                            val mediaItem = MediaItem.Builder()
                                .setUri(url)
                                .setLiveConfiguration(
                                    MediaItem.LiveConfiguration.Builder()
                                        .setTargetOffsetMs(1500)
                                        .setMinPlaybackSpeed(0.97f)
                                        .setMaxPlaybackSpeed(1.03f)
                                        .build()
                                )
                                .build()
                            
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.playWhenReady = true
                        } catch (e: Exception) {
                            android.util.Log.e("PlaybackManager", "Retry failed", e)
                        }
                    }
                }
            }
        } else {
            // Max retries reached - notify user
            retryCount = 0
            onPlaybackError?.invoke(errorMessage, false)
        }
    }
    
    private fun getErrorMessage(error: PlaybackException): String {
        return when (error.errorCode) {
            // Source errors (stream issues)
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> 
                "Network connection failed. Check your internet."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Connection timed out. Slow network."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                val cause = error.cause
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    when (cause.responseCode) {
                        404 -> "Stream not found (404). Channel may be offline."
                        403 -> "Access denied (403). Stream requires authorization."
                        500, 502, 503 -> "Server error (${cause.responseCode}). Try again later."
                        else -> "HTTP error ${cause.responseCode}"
                    }
                } else {
                    "Stream unavailable"
                }
            }
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Stream not found. Channel may be offline."
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                "Stream error. The channel may be temporarily unavailable."
            
            // Decoder errors
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                "Video decoder error. Try another channel."
            
            // DRM errors
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED ->
                "License error. Protected content."
            
            // Parser errors
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "Invalid stream format."
            
            else -> "Playback error: ${error.message ?: "Unknown error"}"
        }
    }
    
    private fun isRetryableError(error: PlaybackException): Boolean {
        return when (error.errorCode) {
            // Network issues are often transient
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
            
            // Some HTTP errors are retryable (server issues)
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                val cause = error.cause
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    cause.responseCode in listOf(500, 502, 503, 504)
                } else {
                    false
                }
            }
            
            // 404 and other client errors are NOT retryable
            else -> false
        }
    }

    fun playUrl(context: Context, url: String) {
        val player = getPlayer(context)
        
        // If same URL is already playing/buffering, don't reload
        if (currentUrl == url && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            return
        }

        // Reset retry count for new URL
        retryCount = 0
        retryJob?.cancel()
        currentUrl = url
        
        try {
            player.stop()
            player.clearMediaItems()
            
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(1500)
                        .setMinPlaybackSpeed(0.97f)
                        .setMaxPlaybackSpeed(1.03f)
                        .build()
                )
                .build()
            
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            android.util.Log.e("PlaybackManager", "Error setting up playback", e)
            onPlaybackError?.invoke("Failed to load stream: ${e.message}", false)
        }
    }
    
    fun retry(context: Context) {
        currentUrl?.let { url ->
            retryCount = 0
            val tempUrl = url
            currentUrl = null // Force reload
            playUrl(context, tempUrl)
        }
    }

    fun release() {
        retryJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        currentUrl = null
        retryCount = 0
    }

    private fun createOptimizedPlayer(context: Context): ExoPlayer {
        // Optimized buffering for TV boxes - balanced between startup speed and stability
        // Uses AppConfig values for easy tuning
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                AppConfig.Performance.MIN_BUFFER_MS,        // Min buffer before playback
                AppConfig.Performance.MAX_BUFFER_MS,        // Max buffer to maintain
                AppConfig.Performance.BUFFER_FOR_PLAYBACK_MS,   // Buffer for playback to start
                AppConfig.Performance.BUFFER_FOR_REBUFFER_MS    // Buffer after rebuffering
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Prefer hardware decoders on TV boxes (they're optimized for video)
        // Enable fallback for compatibility
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        // Optimized track selection for TV boxes
        val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory()).apply {
            setParameters(
                buildUponParameters()
                    // Allow up to 1080p for TV boxes
                    .setMaxVideoSize(1920, 1080)
                    // Don't force highest bitrate - let adaptive selection work
                    .setForceHighestSupportedBitrate(false)
                    // Allow flexibility for edge cases
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    // Enable adaptive switching between formats
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    // Prefer 5.1 audio if available
                    .setMaxAudioChannelCount(6)
            )
        }

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            // Disable audio focus for TV apps (usually not needed)
            .setHandleAudioBecomingNoisy(false)
            .build().apply {
                // Use SCALE_TO_FIT to avoid cropping on TV
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
}

