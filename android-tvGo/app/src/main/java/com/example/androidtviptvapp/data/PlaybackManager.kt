package com.example.androidtviptvapp.data

import android.content.Context
import android.util.Log
import com.example.androidtviptvapp.player.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackManager - lightweight manager for View-based PlayerView.
 * The actual player logic is in AdaptExoPlayerView/PlayerView.
 * This singleton just holds reference to the active player view.
 */
object PlaybackManager {
    private const val TAG = "PlaybackManager"

    // Current active player view (set by PlayerScreen)
    private var activePlayerView: PlayerView? = null

    // Current source state
    private val _currentSource = MutableStateFlow<PlaybackSource?>(null)
    val currentSource: StateFlow<PlaybackSource?> = _currentSource.asStateFlow()

    // Callbacks (forwarded from PlayerView)
    var onPlaybackError: ((String, Boolean) -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null

    /**
     * Register the active player view
     */
    fun registerPlayerView(playerView: PlayerView) {
        Log.d(TAG, "registerPlayerView")
        activePlayerView = playerView
        
        // Wire up callbacks
        playerView.onSourceChanged = { source ->
            _currentSource.value = source
        }
        playerView.onPlaybackError = onPlaybackError
        playerView.onPlaybackReady = onPlaybackReady
    }

    /**
     * Unregister player view
     */
    fun unregisterPlayerView(playerView: PlayerView) {
        if (activePlayerView == playerView) {
            Log.d(TAG, "unregisterPlayerView")
            activePlayerView = null
        }
    }

    /**
     * Get current player view
     */
    fun getActivePlayerView(): PlayerView? = activePlayerView

    /**
     * Play a playback source on the active player
     */
    fun playSource(context: Context, source: PlaybackSource) {
        Log.d(TAG, "playSource: ${source.title}")
        _currentSource.value = source
        activePlayerView?.play(source) ?: Log.w(TAG, "No active player view")
    }

    /**
     * Play URL directly
     */
    fun playUrl(context: Context, url: String, isVod: Boolean = false) {
        Log.d(TAG, "playUrl: $url")
        activePlayerView?.playUrl(url, isVod) ?: Log.w(TAG, "No active player view")
    }

    /**
     * Jump channel on active player
     */
    fun jumpChannel(context: Context, direction: Int): Boolean {
        return activePlayerView?.jumpChannel(direction) != null
    }

    /**
     * Release current playback
     */
    fun release() {
        activePlayerView = null
        _currentSource.value = null
    }
}
