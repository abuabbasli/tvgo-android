package com.example.androidtviptvapp.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SharedPlayerManager - TRUE SINGLETON to manage ONE shared player across entire app.
 * 
 * CRITICAL: TV boxes have very limited RAM (1-2GB). Creating multiple ExoPlayers
 * WILL crash the device. This singleton ensures only ONE player exists at any time.
 * 
 * Key Features:
 * - Single player instance that persists across navigation
 * - Tracks current channel so we can skip reload on return
 * - Provides state flow for UI observation
 * - Memory-efficient: reuses same player, never creates new ones
 */
object SharedPlayerManager {
    private const val TAG = "SharedPlayerManager"
    
    // =========================================================================
    // TICK-BASED STATE UPDATES (OnTV-main Pattern)
    // Refreshes player state every 300ms for smooth UI sync
    // =========================================================================
    
    private const val TICK_INTERVAL_MS = 300L
    private const val MEMORY_CHECK_INTERVAL = 200 // Check memory every 60 seconds (200 ticks)
    private var tickJob: Job? = null
    private var tickCount = 0
    
    /**
     * Player state for UI observation - updated by tick()
     * Enhanced with OnTV-main style error tracking
     */
    data class PlayerState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val isPlayReady: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val error: String? = null,
        val isRetrying: Boolean = false,
        val channelId: String? = null
    )
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    // Current channel being played
    private val _currentChannelId = MutableStateFlow<String?>(null)
    val currentChannelId: StateFlow<String?> = _currentChannelId.asStateFlow()
    
    // Current stream URL
    private val _currentStreamUrl = MutableStateFlow<String?>(null)
    val currentStreamUrl: StateFlow<String?> = _currentStreamUrl.asStateFlow()
    
    // THE singleton player - only one ever exists!
    private var singletonPlayer: PlayerView? = null
    private var playerContext: Context? = null
    
    // Track if player is attached to a view (prevent double-destroy)
    private var isAttached: Boolean = false
    
    // Last playback position for resume
    private var lastPosition: Long = 0
    
    // Flag to skip debounce when returning from fullscreen
    var skipNextDebounce: Boolean = false
    
    // Track player creation count for debugging
    private var creationCount = 0
    
    /**
     * Start tick-based state updates. Call when player becomes active.
     */
    fun startTicking(scope: CoroutineScope) {
        if (tickJob?.isActive == true) return // Already ticking
        
        Log.d(TAG, "Starting tick updates (${TICK_INTERVAL_MS}ms interval)")
        tickCount = 0
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                tick()
            }
        }
    }
    
    /**
     * Stop tick updates. Call when player view is detached.
     */
    fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
        Log.d(TAG, "Stopped tick updates")
    }
    
    /**
     * Internal tick - OnTV-main pattern EXACTLY
     *
     * CRITICAL: This calls player.tick() which monitors playback health.
     * This is the CORE of the freeze detection and auto-recovery system.
     *
     * Called every 300ms to:
     * 1. Update player state for UI
     * 2. Call player.tick() for health monitoring (CRITICAL!)
     * 3. Check memory periodically
     */
    private fun tick() {
        tickCount++

        singletonPlayer?.let { player ->
            // CRITICAL: Call player tick for health monitoring (OnTV-main pattern)
            // This checks for frozen streams and auto-recovers
            player.tick()

            // Update state flow for UI observation
            _playerState.value = PlayerState(
                isPlaying = !player.pause && player.isPlayReady,
                isBuffering = player.isBuffering,
                isPlayReady = player.isPlayReady,
                currentPosition = player.seek,
                duration = player.duration,
                error = player.currentError?.message,
                isRetrying = player.isRetryAfterError != null,
                channelId = _currentChannelId.value
            )
        }

        // Periodic memory check (every ~60 seconds = 200 ticks at 300ms)
        if (tickCount % MEMORY_CHECK_INTERVAL == 0) {
            com.example.androidtviptvapp.data.TvRepository.checkMemoryAndCleanup()
        }
    }
    
    /**
     * Get the SINGLE shared player instance.
     * If no player exists, creates one. If one exists, returns it.
     * NEVER call this from multiple places expecting different players!
     */
    fun getOrCreatePlayer(context: Context): PlayerView {
        val appContext = context.applicationContext
        
        // Check if we already have a valid player
        singletonPlayer?.let { existing ->
            if (existing.isInitialized) {
                Log.d(TAG, "Reusing existing singleton player (creation count: $creationCount)")
                logMemoryUsage()
                return existing
            } else {
                // Player was destroyed but reference exists, clean up
                Log.w(TAG, "Player reference exists but not initialized, recreating...")
                singletonPlayer = null
            }
        }
        
        // Create new player only if none exists
        creationCount++
        Log.d(TAG, "Creating singleton player #$creationCount")
        logMemoryUsage()
        
        val newPlayer = PlayerView(appContext).apply {
            resizeMode = AdaptExoPlayerView.RESIZE_MODE_FIT
            init()
        }
        singletonPlayer = newPlayer
        playerContext = appContext
        isAttached = false
        
        return newPlayer
    }
    
    /**
     * Mark player as attached to a view
     */
    fun markAttached() {
        isAttached = true
    }
    
    /**
     * Mark player as detached from view (DON'T destroy - just detaching)
     */
    fun markDetached() {
        isAttached = false
    }
    
    /**
     * Set the current channel being played
     */
    fun setCurrentChannel(channelId: String, streamUrl: String) {
        Log.d(TAG, "Setting current channel: $channelId")
        _currentChannelId.value = channelId
        _currentStreamUrl.value = streamUrl
    }
    
    /**
     * Check if a channel is already playing
     */
    fun isChannelPlaying(channelId: String): Boolean {
        return _currentChannelId.value == channelId
    }
    
    /**
     * Save playback position for resume
     */
    fun savePosition() {
        singletonPlayer?.let {
            lastPosition = it.seek
            Log.d(TAG, "Saved position: $lastPosition")
        }
    }
    
    /**
     * Get last saved position
     */
    fun getLastPosition(): Long = lastPosition
    
    /**
     * Clear the current channel (when stopping playback)
     */
    fun clearCurrentChannel() {
        _currentChannelId.value = null
        _currentStreamUrl.value = null
        lastPosition = 0
    }
    
    /**
     * COMPLETELY release all resources. Call only on app exit or OOM.
     * Enhanced with proper tick job cleanup - OnTV-main pattern
     */
    fun releaseCompletely() {
        Log.w(TAG, "RELEASING COMPLETELY - destroying singleton player")
        logMemoryUsage()

        // Stop tick updates first
        stopTicking()

        // Cancel any pending load jobs on the player
        singletonPlayer?.loadAndPlayJob?.cancel()
        singletonPlayer?.loadAndPlayJob = null

        // Destroy player
        singletonPlayer?.destroy()
        singletonPlayer = null
        playerContext = null
        isAttached = false
        clearCurrentChannel()

        // Reset state
        _playerState.value = PlayerState()

        // Force garbage collection
        System.gc()

        logMemoryUsage()
    }
    
    /**
     * Mark that we're returning from fullscreen (skip debounce)
     */
    fun markReturningFromFullscreen() {
        skipNextDebounce = true
        Log.d(TAG, "Marked returning from fullscreen - will skip debounce")
    }
    
    /**
     * Check and consume skip debounce flag
     */
    fun shouldSkipDebounce(): Boolean {
        val skip = skipNextDebounce
        skipNextDebounce = false
        return skip
    }
    
    /**
     * Log memory usage for debugging TV box issues
     */
    private fun logMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMem = runtime.maxMemory() / 1024 / 1024
        val freeMem = runtime.freeMemory() / 1024 / 1024
        Log.d(TAG, "Memory: used=${usedMem}MB, free=${freeMem}MB, max=${maxMem}MB")
    }
    
    /**
     * Check if player is valid and can be reused
     */
    fun isPlayerValid(): Boolean {
        return singletonPlayer?.isInitialized == true
    }
}

