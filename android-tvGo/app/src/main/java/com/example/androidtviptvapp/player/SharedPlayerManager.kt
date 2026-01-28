package com.example.androidtviptvapp.player

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Rect
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
 * SharedPlayerManager - TRUE SINGLETON player with GLOBAL OVERLAY support.
 *
 * KEY INSIGHT: Instead of moving the player between containers (which causes
 * MediaCodec errors), we keep ONE player in a FIXED root container and just
 * change its size/position for preview vs fullscreen mode.
 *
 * This prevents:
 * - Channel reload when going fullscreen
 * - Channel reload when returning from fullscreen
 * - MediaCodec errors from view parent changes
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

    // ==========================================================================
    // GLOBAL OVERLAY MODE - Player stays in one place, just changes size
    // ==========================================================================

    // Preview bounds (in dp) - where the preview area is on screen
    data class OverlayBounds(
        val x: Float = 0f,      // Left position in dp
        val y: Float = 0f,      // Top position in dp
        val width: Float = 0f,  // Width in dp
        val height: Float = 0f, // Height in dp
        val isVisible: Boolean = true
    )

    private val _previewBounds = MutableStateFlow(OverlayBounds())
    val previewBounds: StateFlow<OverlayBounds> = _previewBounds.asStateFlow()

    // Track if overlay is visible (when on channels screen or player screen)
    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    // Global container - set once in MainActivity, never changed
    private var globalContainer: FrameLayout? = null

    /**
     * Set the preview area bounds (called from ChannelsScreen when layout changes)
     */
    fun setPreviewBounds(x: Float, y: Float, width: Float, height: Float) {
        _previewBounds.value = OverlayBounds(x, y, width, height, true)
        Timber.d("Preview bounds set: x=$x, y=$y, w=$width, h=$height")
    }

    /**
     * Show/hide the player overlay
     */
    fun setOverlayVisible(visible: Boolean) {
        _isOverlayVisible.value = visible
        Timber.d("Overlay visible: $visible")
    }

    /**
     * Attach player to global container (called ONCE from MainActivity)
     * The player NEVER moves from this container!
     */
    fun attachToGlobalContainer(container: FrameLayout, context: Context) {
        val player = getOrCreatePlayer(context)

        // Only attach if not already attached to this container
        if (globalContainer != container || player.parent != container) {
            // Remove from any previous parent
            (player.parent as? ViewGroup)?.removeView(player)

            Timber.d("Attaching player to GLOBAL container (one-time)")
            container.addView(player, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            globalContainer = container
        }
    }

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
     * When moving between containers, we reload the stream to avoid MediaCodec errors.
     * The stream URL is preserved so it reloads the same content seamlessly.
     */
    fun attachToContainer(container: FrameLayout, context: Context) {
        val player = getOrCreatePlayer(context)
        val isMovingContainers = currentContainer != null && currentContainer != container && player.parent != null

        // Track if we need to reload after move
        val needsReload = isMovingContainers && player.streamUrl != null

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

        // Reload stream after move to reset MediaCodec and avoid decoder errors
        if (needsReload) {
            Timber.d("Reloading stream after container move to reset codecs")
            player.reloadStream()
        }
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
