package com.example.androidtviptvapp.data

import com.example.androidtviptvapp.data.api.CurrentProgram as ApiCurrentProgram
import com.example.androidtviptvapp.player.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * PlaybackSource abstraction - OnTV-main pattern EXACTLY.
 * Separates content types from playback logic with full EPG/timeshift support.
 *
 * KEY FEATURES:
 * - loadUrlAndPlay() with proper job management
 * - Timeshift support (startAbsTime, liveProgram)
 * - Program/archive-aware playback
 * - Looped channel navigation
 */
sealed class PlaybackSource {

    companion object {
        private const val TAG = "PlaybackSource"

        // Shared scope for loading operations - OnTV-main uses AuthorizedUser.scope
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

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
 * Program info for EPG - OnTV-main pattern
 */
data class ProgramInfo(
    val id: String,
    val name: String,
    val startTimeMS: Long,
    val stopTimeMS: Long,
    val durationTimeMS: Long = stopTimeMS - startTimeMS
) {
    val isLive: Boolean
        get() = System.currentTimeMillis() in startTimeMS until stopTimeMS

    val isWasStarted: Boolean
        get() = System.currentTimeMillis() >= startTimeMS
}

/**
 * Channel playback source with EPG integration - OnTV-main pattern EXACTLY
 */
class ChannelPlaybackSource(
    val channel: Channel,
    val currentProgram: ApiCurrentProgram? = null,
    val isLive: Boolean = true,
    private val channelList: List<Channel> = emptyList(),
    val startAbsTime: Long? = null,  // UTC ms for timeshift
    val program: ProgramInfo? = null  // Specific program for archive playback
) : PlaybackSource(), IJumpable {

    override val contentType = ContentType.CHANNEL
    override val title: String = channel.name
    override val logoUrl: String? = channel.logo.takeIf { it.isNotEmpty() }
    override val streamUrl: String = channel.streamUrl

    // Live channels can only pause if they have archive support
    override val canPause: Boolean = channel.hasArchive

    // Can't seek forward on live
    override val canSeekForward: Boolean = !isLive

    // Can always seek back if archive is available
    override val canSeekBackward: Boolean = channel.hasArchive

    /**
     * Whether this source has a defined start and duration (archive program)
     * OnTV-main pattern
     */
    val isHaveStartAndDuration: Boolean
        get() = program != null

    /**
     * Start live offset for timeshift (OnTV-main pattern)
     */
    val startLiveOffset: Long?
        get() = startAbsTime?.let { System.currentTimeMillis() - it }

    /**
     * Get the current live program from EPG (OnTV-main pattern)
     */
    val liveProgram: ProgramInfo?
        get() = if (program == null) {
            currentProgram?.let { prog ->
                ProgramInfo(
                    id = prog.id,
                    name = prog.title,
                    startTimeMS = prog.startTime,
                    stopTimeMS = prog.stopTime
                )
            }
        } else null

    /**
     * Get program or live program (OnTV-main pattern)
     */
    val programOrLiveProgram: ProgramInfo?
        get() = program ?: liveProgram

    // Current position in channel list
    private val currentIndex: Int
        get() = channelList.indexOfFirst { it.id == channel.id }

    override val canJumpNext: Boolean
        get() = channelList.isNotEmpty()

    override val canJumpPrev: Boolean
        get() = channelList.isNotEmpty()

    /**
     * Get all channels for navigation (OnTV-main pattern)
     */
    fun getAllChannels(): List<Channel> = channelList

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
                Timber.i("start play channel: ${channel.name} program: ${program?.name}")

                // Show loading state before URL is resolved (OnTV-main pattern)
                if (closeBeforeLoad) {
                    player.scope?.launch {
                        player.closeStreamPreparePlaybackSource(this@ChannelPlaybackSource)
                    }
                }

                // Get the stream URL
                val url = channel.streamUrl
                if (url.isBlank()) {
                    throw Exception("Stream URL is empty for channel ${channel.name}")
                }

                // Calculate start offset for archive/timeshift
                var startOffset = 0L
                program?.let { pr ->
                    startOffset = startAbsTime?.let { absT ->
                        absT - pr.startTimeMS
                    } ?: 0
                }

                Timber.i("loadUrlAndPlay: Got URL for ${channel.name}: $url, startOffset: $startOffset")

                // Open the stream on main thread (OnTV-main pattern)
                player.scope?.launch {
                    if (discardSeekProcess) {
                        player.discardSeekProcess()
                    }
                    player.openPlaybackSource(
                        this@ChannelPlaybackSource,
                        url,
                        vod = false,
                        pos = if (startOffset > 0) startOffset else null,
                        pauseAfter = pauseAfter
                    )
                }

                // Trigger EPG update for this channel (OnTV-main pattern)
                TvRepository.triggerUpdatePrograms(channel.id)

            } catch (ex: Exception) {
                Timber.e(ex, "loadUrlAndPlay failed for ${channel.name}")
            }
        }
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
                Timber.i("start play movie: ${movie.title} id: ${movie.id}")

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

                Timber.i("loadUrlAndPlay: Got URL for ${movie.title}, resume at $startPos ms")

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
                Timber.e(ex, "loadUrlAndPlay failed for ${movie.title}")
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
}

/**
 * Audio track info for track selection - kept for compatibility
 */
data class AudioTrack(
    val id: String,
    val name: String,
    val language: String?,
    val isSelected: Boolean = false
)
