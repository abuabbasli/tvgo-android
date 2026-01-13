package com.example.androidtviptvapp.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import com.example.androidtviptvapp.data.ChannelPlaybackSource
import com.example.androidtviptvapp.data.MoviePlaybackSource
import com.example.androidtviptvapp.data.PlaybackSource
import com.google.android.exoplayer2.ExoPlaybackException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PlayerView - extends AdaptExoPlayerView with playback source management.
 * Following OnTV-main pattern with robust error recovery and directional seek.
 */
class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AdaptExoPlayerView(context, attrs, defStyle) {

    companion object {
        private const val TAG = "PlayerView"
        private const val ERROR_RETRY_DELAY = 2000L
        private const val MAX_RETRIES = 5
        private const val HEALTH_CHECK_INTERVAL = 5000L
        private const val FREEZE_THRESHOLD = 3000L
    }

    var playbackSource: PlaybackSource? = null
        private set

    private var retryCount = 0
    private var healthCheckJob: Job? = null
    private var lastPosition = 0L
    private var lastPositionCheckTime = 0L
    private var frozenCount = 0

    // Retry state (OnTV-main pattern)
    var isRetryingAfterError: Throwable? = null
        private set

    // =========================================================================
    // Directional Seek (OnTV-main pattern)
    // =========================================================================
    
    private var isPauseAfterSeekProcess = false
    private var seekProcessLastPointTimestamp: Long = 0
    private var seekProcessLastPoint: Long = 0
    var seekProcessSeed: Long = 60 // Speed multiplier
    
    private var seekProcessDirectionField: Int = 0
    
    /**
     * Seek direction: -1 = backward, 0 = none/idle, 1 = forward
     */
    var seekProcessDirection: Int
        get() = seekProcessDirectionField
        set(v) {
            if (seekProcessDirectionField == v) return
            
            if (v == 0) {
                // Complete seek process
                val targetSeek = calculateTargetSeek()
                seek = targetSeek
                super.pause = isPauseAfterSeekProcess
                seekProcessDirectionField = 0
            } else {
                // Start seek process
                if (v > 0 && playbackSource is ChannelPlaybackSource) {
                    // Don't seek forward on live channels
                    return
                }
                seekProcessLastPoint = seek
                seekProcessLastPointTimestamp = System.currentTimeMillis()
                isPauseAfterSeekProcess = pause
                seekProcessDirectionField = v
                super.pause = true
            }
        }
    
    /**
     * Calculate target seek position during directional seek
     */
    private fun calculateTargetSeek(): Long {
        if (seekProcessDirection == 0) return seek
        
        val elapsed = System.currentTimeMillis() - seekProcessLastPointTimestamp
        val seekDelta = elapsed * seekProcessDirection * seekProcessSeed
        val target = seekProcessLastPoint + seekDelta
        
        return target.coerceIn(0, duration)
    }
    
    /**
     * Current target seek position (accounts for ongoing seek process)
     */
    val targetSeek: Long
        get() = if (seekProcessDirection != 0) calculateTargetSeek() else seek
    
    /**
     * Discard ongoing seek process
     */
    fun discardSeekProcess(newSeek: Long? = null) {
        if (seekProcessDirectionField == 0) return
        newSeek?.let { seek = it }
        super.pause = isPauseAfterSeekProcess
        seekProcessDirectionField = 0
    }

