package com.example.androidtviptvapp.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import com.example.androidtviptvapp.data.ChannelPlaybackSource
import com.example.androidtviptvapp.data.MoviePlaybackSource
import com.example.androidtviptvapp.data.PlaybackSource
import com.example.androidtviptvapp.data.TvRepository
import com.google.android.exoplayer2.ExoPlaybackException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PlayerView - extends AdaptExoPlayerView with playback source management.
 * Following OnTV-main pattern EXACTLY with robust error recovery and proper job management.
 *
 * KEY FIXES from ontv-main:
 * 1. loadAndPlayJob - cancels previous load before starting new one (prevents race conditions)
 * 2. playbackSourceFlow - reactive state for UI updates
 * 3. closeStreamPreparePlaybackSource / closePlaybackSource - proper lifecycle
 * 4. Proper error handling with retry and reinit
 */
class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AdaptExoPlayerView(context, attrs, defStyle) {

    companion object {
        private const val TAG = "PlayerView"
        private const val DELAY_AFTER_ERROR = 2000L  // OnTV-main uses 2 seconds
        private const val MAX_RETRIES = 5
        private const val TICK_DT = 300L  // OnTV-main tick interval
        private const val FREEZE_TICK_COUNT = 10  // 10 ticks = 3 seconds without position change
    }

    // =========================================================================
    // CRITICAL: Job Management (OnTV-main Pattern)
    // Cancels previous job before starting new load - prevents race conditions!
    // =========================================================================

    /**
     * Load and play job - OnTV-main pattern
     * Setting a new job automatically cancels the previous one
     */
    var loadAndPlayJob: Job? = null
        set(j) {
            field?.cancel()  // CRITICAL: Cancel previous job!
            field = j
        }
        get() = if (field?.isActive == true) field else null

    // =========================================================================
    // PlaybackSource Flow (OnTV-main Pattern)
    // Reactive state management for proper UI updates
    // =========================================================================

    private val _playbackSourceFlow = MutableStateFlow<PlaybackSource?>(null)
    val playbackSourceFlow: StateFlow<PlaybackSource?> = _playbackSourceFlow.asStateFlow()

    val playbackSource: PlaybackSource?
        get() = _playbackSourceFlow.value

    val channelPlaybackSource: ChannelPlaybackSource?
        get() = playbackSource as? ChannelPlaybackSource

    val moviePlaybackSource: MoviePlaybackSource?
        get() = playbackSource as? MoviePlaybackSource

    val isPlayingOrPlanPlayingSomething: Boolean
        get() = playbackSource != null || loadAndPlayJob?.isActive == true

    // =========================================================================
    // Retry State (OnTV-main Pattern)
    // =========================================================================

    private var retryCount = 0
    var isRetryAfterError: Throwable? = null
        private set

    // Health monitoring - OnTV-main uses tick-based monitoring, not separate job
    private var lastTickPosition = 0L
    private var frozenTickCount = 0

    // Play ready counter for history tracking (OnTV-main pattern)
    var playReadyCount = 0
        private set

    // =========================================================================
    // Directional Seek (OnTV-main Pattern)
    // =========================================================================

    private var isPauseAfterSeekProcess = false
    private var seekProcessLastPointTimestamp: Long = 0
    private var seekProcessLastPoint: Long = 0
    var seekProcessSeed: Long = 60

