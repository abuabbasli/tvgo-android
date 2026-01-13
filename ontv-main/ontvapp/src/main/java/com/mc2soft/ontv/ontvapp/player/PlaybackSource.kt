package com.mc2soft.ontv.ontvapp.player

import android.content.Context
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.*
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.analytics.Analytics
import com.mc2soft.ontv.ontvapp.model.ChannelsCache
import com.mc2soft.ontv.ontvapp.model.SeriesDetails
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import com.mc2soft.ontv.ontvapp.player.ui.UseOrNotSavedPositionDialog
import com.mc2soft.ontv.ontvapp.ui.PinCodeCheckDialog
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class PlaybackSource {
    interface IJumpable {
        val isCanJumpNext: Boolean
        val isCanJumpPrev: Boolean
        fun jumpNext(): PlaybackSource?
        fun jumpPrev(): PlaybackSource?
    }

    sealed class Position()
    data class SeekAndDuration(val seek: Long, val duration: Long) : Position() {
        fun fix(): SeekAndDuration {
            if (seek in 0.. duration)return this
            val d = duration.coerceAtLeast(0)
            return SeekAndDuration(seek.coerceAtLeast(0).coerceAtMost(d), d)
        }
        val progress: Float
            get() = seek.toFloat() / (if (duration > 0)duration else 1)
    }
    data class AbsPosition(val utcMS: Long) : Position()


    open val isCanPause: Boolean
        get() = true
    open val isCanForward: Boolean
        get() = true
    open val isCanBackward: Boolean
        get() = true

    abstract val netItem: Any
    open val isParentalBlock: Boolean
        get() = UserLocalData.inst.isParentalLock(netItem)
    abstract val isCensored: Boolean

    fun showAccessDialogInNeedWithCancelCallback(context: Context, cancelCallback: (()->Unit)?, runCallback: (ps: PlaybackSource)->Unit) {
        if (isCensored || isParentalBlock) {
            MainActivity.get(context)?.let { act->
                PinCodeCheckDialog {
                    runCallback(this)
                }.apply {
                    onCancel = cancelCallback
                }.show(act.supportFragmentManager, null)
            }
        } else {
            runCallback(this)
        }
    }

    fun showAccessDialogInNeed(context: Context, runCallback: (ps: PlaybackSource)->Unit) {
        showAccessDialogInNeedWithCancelCallback(context, null, runCallback)
    }

    open fun showUseSavedPositionDialogInNeed(context: Context, cb: ()->Unit) {
        cb()
    }

    open fun loadUrlAndPlay(player: PlayerView, pauseAfter: Boolean? = false, closeBeforeLoad: Boolean = true, discardSeekProcess: Boolean = true) {}

    class ChannelPlaybackSource : PlaybackSource, IJumpable {
        val channel: NetChannel
        val program: NetChannelProgram?
        val startAbsTime: Long?
        val startLiveOffset: Long?
        val genre: NetGenre?

        constructor(inGenre: NetGenre?, inChannel: NetChannel) {
            genre = inGenre
            channel = inChannel
            program = null
            startAbsTime = null
            startLiveOffset = null
        }

        constructor(inGenre: NetGenre?, inChannel: NetChannel, programTakeIfNotLive: NetChannelProgram) {
            genre = inGenre
            channel = inChannel
            program = programTakeIfNotLive.takeIf { !it.isLive && it.isWasStarted && inChannel.isHaveArchive }
            startAbsTime = null
            startLiveOffset = null
        }

        constructor(inGenre: NetGenre?, inChannel: NetChannel, inStartAbsTime: Long) {
            genre = inGenre
            channel = inChannel
            if (inChannel.isHaveArchive) {
                program = programs?.find {
                    it.startTimeMS <= inStartAbsTime && inStartAbsTime < it.stopTimeMS && !it.isLive && it.isWasStarted
                }
                startAbsTime = inStartAbsTime.takeIf { it > 0 && it < System.currentTimeMillis() }
                startLiveOffset = startAbsTime?.let { System.currentTimeMillis() - it }
            } else {
                program = null
                startAbsTime = null
                startLiveOffset = null
            }
        }

        //если есть program то значит у наз запись имеющая началао и конец и плеер сообшает об окончании проигрывания потока
        //если нету программы значит что у нас поток, поток может быть со смещением(startAbsTime) или без смешения(live)
        //есть ли у потока программа в epg не вилияет на логику, только на отображение

        val isHaveStartAndDuration: Boolean
            get() = program != null

        val isLive: Boolean
            get() = program == null && startAbsTime == null

        val programs: List<NetChannelProgram>?
            get() = ChannelsCache.getPrograms(channel.id)?.value

        val liveProgram: NetChannelProgram?
            get() = if (program == null) programs?.find { it.isLive } else null

        val programOrLiveProgram: NetChannelProgram?
            get() = program ?: liveProgram

        override val isCanPause: Boolean
            get() = channel.isHaveArchive

        override val isCanForward: Boolean
            get() = !isLive

        override val isCanBackward: Boolean
            get() = channel.isHaveArchive

        override fun loadUrlAndPlay(player: PlayerView, pauseAfter: Boolean?, closeBeforeLoad: Boolean, discardSeekProcess: Boolean) {
            player.loadAndPlayJob = null
            player.loadAndPlayJob = AuthorizedUser.scope.launch {
                try {
                    Timber.i("start play channel: ${channel.name} program: ${program?.name}")
                    Analytics.analyticsOnChannelTryGetLink(channel)
                    if (closeBeforeLoad) {
                        player.scope?.launch {
                            player.closeStreamPreparePlaybackSource(this@ChannelPlaybackSource)
                        }
                    }
                    val cmd = channel.cmdFixed?.takeIf { it.isNotBlank() } ?: throw Exception("cmd is null or empty")
                    var startOffset = 0L
                    var link: NetChannelLink? = channel.playableUrl?.let { NetChannelLink(it, null) }
                    val url = if (channel.isMulticast) {
                        Analytics.analyticsOnChannelStartPlayMulticast(channel)
                        program?.let { pr->
                            startOffset = startAbsTime?.let { absT->
                                absT - pr.startTimeMS
                            } ?: 0
                            AuthorizedUser.getProgramVideoStreamUrlHack(pr)
                        } ?: startAbsTime?.let {
                            throw Exception("not implemented")
                        } ?: run {
                            channel.multicastLink
                        }
                    } else {
                        if (link == null)
                            link = AuthorizedUser.getVideoStreamUrl(cmd)
                        program?.let { pr->
                            startOffset = startAbsTime?.let { absT->
                                absT - pr.startTimeMS
                            } ?: 0
                            link.tuneLink(pr.start_timestamp, pr.stop_timestamp - pr.start_timestamp)
                        } ?: startAbsTime?.let {
                            link.tuneLink(it / 1000)
                        } ?: run {
                            link.fixedLink
                        }
                    }?.takeIf { it.isNotBlank() } ?: throw Exception("invalid link cmd=\"${link?.cmd}\" link_id=\"${link?.link_id}\"")
                    Analytics.analyticsOnChannelPlayableLinkObtained(channel, link?.link_id)
                    player.scope?.launch {
                        if (discardSeekProcess) {
                            player.discardSeekProcess()
                        }
                        player.openPlaybackSource(this@ChannelPlaybackSource, url, false, startOffset, pauseAfter)
                    }
                    ChannelsCache.triggerUpdatePrograms(channel.id)
                } catch (ex: java.lang.Exception) {
                    BaseApp.handleError(ex)
                }
            }
        }

        fun prevProgram(): NetChannelProgram? {
            if (!channel.isHaveArchive)return null
            (if (isLive)programs?.indexOfLast { it.isLive } else programs?.indexOfLast { it.id == program?.id })?.let { ind->
                programs?.getOrNull(ind - 1)?.let {
                    return it
                }
            }
            return null
        }

        fun nextProgramInList(): NetChannelProgram? {
            val id = liveProgram?.id ?: program?.id
            programs?.indexOfLast { it.id == id }?.let { ind->
                programs?.getOrNull(ind + 1)?.let {
                    return it
                }
            }
            return null
        }

        fun nextProgram(): NetChannelProgram? {
            if (isLive || !channel.isHaveArchive)return null
            programs?.indexOfLast { it.id == program?.id }?.let { ind->
                programs?.getOrNull(ind + 1)?.let {
                    return it
                }
            }
            return null
        }

        override val isCanJumpNext: Boolean
            get() = nextProgram() != null

        override val isCanJumpPrev: Boolean
            get() = prevProgram() != null

        override fun jumpPrev(): PlaybackSource? {
            return prevProgram()?.let {
                ChannelPlaybackSource(genre, channel, it)
            }
        }

        override fun jumpNext(): PlaybackSource? {
            return nextProgram()?.let {
                return ChannelPlaybackSource(genre, channel, it)
            }
        }

        override val netItem: Any
            get() = channel

        override val isCensored: Boolean
            get() = channel.isCensored
    }

    class MoviePlaybackSource(val movie: NetMovieOrSeries) : PlaybackSource() {
        var vod: NetVOD? = null
            private set

        var startupPos = 0L
            private set

        override fun loadUrlAndPlay(player: PlayerView, pauseAfter: Boolean?, closeBeforeLoad: Boolean, discardSeekProcess: Boolean) {
            player.loadAndPlayJob = null
            player.loadAndPlayJob = AuthorizedUser.scope.launch {
                try {
                    Timber.i("start play movie: ${movie.name} id: ${movie.id}")
                    if (closeBeforeLoad) {
                        player.scope?.launch {
                            player.closeStreamPreparePlaybackSource(this@MoviePlaybackSource)
                        }
                    }
                    vod = AuthorizedUser.getMovieVOD(movie.id)
                    val url = vod?.getPlaybackUrl() ?: throw Exception("movie url invalid")
                    player.scope?.launch {
                        if (discardSeekProcess) {
                            player.discardSeekProcess()
                        }
                        player.openPlaybackSource(this@MoviePlaybackSource, url, true, startupPos, pauseAfter)
                    }
                } catch (ex: java.lang.Exception) {
                    BaseApp.handleError(ex)
                }
            }
        }

        override val netItem: Any
            get() = movie

        override val isCensored: Boolean
            get() = movie.isCensored

        override fun showUseSavedPositionDialogInNeed(context: Context, cb: ()->Unit) {
            val savedPos = UserLocalData.inst.getMovieLastPlayPosition(movie.id)?.seek
            if (savedPos != null && savedPos != 0L) {
                MainActivity.get(context)?.let { act->
                    UseOrNotSavedPositionDialog().apply {
                        callback = {
                            startupPos = if (it) savedPos else 0L
                            cb()
                        }
                    }.show(act.supportFragmentManager, null)
                }
            } else {
                cb()
            }
        }
    }

    class SeriesPlaybackSource(val seriesFull: SeriesDetails, val seasonNumber: Int, val episodeNumber: Int) : PlaybackSource(), IJumpable {
        val series: NetMovieOrSeries = seriesFull.series
        val season: NetSeason = seriesFull.seasons[seasonNumber].season
        val episode: NetEpisode = seriesFull.seasons[seasonNumber].episodes[episodeNumber]

        var startupPos = 0L
            private set

        var vod: NetVOD? = null
            private set

        override fun loadUrlAndPlay(player: PlayerView, pauseAfter: Boolean?, closeBeforeLoad: Boolean, discardSeekProcess: Boolean) {
            player.loadAndPlayJob = null
            player.loadAndPlayJob = AuthorizedUser.scope.launch {
                try {
                    Timber.i("start play series: ${series.name} id: ${series.id} episode: $episodeNumber")
                    if (closeBeforeLoad) {
                        player.scope?.launch {
                            player.closeStreamPreparePlaybackSource(this@SeriesPlaybackSource)
                        }
                    }

                    vod = AuthorizedUser.getSeriesSeasonEpisodeVOD(series.id, season.id, episode.id).get(0)
                    val url = vod?.getPlaybackUrl() ?: throw Exception("movie url invalid")

                    player.scope?.launch {
                        if (discardSeekProcess) {
                            player.discardSeekProcess()
                        }
                        player.openPlaybackSource(this@SeriesPlaybackSource, url, true, startupPos, pauseAfter)
                    }
                } catch (ex: java.lang.Exception) {
                    BaseApp.handleError(ex)
                }
            }
        }

        val prevSeason: SeriesDetails.Season?
            get() = seriesFull.seasons.getOrNull(seasonNumber - 1)

        val nextSeason: SeriesDetails.Season?
            get() = seriesFull.seasons.getOrNull(seasonNumber + 1)

        val prevEpisodeInCurrentSeason: NetEpisode?
            get() = seriesFull.seasons.getOrNull(seasonNumber)?.episodes?.getOrNull(episodeNumber - 1)

        val nextEpisodeInCurrentSeason: NetEpisode?
            get() = seriesFull.seasons.getOrNull(seasonNumber)?.episodes?.getOrNull(episodeNumber + 1)

        val lastEpisodeInPrevSeason: NetEpisode?
            get() = prevSeason?.episodes?.lastOrNull()

        val firstEpisodeInNextSeason: NetEpisode?
            get() = nextSeason?.episodes?.firstOrNull()

        val prevEpisode: NetEpisode?
            get() = prevEpisodeInCurrentSeason ?: lastEpisodeInPrevSeason

        val nextEpisode: NetEpisode?
            get() = nextEpisodeInCurrentSeason ?: firstEpisodeInNextSeason

        override val isCanJumpNext: Boolean
            get() = nextEpisode != null

        override val isCanJumpPrev: Boolean
            get() = prevEpisode != null

        override fun jumpPrev(): PlaybackSource? {
            return prevEpisodeInCurrentSeason?.let {
                SeriesPlaybackSource(seriesFull, seasonNumber, episodeNumber - 1)
            } ?: lastEpisodeInPrevSeason?.let {
                SeriesPlaybackSource(seriesFull, seasonNumber - 1, prevSeason!!.episodes.size - 1)
            }
        }

        override fun jumpNext(): PlaybackSource? {
            return nextEpisodeInCurrentSeason?.let {
                SeriesPlaybackSource(seriesFull, seasonNumber, episodeNumber + 1)
            } ?: firstEpisodeInNextSeason?.let {
                SeriesPlaybackSource(seriesFull, seasonNumber + 1, 0)
            }
        }

        override val netItem: Any
            get() = series

        override val isCensored: Boolean
            get() = series.isCensored

        override fun showUseSavedPositionDialogInNeed(context: Context, cb: ()->Unit) {
            val savedPos = UserLocalData.inst.getMovieOrEpisodeLastPlayPosition(series.id, season.id, episode.id)?.seek
            if (savedPos != null && savedPos != 0L) {
                MainActivity.get(context)?.let { act->
                    UseOrNotSavedPositionDialog().apply {
                        callback = {
                            startupPos = if (it) savedPos else 0L
                            cb()
                        }
                    }.show(act.supportFragmentManager, null)
                }
            } else {
                cb()
            }
        }
    }
}