    // Callbacks
    var onSourceChanged: ((PlaybackSource?) -> Unit)? = null
    var onPlaybackError: ((String, Boolean) -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null

    /**
     * Open a playback source
     */
    fun openPlaybackSource(
        source: PlaybackSource,
        url: String,
        vod: Boolean,
        startPosition: Long? = null,
        pauseAfter: Boolean? = null
    ) {
        Log.d(TAG, "openPlaybackSource: ${source.title}")
        playbackSource = source
        retryCount = 0
        isRetryingAfterError = null
        discardSeekProcess()
        onSourceChanged?.invoke(source)
        openStream(url, vod, startPosition, pauseAfter)
        startHealthMonitoring()
    }

    /**
     * Play a channel
     */
    fun playChannel(source: ChannelPlaybackSource) {
        Log.d(TAG, "playChannel: ${source.channel.name}")
        openPlaybackSource(source, source.streamUrl, vod = false)
    }

    /**
     * Play a movie
     */
    fun playMovie(source: MoviePlaybackSource) {
        Log.d(TAG, "playMovie: ${source.movie.title}")
        openPlaybackSource(
            source,
            source.streamUrl,
            vod = true,
            startPosition = if (source.resumePosition > 0) source.resumePosition else null
        )
    }

    /**
     * Play any playback source
     */
    fun play(source: PlaybackSource) {
        when (source) {
            is ChannelPlaybackSource -> playChannel(source)
            is MoviePlaybackSource -> playMovie(source)
        }
    }

    /**
     * Play URL directly (for simple cases)
     */
    fun playUrl(url: String, vod: Boolean = false) {
        Log.d(TAG, "playUrl: $url")
        playbackSource = null
        retryCount = 0
        isRetryingAfterError = null
        discardSeekProcess()
        onSourceChanged?.invoke(null)
        openStream(url, vod)
        startHealthMonitoring()
    }

    /**
     * Start playback health monitoring - detects frozen streams
     */
    private fun startHealthMonitoring() {
        healthCheckJob?.cancel()
        lastPosition = 0L
        lastPositionCheckTime = System.currentTimeMillis()
        frozenCount = 0

        healthCheckJob = scope?.launch {
            while (true) {
                delay(HEALTH_CHECK_INTERVAL)
                checkPlaybackHealth()
            }
        }
    }

    /**
     * Check if playback is healthy or frozen
     */
    private fun checkPlaybackHealth() {
        if (pause || !isPlayReady || seekProcessDirection != 0) return

        val currentPosition = seek
        val currentTime = System.currentTimeMillis()

        // For live streams, check if position is advancing
        if (playbackSource is ChannelPlaybackSource || streamUrl?.contains(".m3u8") == true) {
            if (currentPosition == lastPosition && currentTime - lastPositionCheckTime > FREEZE_THRESHOLD) {
                frozenCount++
                Log.w(TAG, "Possible freeze detected (count: $frozenCount)")
                
                if (frozenCount >= 2) {
                    Log.w(TAG, "Stream appears frozen - auto-recovering")
                    frozenCount = 0
                    restartStream()
                }
            } else {
                frozenCount = 0
            }
        }

        lastPosition = currentPosition
        lastPositionCheckTime = currentTime
    }

    override fun onError(error: Throwable) {
        super.onError(error)
        discardSeekProcess()
        handleError()
    }

    override fun onPlayerChange() {
        super.onPlayerChange()
        if (isPlayReady) {
            retryCount = 0
            isRetryingAfterError = null
            frozenCount = 0
            onPlaybackReady?.invoke()
            onPlaybackError?.invoke("", false)
        }
    }

    /**
     * Handle playback error with retry logic (OnTV-main pattern)
     */
    private fun handleError() {
        val error = currentError ?: return

        // Don't retry if paused
        if (pause && seekProcessDirection == 0) return

        // Immediate restart for BehindLiveWindow, AudioSink errors
        if (isShouldRestartNowException(error)) {
            Log.d(TAG, "BehindLiveWindow/AudioSink - instant restart")
            restartStream()
            return
        }

        // Retry with backoff
        if (retryCount < MAX_RETRIES) {
            retryCount++
            isRetryingAfterError = error
            
            val message = when {
                retryCount == 1 -> ""
                retryCount <= 3 -> "Reconnecting..."
                else -> "Reconnecting (attempt $retryCount)..."
            }
            onPlaybackError?.invoke(message, true)

            scope?.launch {
                val delayMs = if (retryCount == 1) 500L else ERROR_RETRY_DELAY * (retryCount - 1)
                delay(delayMs)

                if (currentError == null || (pause && seekProcessDirection == 0)) return@launch

                // Reinit for decoder/renderer errors (OnTV-main pattern with ExoPlaybackException.type)
                if (isRenderError(error)) {
                    Log.d(TAG, "Render error (TYPE_RENDERER) - reinitializing player")
                    reinit()
                    startHealthMonitoring()
                    return@launch
                }

                // For source errors, just reload the stream
                if (isSourceException(error)) {
                    Log.d(TAG, "Source error (TYPE_SOURCE) - reloading stream")
                }

                // Reload stream
                val url = streamUrl
                if (url != null) {
                    Log.d(TAG, "Retry attempt $retryCount for $url")
                    val isVod = playbackSource is MoviePlaybackSource
                    openStream(url, isVod)
                }
            }
        } else {
            retryCount = 0
            isRetryingAfterError = null
            val message = getErrorMessage(error)
            onPlaybackError?.invoke(message, false)
        }
    }

    private fun getErrorMessage(error: Throwable): String {
        // Use ExoPlaybackException.type for better messages
        val exo = error as? ExoPlaybackException
        if (exo != null) {
            return when (exo.type) {
                ExoPlaybackException.TYPE_SOURCE -> "Stream unavailable"
                ExoPlaybackException.TYPE_RENDERER -> "Decoder error"
                ExoPlaybackException.TYPE_UNEXPECTED -> "Unexpected error"
                ExoPlaybackException.TYPE_REMOTE -> "Server error"
                else -> "Playback error"
            }
        }
        
        return when {
            error.message?.contains("network", ignoreCase = true) == true -> "Network error"
            error.message?.contains("timeout", ignoreCase = true) == true -> "Connection timeout"
            error.message?.contains("decoder", ignoreCase = true) == true -> "Decoder error"
            error.message?.contains("source", ignoreCase = true) == true -> "Stream unavailable"
            else -> "Playback error"
        }
    }

    /**
     * Jump to next/previous channel
     */
    fun jumpChannel(direction: Int): ChannelPlaybackSource? {
        val current = playbackSource as? ChannelPlaybackSource ?: return null
        val next = current.jumpToChannel(direction) ?: return null
        playChannel(next)
        return next
    }

    override fun destroy() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        discardSeekProcess()
        super.destroy()
    }
}
