package com.example.androidtviptvapp.data

import android.util.Log
import com.example.androidtviptvapp.data.api.CurrentProgram as ApiCurrentProgram
import com.example.androidtviptvapp.player.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PlaybackSource abstraction - separates content types from playback logic.
 * Based on OnTV-main's architecture for robust IPTV playback.
 *
 * KEY FEATURE: loadUrlAndPlay() method follows OnTV-main pattern for proper job management
 *
 * Key benefits:
 * - Content-type awareness (channel vs movie)
 * - EPG integration for channels
 * - Resume position for movies
 * - Navigation support (prev/next channel)
 * - Proper job cancellation to prevent race conditions
 */
sealed class PlaybackSource {

    companion object {
        private const val TAG = "PlaybackSource"

        // Shared scope for loading operations - OnTV-main uses AuthorizedUser.scope
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * Position for seek operations - relative to start
     */
    data class SeekPosition(val seekMs: Long, val durationMs: Long) {
        val progress: Float
            get() = if (durationMs > 0) seekMs.toFloat() / durationMs else 0f

        fun coerceIn(): SeekPosition {
            val d = durationMs.coerceAtLeast(0)
            return SeekPosition(seekMs.coerceIn(0, d), d)
        }
    }

    /**
     * Absolute position for timeshift - UTC timestamp
     */
    data class AbsolutePosition(val utcMs: Long)

    abstract val contentType: ContentType
    abstract val title: String
    abstract val logoUrl: String?
    abstract val streamUrl: String

    /**
     * Whether playback can be paused (live channels without archive = no)
     */
    open val canPause: Boolean = true

    /**
     * Whether seeking forward is allowed
     */
    open val canSeekForward: Boolean = true

    /**
     * Whether seeking backward is allowed
     */
    open val canSeekBackward: Boolean = true

    enum class ContentType {
        CHANNEL,
        MOVIE
    }

    /**
     * Load URL and start playback - OnTV-main pattern
     *
     * CRITICAL: This sets player.loadAndPlayJob which automatically cancels
     * any previous load operation, preventing race conditions when rapidly
     * switching channels.
     *
     * @param player The PlayerView to play on
     * @param pauseAfter Whether to pause after loading (default false)
     * @param closeBeforeLoad Whether to close stream before loading (shows loading UI)
     * @param discardSeekProcess Whether to discard any ongoing seek process
     */
    open fun loadUrlAndPlay(
        player: PlayerView,
        pauseAfter: Boolean? = false,
        closeBeforeLoad: Boolean = true,
        discardSeekProcess: Boolean = true
    ) {
        // Default implementation - subclasses override
    }
}

/**
 * Interface for sources that support prev/next navigation (channels, series episodes)
 */
interface IJumpable {
    val canJumpNext: Boolean
    val canJumpPrev: Boolean
    fun jumpNext(): PlaybackSource?
    fun jumpPrev(): PlaybackSource?
}

/**
 * Channel playback source with EPG integration
 */
class ChannelPlaybackSource(
    val channel: Channel,
    val currentProgram: ApiCurrentProgram? = null,
    val isLive: Boolean = true,
    private val channelList: List<Channel> = emptyList()
) : PlaybackSource(), IJumpable {

    override val contentType = ContentType.CHANNEL
    override val title: String = channel.name
    override val logoUrl: String? = channel.logo.takeIf { it.isNotEmpty() }
    override val streamUrl: String = channel.streamUrl

    // Live channels can only pause if they have archive support
    override val canPause: Boolean = true // Assume all can pause for now

    // Can't seek forward on live
    override val canSeekForward: Boolean = !isLive

    // Can always seek back if archive is available
    override val canSeekBackward: Boolean = true

    // Current position in channel list
    private val currentIndex: Int
        get() = channelList.indexOfFirst { it.id == channel.id }

    override val canJumpNext: Boolean
        get() = channelList.isNotEmpty()

    override val canJumpPrev: Boolean
        get() = channelList.isNotEmpty()

    override fun jumpNext(): ChannelPlaybackSource? {
        if (channelList.isEmpty()) return null
        val newIndex = (currentIndex + 1 + channelList.size) % channelList.size
        val nextChannel = channelList.getOrNull(newIndex) ?: return null
        if (nextChannel.id == channel.id) return null
        return ChannelPlaybackSource(
            channel = nextChannel,
            currentProgram = TvRepository.currentPrograms[nextChannel.id],
            isLive = true,
            channelList = channelList
        )
    }

    override fun jumpPrev(): ChannelPlaybackSource? {
        if (channelList.isEmpty()) return null
        val newIndex = (currentIndex - 1 + channelList.size) % channelList.size
        val prevChannel = channelList.getOrNull(newIndex) ?: return null
        if (prevChannel.id == channel.id) return null
        return ChannelPlaybackSource(
            channel = prevChannel,
            currentProgram = TvRepository.currentPrograms[prevChannel.id],
            isLive = true,
            channelList = channelList
        )
    }

    /**
     * Jump to specific channel (looped navigation) - OnTV-main pattern
     */
    fun jumpToChannel(direction: Int): ChannelPlaybackSource? {
        if (channelList.isEmpty()) return null
        val newIndex = (currentIndex + direction + channelList.size) % channelList.size
        val nextChannel = channelList.getOrNull(newIndex) ?: return null
        if (nextChannel.id == channel.id) return null

        return ChannelPlaybackSource(
            channel = nextChannel,
            currentProgram = TvRepository.currentPrograms[nextChannel.id],
            isLive = true,
            channelList = channelList
        )
    }

    /**
     * Load URL and play channel - OnTV-main pattern EXACTLY
     *
     * CRITICAL: Sets player.loadAndPlayJob to cancel previous loads
     */
    override fun loadUrlAndPlay(
        player: PlayerView,
        pauseAfter: Boolean?,
        closeBeforeLoad: Boolean,
        discardSeekProcess: Boolean
    ) {
        // Cancel any previous job and start new one
        player.loadAndPlayJob = null
        player.loadAndPlayJob = loadScope.launch {
            try {
                Log.d(TAG, "loadUrlAndPlay: Starting channel ${channel.name}")

                // Show loading state before URL is resolved (OnTV-main pattern)
                if (closeBeforeLoad) {
                    player.scope?.launch {
                        player.closeStreamPreparePlaybackSource(this@ChannelPlaybackSource)
                    }
                }

                // Get the stream URL (already resolved in our case)
                val url = channel.streamUrl
                if (url.isBlank()) {
                    throw Exception("Stream URL is empty for channel ${channel.name}")
                }

                Log.d(TAG, "loadUrlAndPlay: Got URL for ${channel.name}: $url")

                // Open the stream on main thread (OnTV-main pattern)
                player.scope?.launch {
                    if (discardSeekProcess) {
                        player.discardSeekProcess()
                    }
                    player.openPlaybackSource(
                        this@ChannelPlaybackSource,
                        url,
                        vod = false,
                        pos = null,
                        pauseAfter = pauseAfter
                    )
                }

                // Trigger EPG update for this channel (OnTV-main pattern)
                TvRepository.triggerUpdatePrograms(channel.id)

            } catch (ex: Exception) {
                Log.e(TAG, "loadUrlAndPlay failed for ${channel.name}: ${ex.message}", ex)
                // Error will be handled by player's error handling
            }
        }
    }

    companion object {
        private const val TAG = "ChannelPlaybackSource"

        /**
         * Create a channel source with full channel list for navigation
         */
        fun create(channel: Channel, allChannels: List<Channel>): ChannelPlaybackSource {
            return ChannelPlaybackSource(
                channel = channel,
                currentProgram = TvRepository.currentPrograms[channel.id],
                isLive = true,
                channelList = allChannels
            )
        }
    }
}

/**
 * Movie/VOD playback source with resume position support
 */
class MoviePlaybackSource(
    val movie: Movie,
    var resumePosition: Long = 0L
) : PlaybackSource() {

    override val contentType = ContentType.MOVIE
    override val title: String = movie.title
    override val logoUrl: String? = movie.thumbnail.takeIf { it.isNotEmpty() }
    override val streamUrl: String = movie.videoUrl

    // Movies can always pause and seek
    override val canPause: Boolean = true
    override val canSeekForward: Boolean = true
    override val canSeekBackward: Boolean = true

    /**
     * Load URL and play movie - OnTV-main pattern
     *
     * CRITICAL: Sets player.loadAndPlayJob to cancel previous loads
     */
    override fun loadUrlAndPlay(
        player: PlayerView,
        pauseAfter: Boolean?,
        closeBeforeLoad: Boolean,
        discardSeekProcess: Boolean
    ) {
        player.loadAndPlayJob = null
        player.loadAndPlayJob = loadScope.launch {
            try {
                Log.d(TAG, "loadUrlAndPlay: Starting movie ${movie.title}")

                // Show loading state before URL is resolved
                if (closeBeforeLoad) {
                    player.scope?.launch {
                        player.closeStreamPreparePlaybackSource(this@MoviePlaybackSource)
                    }
                }

                val url = movie.videoUrl
                if (url.isBlank()) {
                    throw Exception("Video URL is empty for movie ${movie.title}")
                }

                // Get saved position for resume
                val savedPosition = TvRepository.getMoviePosition(movie.id)
                val startPos = if (savedPosition > 0) savedPosition else resumePosition

                Log.d(TAG, "loadUrlAndPlay: Got URL for ${movie.title}, resume at $startPos ms")

                // Open the stream on main thread
                player.scope?.launch {
                    if (discardSeekProcess) {
                        player.discardSeekProcess()
                    }
                    player.openPlaybackSource(
                        this@MoviePlaybackSource,
                        url,
                        vod = true,
                        pos = if (startPos > 0) startPos else null,
                        pauseAfter = pauseAfter
                    )
                }

            } catch (ex: Exception) {
                Log.e(TAG, "loadUrlAndPlay failed for ${movie.title}: ${ex.message}", ex)
            }
        }
    }

    /**
     * Save current playback position for resume later
     */
    fun savePosition(positionMs: Long) {
        resumePosition = positionMs
        TvRepository.saveMoviePosition(movie.id, positionMs)
    }

    companion object {
        private const val TAG = "MoviePlaybackSource"
    }
}

/**
 * Audio track info for track selection
 */
data class AudioTrack(
    val id: String,
    val name: String,
    val language: String?,
    val isSelected: Boolean = false
)
