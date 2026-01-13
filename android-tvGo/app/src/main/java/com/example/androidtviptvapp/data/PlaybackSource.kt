package com.example.androidtviptvapp.data

import com.example.androidtviptvapp.data.api.CurrentProgram as ApiCurrentProgram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackSource abstraction - separates content types from playback logic.
 * Based on OnTV-main's architecture for robust IPTV playback.
 * 
 * Key benefits:
 * - Content-type awareness (channel vs movie)
 * - EPG integration for channels
 * - Resume position for movies
 * - Navigation support (prev/next channel)
 */
sealed class PlaybackSource {
    
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
        get() = channelList.isNotEmpty() && currentIndex < channelList.size - 1
    
    override val canJumpPrev: Boolean
        get() = channelList.isNotEmpty() && currentIndex > 0
    
    override fun jumpNext(): ChannelPlaybackSource? {
        if (!canJumpNext) return null
        val nextChannel = channelList.getOrNull(currentIndex + 1) ?: return null
        return ChannelPlaybackSource(
            channel = nextChannel,
            currentProgram = null, // Will be loaded lazily
            isLive = true,
            channelList = channelList
        )
    }
    
    override fun jumpPrev(): ChannelPlaybackSource? {
        if (!canJumpPrev) return null
        val prevChannel = channelList.getOrNull(currentIndex - 1) ?: return null
        return ChannelPlaybackSource(
            channel = prevChannel,
            currentProgram = null,
            isLive = true,
            channelList = channelList
        )
    }
    
    /**
     * Jump to specific channel (looped navigation)
     */
    fun jumpToChannel(direction: Int): ChannelPlaybackSource? {
        if (channelList.isEmpty()) return null
        val newIndex = (currentIndex + direction + channelList.size) % channelList.size
        val nextChannel = channelList.getOrNull(newIndex) ?: return null
        if (nextChannel.id == channel.id) return null
        
        return ChannelPlaybackSource(
            channel = nextChannel,
            currentProgram = null,
            isLive = true,
            channelList = channelList
        )
    }
    
    companion object {
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
     * Save current playback position for resume later
     */
    fun savePosition(positionMs: Long) {
        resumePosition = positionMs
        // TODO: Persist to SharedPreferences or database
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