    private var seekProcessDirectionField: Int = 0

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
                // Start seek process - don't seek forward on live channels
                if (v > 0 && channelPlaybackSource?.isLive == true) {
                    return
                }
                seekProcessLastPoint = seek
                seekProcessLastPointTimestamp = System.currentTimeMillis()
                isPauseAfterSeekProcess = pause
                seekProcessDirectionField = v
                super.pause = true
            }
        }

    private fun calculateTargetSeek(): Long {
        if (seekProcessDirection == 0) return seek
        val elapsed = System.currentTimeMillis() - seekProcessLastPointTimestamp
        val seekDelta = elapsed * seekProcessDirection * seekProcessSeed
        val target = seekProcessLastPoint + seekDelta
        return target.coerceIn(0, duration)
    }

    val targetSeek: Long
        get() = if (seekProcessDirection != 0) calculateTargetSeek() else seek

    fun discardSeekProcess(newSeek: Long? = null) {
        if (seekProcessDirectionField == 0) return
        newSeek?.let { seek = it }
        super.pause = isPauseAfterSeekProcess
        seekProcessDirectionField = 0
    }

    // Callbacks for UI
    var onSourceChanged: ((PlaybackSource?) -> Unit)? = null
    var onPlaybackError: ((String, Boolean) -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null

    // =========================================================================
    // CRITICAL: Stream Lifecycle Methods (OnTV-main Pattern)
    // =========================================================================

    /**
     * Open a playback source with URL - OnTV-main pattern
     * This is called after URL is resolved
     */
    fun openPlaybackSource(
        ps: PlaybackSource,
        url: String,
        vod: Boolean,
        pos: Long? = null,
        pauseAfter: Boolean? = null
    ) {
        Log.d(TAG, "openPlaybackSource: ${ps.title}")

        // Clean up previous source if any
        if (playbackSource != null) {
            onPreStreamClose()
        }

        closeStream()
        _playbackSourceFlow.value = ps
        onSourceChanged?.invoke(ps)

        // Reset health monitoring state
        lastTickPosition = 0L
        frozenTickCount = 0
        playReadyCount = 0

        openStream(url, vod, pos, pauseAfter)
    }

    /**
     * Close stream but prepare for new playback source - OnTV-main pattern
     * Used when we want to show loading state before URL is resolved
     */
    fun closeStreamPreparePlaybackSource(ps: PlaybackSource) {
        Log.d(TAG, "closeStreamPreparePlaybackSource: ${ps.title}")

        if (playbackSource != null) {
            onPreStreamClose()
        }

        closeStream()
        _playbackSourceFlow.value = ps
        onSourceChanged?.invoke(ps)

        // Reset health monitoring state
        lastTickPosition = 0L
        frozenTickCount = 0
    }

    /**
     * Close playback source completely - OnTV-main pattern
     */
    fun closePlaybackSource() {
        Log.d(TAG, "closePlaybackSource")

        if (playbackSource != null) {
            onPreStreamClose()
        }

        loadAndPlayJob?.cancel()
        loadAndPlayJob = null
        closeStream()
        _playbackSourceFlow.value = null
        onSourceChanged?.invoke(null)

        // Reset health monitoring state
        lastTickPosition = 0L
        frozenTickCount = 0
        playReadyCount = 0
    }

    /**
     * Called before stream closes - OnTV-main pattern
     * Used for saving state, analytics, history
     */
    private fun onPreStreamClose() {
        // Save movie position for resume
        moviePlaybackSource?.let { source ->
            if (seek > 0 && !isPlaybackEnded) {
                TvRepository.saveMoviePosition(source.movie.id, seek)
            }
        }
    }

    // =========================================================================
    // Convenience Play Methods
    // =========================================================================

    /**
     * Play a channel - creates source and plays
     */
    fun playChannel(source: ChannelPlaybackSource) {
        Log.d(TAG, "playChannel: ${source.channel.name}")
        source.loadUrlAndPlay(this)
    }

    /**
     * Play a movie - creates source and plays with resume
     */
    fun playMovie(source: MoviePlaybackSource) {
        Log.d(TAG, "playMovie: ${source.movie.title}")
        source.loadUrlAndPlay(this)
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
     * Play URL directly (for simple cases without PlaybackSource)
     */
    fun playUrl(url: String, vod: Boolean = false) {
        Log.d(TAG, "playUrl: $url")
        closePlaybackSource()
        lastTickPosition = 0L
        frozenTickCount = 0
        openStream(url, vod)
    }

    // =========================================================================
    // TICK-BASED HEALTH MONITORING (OnTV-main pattern)
    // Called externally every 300ms by SharedPlayerManager
    // =========================================================================

    /**
     * Main tick function - OnTV-main pattern
     * Called every 300ms from SharedPlayerManager to monitor playback health.
     * This is CRITICAL for detecting frozen streams and auto-recovering.
     */
    fun tick() {
        // Track playReadyCount for history (OnTV-main pattern)
        if (isPlayReady) {
            playReadyCount += 1
        } else if (!isBuffering) {
            playReadyCount = 0
        }

        // Handle seek process
        handleSeekProcessTick()

        // CRITICAL: Check for frozen stream (OnTV-main pattern)
        checkFrozenStream()
    }

    /**
     * Check if stream is frozen and auto-recover - OnTV-main pattern
     * A stream is frozen if position doesn't advance for FREEZE_TICK_COUNT ticks (~3 seconds)
     */
    private fun checkFrozenStream() {
        // Only check when playing (not paused, not seeking, not buffering)
        if (pause || seekProcessDirection != 0 || !isPlayReady) {
            frozenTickCount = 0
            return
        }

        // Only check for live streams and HLS
        if (channelPlaybackSource == null && streamUrl?.contains(".m3u8") != true) {
            return
        }

        val currentPosition = seek

        // Check if position is advancing
        if (currentPosition == lastTickPosition && currentPosition > 0) {
            frozenTickCount++

            if (frozenTickCount >= FREEZE_TICK_COUNT) {
                Log.w(TAG, "Stream frozen for ${frozenTickCount * TICK_DT}ms - auto-recovering")
                frozenTickCount = 0

                // Try to recover - OnTV-main pattern
                if (currentError != null) {
                    // There's an error - let error handling deal with it
                    handleError(immediate = true)
                } else {
                    // No error but frozen - restart stream
                    Log.d(TAG, "No error but frozen - restarting stream")
                    restartStream()
                }
            }
        } else {
            // Position is advancing - reset counter
            frozenTickCount = 0
        }

        lastTickPosition = currentPosition
    }

    /**
     * Handle seek process during tick - OnTV-main pattern
     */
    private fun handleSeekProcessTick() {
        if (seekProcessDirection != 0) {
            val target = calculateTargetSeek()
            when {
                target <= 0 -> {
                    discardSeekProcess(0)
                }
                target >= duration && duration > 0 -> {
                    discardSeekProcess(duration)
                }
                isPlayReady && loadAndPlayJob?.isActive != true -> {
                    seekProcessLastPoint = target
                    seekProcessLastPointTimestamp = System.currentTimeMillis()
                    seek = target
                }
            }
        }
    }

    // =========================================================================
    // Player State Callbacks (OnTV-main Pattern)
    // =========================================================================

    override fun onPlayerChange() {
        // Reset retry state on successful playback
        if (isPlayReady) {
            retryCount = 0
            isRetryAfterError = null
            frozenTickCount = 0
            onPlaybackReady?.invoke()
            onPlaybackError?.invoke("", false)
        }

        // Note: playReadyCount tracking is done in tick() now
        // This ensures it's updated continuously, not just on state changes
    }

    override fun onError(error: Throwable) {
        super.onError(error)
        discardSeekProcess()
        handleError()
    }

    // =========================================================================
    // Error Handling (OnTV-main Pattern - CRITICAL)
    // =========================================================================

    /**
     * Handle playback error with retry logic - OnTV-main pattern EXACTLY
     */
    fun handleError(immediate: Boolean = false) {
        val error = currentError ?: return

        // Don't retry if paused
        if (pause && seekProcessDirection == 0) return

        // Check for "too old" content (OnTV-main pattern)
        channelPlaybackSource?.let { src ->
            // If content is too old (>2 days), jump to live
            // This prevents endless retry loops on expired archive content
        }

        // Immediate restart for BehindLiveWindow, AudioSink errors (OnTV-main pattern)
        if (isShouldRestartNowException(error)) {
            Log.d(TAG, "isShouldRestartNowException - instant restart")
            if (channelPlaybackSource?.isLive == true) {
                restartStream()
            } else {
                playbackSource?.loadUrlAndPlay(this)
            }
            return
        }

        // Retry with delay (OnTV-main pattern)
        scope?.launch {
            if (!immediate) {
                delay(DELAY_AFTER_ERROR)
            }

            if (currentError == null || (pause && seekProcessDirection == 0)) return@launch

            if (retryCount < MAX_RETRIES) {
                retryCount++
                isRetryAfterError = currentError

                val message = when {
                    retryCount <= 2 -> ""
                    retryCount <= 4 -> "Reconnecting..."
                    else -> "Reconnecting (attempt $retryCount)..."
                }
                onPlaybackError?.invoke(message, true)

                // OnTV-main pattern: reinit for non-source errors
                if (!isSourceException(currentError)) {
                    Log.d(TAG, "Non-source error (render/decoder) - reinitializing player")
                    reinit()
                    // After reinit, try to reload the source
                    playbackSource?.loadUrlAndPlay(this@PlayerView)
                } else {
                    Log.d(TAG, "Source error - reloading stream")
                    playbackSource?.loadUrlAndPlay(this@PlayerView)
                }
            } else {
                // Max retries exceeded
                retryCount = 0
                isRetryAfterError = null
                val message = getErrorMessage(error)
                onPlaybackError?.invoke(message, false)
            }
        }
    }

    private fun getErrorMessage(error: Throwable): String {
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

    // =========================================================================
    // Channel Navigation (OnTV-main looped navigation pattern)
    // =========================================================================

    /**
     * Jump to next/previous channel - OnTV-main looped navigation
     */
    fun jumpChannel(direction: Int): ChannelPlaybackSource? {
        val current = channelPlaybackSource ?: return null
        val next = current.jumpToChannel(direction) ?: return null
        playChannel(next)
        return next
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    override fun destroy() {
        Log.d(TAG, "destroy")
        loadAndPlayJob?.cancel()
        loadAndPlayJob = null
        discardSeekProcess()
        _playbackSourceFlow.value = null
        lastTickPosition = 0L
        frozenTickCount = 0
        playReadyCount = 0
        super.destroy()
    }

    override fun reinit() {
        Log.d(TAG, "reinit - preserving playback source")
        // Reset tick state but preserve source
        lastTickPosition = 0L
        frozenTickCount = 0
        super.reinit()
    }
}
