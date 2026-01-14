package com.example.androidtviptvapp.player

import android.content.Context
import android.util.AttributeSet
import com.example.androidtviptvapp.data.ChannelPlaybackSource
import com.example.androidtviptvapp.data.MoviePlaybackSource
import com.example.androidtviptvapp.data.PlaybackSource
import com.example.androidtviptvapp.data.TvRepository
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * PlayerView - extends AdaptExoPlayerView with playback source management.
 * Following OnTV-main pattern EXACTLY with robust error recovery and proper job management.
 *
 * KEY FEATURES from ontv-main:
 * 1. loadAndPlayJob - cancels previous load before starting new one (prevents race conditions)
 * 2. playbackSourceFlow - reactive state for UI updates
 * 3. closeStreamPreparePlaybackSource / closePlaybackSource - proper lifecycle
 * 4. Proper error handling with retry and reinit (no MAX_RETRIES limit)
 * 5. Smart retry delays based on lastUserSetAudioTrackTimestamp
 * 6. Content age checking (>2 days jump to live)
 */
class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AdaptExoPlayerView(context, attrs, defStyle) {

    companion object {
        const val DEFAULT_VIDEO_RESIZE_MODE = AspectRatioFrameLayout.RESIZE_MODE_FILL
        const val DELAY_AFTER_ERROR = 2000L  // OnTV-main uses 2 seconds
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

    private val playbackSourceMutableFlow = MutableStateFlow<PlaybackSource?>(null)
    val playbackSourceFlow = playbackSourceMutableFlow.asStateFlow()

    val playbackSource: PlaybackSource?
        get() = playbackSourceFlow.value

    val channelPlaybackSource: ChannelPlaybackSource?
        get() = playbackSource as? ChannelPlaybackSource

    val moviePlaybackSource: MoviePlaybackSource?
        get() = playbackSource as? MoviePlaybackSource

    val isPlayingOrPlanPlayingSomething: Boolean
        get() = playbackSource != null || loadAndPlayJob?.isActive == true

    // =========================================================================
    // Retry State (OnTV-main Pattern - NO MAX_RETRIES!)
    // =========================================================================

    var isRetryAfterError: Throwable? = null
        private set

    // Play ready counter for history tracking (OnTV-main pattern)
    var playReadyCount = 0
        private set

    // =========================================================================
    // Directional Seek (OnTV-main Pattern)
    // =========================================================================

    private var isPauseAfterSeekProcess = false
    private var seekProcessLastPointTimestamp: Long = 0
    private var seekProcessLastPoint: Long = 0
    private var seekProcessSeed: Long = 60

    /**
     * Override pause to handle live channel timeshift (OnTV-main pattern)
     */
    override var pause: Boolean
        get() = if (seekProcessDirection == 0) super.pause else isPauseAfterSeekProcess
        set(v) {
            if (pause == v) return
            if (currentError != null) {
                discardSeekProcess()
                super.pause = v
                if (!v) {
                    handleError(true)
                }
            } else {
                seekProcessDirection = 0
                val channelSource = channelPlaybackSource
                if (channelSource?.isLive == true && !v) {
                    // Resume from pause on live channel - jump to current live position
                    super.pause = true
                    val pos = System.currentTimeMillis() - (liveOffset ?: 20000)
                    ChannelPlaybackSource(
                        channel = channelSource.channel,
                        currentProgram = channelSource.currentProgram,
                        isLive = true,
                        channelList = channelSource.getAllChannels(),
                        startAbsTime = pos
                    ).loadUrlAndPlay(this)
                } else {
                    super.pause = v
                }
            }
        }

    /**
     * Position types for seek operations (OnTV-main pattern)
     */
    sealed class Position
    data class SeekAndDuration(val seek: Long, val duration: Long) : Position() {
        fun fix(): SeekAndDuration {
            if (seek in 0..duration) return this
            val d = duration.coerceAtLeast(0)
            return SeekAndDuration(seek.coerceAtLeast(0).coerceAtMost(d), d)
        }
        val progress: Float
            get() = seek.toFloat() / (if (duration > 0) duration else 1)
    }
    data class AbsPosition(val utcMS: Long) : Position()

    // utc ms for live and livepvr
    val targetSeek: Position
        get() = if (seekProcessDirection != 0) {
            val p = (System.currentTimeMillis() - seekProcessLastPointTimestamp) * seekProcessDirection * seekProcessSeed + seekProcessLastPoint
            channelPlaybackSource?.takeIf { !it.isHaveStartAndDuration }?.let {
                AbsPosition(p)
            } ?: SeekAndDuration(p, duration).fix()
        } else {
            channelPlaybackSource?.takeIf { !it.isHaveStartAndDuration }?.let {
                AbsPosition(System.currentTimeMillis() - (liveOffset ?: it.startLiveOffset ?: 0))
            } ?: SeekAndDuration(seek, duration).fix()
        }

    val userReadableSeek: Position
        get() {
            val p = targetSeek
            if (p is AbsPosition) {
                channelPlaybackSource?.liveProgram?.let { liveProgram ->
                    return SeekAndDuration(p.utcMS - liveProgram.startTimeMS, liveProgram.durationTimeMS)
                }
            }
            return p
        }

    private var seekProcessDirectionField: Int = 0

    fun discardSeekProcess(seek_: Long? = null) {
        if (seekProcessDirectionField == 0) return
        seek_?.let { seek = it }
        super.pause = isPauseAfterSeekProcess
        seekProcessDirectionField = 0
    }

    var seekProcessDirection: Int
        get() = seekProcessDirectionField
        set(v) {
            if (seekProcessDirectionField == v) return
            val targetSeek = targetSeek
            if (v == 0) {
                when (targetSeek) {
                    is AbsPosition -> {
                        val channelSource = channelPlaybackSource!!
                        if (targetSeek.utcMS >= System.currentTimeMillis()) {
                            ChannelPlaybackSource(
                                channel = channelSource.channel,
                                currentProgram = channelSource.currentProgram,
                                isLive = true,
                                channelList = channelSource.getAllChannels()
                            )
                        } else {
                            ChannelPlaybackSource(
                                channel = channelSource.channel,
                                currentProgram = channelSource.currentProgram,
                                isLive = false,
                                channelList = channelSource.getAllChannels(),
                                startAbsTime = channelSource.liveProgram?.takeIf { targetSeek.utcMS <= it.startTimeMS }?.let {
                                    it.startTimeMS
                                } ?: targetSeek.utcMS
                            )
                        }.loadUrlAndPlay(this, isPauseAfterSeekProcess)
                    }
                    is SeekAndDuration -> {
                        seek = targetSeek.seek
                        super.pause = isPauseAfterSeekProcess
                    }
                }
                seekProcessDirectionField = v
            } else {
                if (v > 0) {
                    if (channelPlaybackSource?.isLive == true) return
                } else {
                    (userReadableSeek as? SeekAndDuration)?.seek?.takeIf {
                        it < if (channelPlaybackSource?.isHaveStartAndDuration == false) 15000 else 5000
                    }?.let {
                        // Could show go-to-live dialog here
                        return
                    }
                }
                seekProcessLastPoint = when (targetSeek) {
                    is AbsPosition -> targetSeek.utcMS
                    is SeekAndDuration -> targetSeek.seek
                }
                seekProcessLastPointTimestamp = System.currentTimeMillis()
                isPauseAfterSeekProcess = pause
                seekProcessDirectionField = v
                super.pause = true
            }
        }

    // Callbacks for UI
    var onSourceChanged: ((PlaybackSource?) -> Unit)? = null
    var onPlaybackError: ((String, Boolean) -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null

    // Playback ended handling (OnTV-main pattern)
    private var isPlaybackEndedHandled = false

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
        if (playbackSource != null) {
            onPreStreamClose()
        }
        closeStream()
        playbackSourceMutableFlow.value = ps
        onSourceChanged?.invoke(ps)
        openStream(url, vod, pos, pauseAfter)
    }

    /**
     * Close stream but prepare for new playback source - OnTV-main pattern
     * Used when we want to show loading state before URL is resolved
     */
    fun closeStreamPreparePlaybackSource(ps: PlaybackSource) {
        if (playbackSource != null) {
            onPreStreamClose()
        }
        closeStream()
        playbackSourceMutableFlow.value = ps
        onSourceChanged?.invoke(ps)
    }

    /**
     * Close playback source completely - OnTV-main pattern
     */
    fun closePlaybackSource() {
        if (playbackSource != null) {
            onPreStreamClose()
        }
        loadAndPlayJob?.cancel()
        loadAndPlayJob = null
        closeStream()
        playbackSourceMutableFlow.value = null
        onSourceChanged?.invoke(null)
    }

    /**
     * Called before stream closes - OnTV-main pattern
     * Used for saving state, analytics, history
     */
    private fun onPreStreamClose() {
        addContentToHistory()
        // Save movie position for resume
        if (!isPlaybackEnded) {
            moviePlaybackSource?.let { source ->
                if (seek > 0) {
                    TvRepository.saveMoviePosition(source.movie.id, seek)
                }
            }
        }
    }

    /**
     * Add content to history (OnTV-main pattern)
     */
    fun addContentToHistory() {
        val ps = playbackSource
        when (ps) {
            is ChannelPlaybackSource -> {
                // Add channel to history if played long enough
                if (playReadyCount * 300 > 5000) { // 5 seconds
                    TvRepository.addChannelToHistory(ps.channel)
                }
            }
            is MoviePlaybackSource -> {
                TvRepository.saveMoviePosition(ps.movie.id, seek)
            }
            else -> {}
        }
    }

    // =========================================================================
    // Convenience Play Methods
    // =========================================================================

    /**
     * Play a channel - creates source and plays
     */
    fun playChannel(source: ChannelPlaybackSource) {
        Timber.d("playChannel: ${source.channel.name}")
        source.loadUrlAndPlay(this)
    }

    /**
     * Play a movie - creates source and plays with resume
     */
    fun playMovie(source: MoviePlaybackSource) {
        Timber.d("playMovie: ${source.movie.title}")
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

    // =========================================================================
    // TICK-BASED HEALTH MONITORING (OnTV-main pattern EXACTLY)
    // Called externally every 300ms by SharedPlayerManager
    // =========================================================================

    /**
     * Main tick function - OnTV-main pattern EXACTLY
     */
    fun tick() {
        if (isPlayReady)
            playReadyCount += 1
        else if (!isBuffering)
            playReadyCount = 0

        if (seekProcessDirection != 0) {
            val targetSeek = targetSeek
            when (targetSeek) {
                is AbsPosition -> {
                    val channelSource = channelPlaybackSource!!
                    if (targetSeek.utcMS >= System.currentTimeMillis()) {
                        discardSeekProcess()
                        ChannelPlaybackSource(
                            channel = channelSource.channel,
                            currentProgram = channelSource.currentProgram,
                            isLive = true,
                            channelList = channelSource.getAllChannels()
                        ).loadUrlAndPlay(this, isPauseAfterSeekProcess)
                    } else {
                        channelSource.liveProgram?.takeIf { targetSeek.utcMS <= it.startTimeMS }?.let {
                            discardSeekProcess()
                            ChannelPlaybackSource(
                                channel = channelSource.channel,
                                currentProgram = channelSource.currentProgram,
                                isLive = false,
                                channelList = channelSource.getAllChannels(),
                                startAbsTime = it.startTimeMS
                            ).loadUrlAndPlay(this, isPauseAfterSeekProcess)
                        } ?: run {
                            if (isPlayReady && loadAndPlayJob?.isActive != true) {
                                seekProcessLastPoint = targetSeek.utcMS
                                seekProcessLastPointTimestamp = System.currentTimeMillis()
                                ChannelPlaybackSource(
                                    channel = channelSource.channel,
                                    currentProgram = channelSource.currentProgram,
                                    isLive = false,
                                    channelList = channelSource.getAllChannels(),
                                    startAbsTime = targetSeek.utcMS
                                ).loadUrlAndPlay(this, null, true, false)
                            }
                        }
                    }
                }
                is SeekAndDuration -> {
                    if (targetSeek.seek <= 0) {
                        discardSeekProcess(0)
                    } else if (targetSeek.seek >= targetSeek.duration) {
                        discardSeekProcess(targetSeek.duration)
                        // Could show go-to-live dialog here
                    } else if (isPlayReady && loadAndPlayJob?.isActive != true) {
                        seekProcessLastPoint = targetSeek.seek
                        seekProcessLastPointTimestamp = System.currentTimeMillis()
                        seek = targetSeek.seek
                    }
                }
            }
        }

        // Add to history after sufficient play time (OnTV-main pattern)
        if (playReadyCount * 300 > 5000) { // 5 seconds = ADD_TO_HISTORY_AFTER_TIME
            addContentToHistory()
        }
    }

    // =========================================================================
    // Player State Callbacks (OnTV-main Pattern)
    // =========================================================================

    override fun onPlayerChange() {
        if (isPlayReady) {
            isRetryAfterError = null
            onPlaybackReady?.invoke()
            onPlaybackError?.invoke("", false)
        }

        // Handle playback ended (OnTV-main pattern)
        if (isPlaybackEnded && !isPlaybackEndedHandled) {
            isPlaybackEndedHandled = true
            seekProcessDirection = 0
            if (loadAndPlayJob?.isActive != true) {
                channelPlaybackSource?.let {
                    // Could show go-to-live dialog here
                }
            }
        }
        if (!isPlaybackEnded)
            isPlaybackEndedHandled = false

        tick()
    }

    override fun onError(error: Throwable) {
        super.onError(error)
        discardSeekProcess()
        handleError()
    }

    // =========================================================================
    // Error Handling (OnTV-main Pattern EXACTLY - NO MAX_RETRIES!)
    // =========================================================================

    /**
     * Handle playback error with retry logic - OnTV-main pattern EXACTLY
     * NOTE: No MAX_RETRIES limit - retries indefinitely like OnTV-main
     */
    fun handleError(immediate: Boolean = false) {
        val error = currentError ?: return
        if (pause) return

        // Content age check - if content is >2 days old, jump to live (OnTV-main pattern)
        channelPlaybackSource?.let { src ->
            (src.currentProgram?.startTimeMS ?: src.startAbsTime)?.let { startTime ->
                if (System.currentTimeMillis() - startTime > 2 * 24 * 60 * 60000) {
                    Timber.d("Content too old (>2 days), jumping to live")
                    ChannelPlaybackSource(
                        channel = src.channel,
                        currentProgram = src.currentProgram,
                        isLive = true,
                        channelList = src.getAllChannels()
                    ).loadUrlAndPlay(this)
                    return
                }
            }
        }

        // Immediate restart for BehindLiveWindow, AudioSink errors (OnTV-main pattern)
        if (isShouldRestartNowException(error)) {
            Timber.e("isShouldRestartNowException")
            if (channelPlaybackSource?.isLive == true) {
                restartStream()
            } else {
                channelPlaybackSource?.loadUrlAndPlay(this)
            }
        } else {
            // Retry with smart delay (OnTV-main pattern)
            scope?.launch {
                // Skip delay if user just changed audio track (OnTV-main pattern)
                if (!immediate && System.currentTimeMillis() - lastUserSetAudioTrackTimestamp > DELAY_AFTER_ERROR * 2) {
                    delay(DELAY_AFTER_ERROR)
                }
                if (currentError != null && !pause) {
                    isRetryAfterError = currentError
                    if (!isSourceException(currentError)) {
                        // Non-source error (render/decoder) - full reinit (OnTV-main pattern)
                        GlobalScope.launch(Dispatchers.Main) {
                            reinit()
                            playbackSource?.loadUrlAndPlay(this@PlayerView)
                        }
                    } else {
                        // Source error - just reload
                        playbackSource?.loadUrlAndPlay(this@PlayerView)
                    }
                }
            }
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
        Timber.d("destroy")
        loadAndPlayJob?.cancel()
        loadAndPlayJob = null
        discardSeekProcess()
        playbackSourceMutableFlow.value = null
        playReadyCount = 0
        super.destroy()
    }

    override fun reinit() {
        Timber.d("reinit - preserving playback source")
        super.reinit()
    }
}
