package com.example.androidtviptvapp.player

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SharedPlayerManager - TRUE SINGLETON player that persists across screens.
 *
 * KEY INSIGHT: Instead of creating/destroying players when navigating between
 * preview and fullscreen, we keep ONE player and just move it between containers.
 *
 * This prevents:
 * - Channel reload when going fullscreen
 * - Channel reload when returning from fullscreen
 * - Memory issues from multiple players
 * - Buffering/loading delays on navigation
 */
object SharedPlayerManager {

    const val TICK_DT = 300L
    private const val MEMORY_CHECK_INTERVAL = 200
    private var tickJob: Job? = null
    private var tickCount = 0

    /**
     * Player state for UI observation
     */
    data class PlayerState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val isPlayReady: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val error: String? = null,
        val isRetrying: Boolean = false,
        val channelId: String? = null,
        val channelName: String? = null
    )

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Current channel info
    private val _currentChannelId = MutableStateFlow<String?>(null)
    val currentChannelId: StateFlow<String?> = _currentChannelId.asStateFlow()

    private val _currentChannelName = MutableStateFlow<String?>(null)
    val currentChannelName: StateFlow<String?> = _currentChannelName.asStateFlow()

    private val _currentStreamUrl = MutableStateFlow<String?>(null)
    val currentStreamUrl: StateFlow<String?> = _currentStreamUrl.asStateFlow()

    // THE singleton player - created once, never destroyed until app exit
    private var singletonPlayer: PlayerView? = null
    private var playerContext: Context? = null

    // Current container the player is attached to
    private var currentContainer: FrameLayout? = null

    // Track if we're in fullscreen mode
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    // Track returning from fullscreen to prevent scroll/reload
    private var _returningFromFullscreen = false
    val returningFromFullscreen: Boolean get() = _returningFromFullscreen

    // Track creation for debugging
    private var creationCount = 0

    /**
     * Get or create the singleton player.
     * Player is created ONCE and reused forever.
     */
    fun getOrCreatePlayer(context: Context): PlayerView {
        val appContext = context.applicationContext

        singletonPlayer?.let { existing ->
            if (existing.isInitialized) {
                Timber.d("Reusing singleton player (creation #$creationCount)")
                return existing
            }
            Timber.w("Player exists but not initialized, recreating...")
            singletonPlayer = null
        }

        creationCount++
        Timber.d("Creating singleton player #$creationCount")
        logMemoryUsage()

        val newPlayer = PlayerView(appContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            resizeMode = AdaptExoPlayerView.RESIZE_MODE_FIT
            init()
        }
        singletonPlayer = newPlayer
        playerContext = appContext

        return newPlayer
    }

    /**
     * Attach the singleton player to a container.
     * Removes from previous container first - NO RELOAD happens!
     */
    fun attachToContainer(container: FrameLayout, context: Context) {
        val player = getOrCreatePlayer(context)

        // Remove from current container if attached elsewhere
        if (currentContainer != null && currentContainer != container) {
            Timber.d("Moving player from old container to new container")
            (player.parent as? ViewGroup)?.removeView(player)
        }

        // Add to new container if not already there
        if (player.parent != container) {
            // Remove from any parent first
            (player.parent as? ViewGroup)?.removeView(player)

            Timber.d("Attaching player to container")
            container.addView(player, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        currentContainer = container
    }

    /**
     * Detach player from current container (but DON'T destroy it!)
     * Player keeps playing in background.
     */
    fun detachFromContainer() {
        singletonPlayer?.let { player ->
            Timber.d("Detaching player from container (keeps playing)")
            (player.parent as? ViewGroup)?.removeView(player)
        }
        currentContainer = null
    }

    /**
     * Play a channel URL. If same channel, just continues playing - NO RELOAD!
     */
    fun playChannel(channelId: String, channelName: String, streamUrl: String) {
        val player = singletonPlayer ?: return

        // If same channel is already playing, don't reload!
        if (_currentChannelId.value == channelId && player.streamUrl == streamUrl) {
            Timber.d("Same channel already playing, NO RELOAD: $channelName")
            player.pause = false
            return
        }

        Timber.d("Playing NEW channel: $channelName ($channelId)")
        _currentChannelId.value = channelId
        _currentChannelName.value = channelName
        _currentStreamUrl.value = streamUrl

        player.playUrl(streamUrl, vod = false)
    }

    /**
     * Check if a specific channel is currently playing
     */
    fun isChannelPlaying(channelId: String): Boolean {
        return _currentChannelId.value == channelId
    }

    /**
     * Check if we have any channel playing
     */
    fun hasActivePlayback(): Boolean {
        return _currentChannelId.value != null && singletonPlayer?.isInitialized == true
    }

    /**
     * Pause playback
     */
    fun pause() {
        singletonPlayer?.pause = true
    }

    /**
     * Resume playback
     */
    fun resume() {
        singletonPlayer?.pause = false
    }

    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        singletonPlayer?.let { player ->
            player.pause = !player.pause
        }
    }

    /**
     * Get the player for direct access (e.g., for channel switching)
     */
    fun getPlayer(): PlayerView? = singletonPlayer

    /**
     * Set fullscreen mode
     */
    fun setFullscreen(fullscreen: Boolean) {
        _isFullscreen.value = fullscreen
        Timber.d("Fullscreen mode: $fullscreen")
    }

    /**
     * Set current channel info (for tracking)
     */
    fun setCurrentChannel(channelId: String, streamUrl: String) {
        _currentChannelId.value = channelId
        _currentStreamUrl.value = streamUrl
    }

    /**
     * Start tick-based state updates
     */
    fun startTicking(scope: CoroutineScope) {
        if (tickJob?.isActive == true) return

        Timber.d("Starting tick updates (${TICK_DT}ms)")
        tickCount = 0
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_DT)
                tick()
            }
        }
    }

    /**
     * Stop tick updates
     */
    fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    /**
     * Internal tick - updates state and calls player.tick()
     */
    private fun tick() {
        tickCount++

        singletonPlayer?.let { player ->
            player.tick()

            _playerState.value = PlayerState(
                isPlaying = !player.pause && player.isPlayReady,
                isBuffering = player.isBuffering,
                isPlayReady = player.isPlayReady,
                currentPosition = player.seek,
                duration = player.duration,
                error = player.currentError?.message,
                isRetrying = player.isRetryAfterError != null,
                channelId = _currentChannelId.value,
                channelName = _currentChannelName.value
            )
        }

        // Periodic memory check
        if (tickCount % MEMORY_CHECK_INTERVAL == 0) {
            com.example.androidtviptvapp.data.TvRepository.checkMemoryAndCleanup()
        }
    }

    /**
     * Clear current channel (stop playback tracking)
     */
    fun clearCurrentChannel() {
        _currentChannelId.value = null
        _currentChannelName.value = null
        _currentStreamUrl.value = null
    }

    /**
     * Mark returning from fullscreen - prevents scroll/reload in ChannelsScreen
     */
    fun markReturningFromFullscreen() {
        _returningFromFullscreen = true
        Timber.d("Marked returning from fullscreen")
    }

    /**
     * Clear the returning from fullscreen flag
     */
    fun clearReturningFromFullscreen() {
        _returningFromFullscreen = false
        Timber.d("Cleared returning from fullscreen flag")
    }

    /**
     * Should skip debounce - returns true when returning from fullscreen
     */
    fun shouldSkipDebounce(): Boolean = _returningFromFullscreen

    /**
     * Release everything - only call on app exit
     */
    fun releaseCompletely() {
        Timber.w("RELEASING COMPLETELY - destroying singleton player")
        logMemoryUsage()

        stopTicking()

        singletonPlayer?.loadAndPlayJob?.cancel()
        singletonPlayer?.loadAndPlayJob = null

        detachFromContainer()

        singletonPlayer?.destroy()
        singletonPlayer = null
        playerContext = null

        clearCurrentChannel()
        _isFullscreen.value = false
        _playerState.value = PlayerState()

        System.gc()
    }

    /**
     * Log memory usage
     */
    private fun logMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMem = runtime.maxMemory() / 1024 / 1024
        Timber.d("Memory: used=${usedMem}MB, max=${maxMem}MB")
    }

    /**
     * Check if player is valid
     */
    fun isPlayerValid(): Boolean {
        return singletonPlayer?.isInitialized == true
    }
}
