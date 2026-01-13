package com.mc2soft.ontv.ontvapp.player

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.mc2soft.ontv.common.ui.UiUtil
import com.mc2soft.ontv.common.ui.UserDialog
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.analytics.Analytics
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Exception


class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AdaptExoPlayerView(context, attrs, defStyle) {

    companion object {
        const val DEFAULT_VIDEO_RESIZE_MODE = AspectRatioFrameLayout.RESIZE_MODE_FILL
        const val DELAY_AFTER_ERROR = 2000L
    }

    var loadAndPlayJob: Job? = null
        set(j) {
            field?.cancel()
            field = j
        }
        get() = if (field?.isActive == true) field else null

    private val playbackSourceMutableFlow = MutableStateFlow<PlaybackSource?>(null)
    val playbackSourceFlow = playbackSourceMutableFlow.asStateFlow()

    val playbackSource: PlaybackSource?
        get() = playbackSourceFlow.value

    val channelPlaybackSource: PlaybackSource.ChannelPlaybackSource?
        get() = playbackSource as? PlaybackSource.ChannelPlaybackSource

    val seriesPlaybackSource: PlaybackSource.SeriesPlaybackSource?
        get() = playbackSource as? PlaybackSource.SeriesPlaybackSource

    val isPlayingOrPlanPlayingSomething: Boolean
        get() = playbackSource != null || loadAndPlayJob?.isActive == true

    fun openPlaybackSource(ps: PlaybackSource, url: String, vod: Boolean, pos: Long? = null, pauseAfter: Boolean? = null) {
        if (playbackSource != null) {
            onPreStreamClose()
        }
        closeStream()
        playbackSourceMutableFlow.value = ps
        openStream(url, vod, pos, pauseAfter)
    }

    fun closeStreamPreparePlaybackSource(ps: PlaybackSource) {
        if (playbackSource != null) {
            onPreStreamClose()
        }
        closeStream()
        playbackSourceMutableFlow.value = ps
    }

    fun closePlaybackSource() {
        if (playbackSource != null) {
            onPreStreamClose()
        }
        loadAndPlayJob?.cancel()
        loadAndPlayJob = null
        closeStream()
        playbackSourceMutableFlow.value = null
    }

    private var isPauseAfterSeekProcess = false
    private var seekProcessLastPointTimestamp: Long = 0
    private var seekProcessLastPoint: Long = 0
    private var seekProcessSeed: Long = 60

    override var pause: Boolean
        get() = if (seekProcessDirection == 0) super.pause else isPauseAfterSeekProcess
        set(v) {
            if (pause == v)return
            if (currentError != null) {
                discardSeekProcess()
                super.pause = v
                if (!v) {
                    handleError(true)
                }
            } else {
                seekProcessDirection = 0
                val channelPlaybackSource = channelPlaybackSource
                if (channelPlaybackSource?.isLive == true && !v) {
                    super.pause = true
                    val pos = System.currentTimeMillis() - (liveOffset ?: 20000)
                    PlaybackSource.ChannelPlaybackSource(channelPlaybackSource.genre, channelPlaybackSource.channel, pos).loadUrlAndPlay(this)
                } else {
                    super.pause = v
                }
            }
        }


    //utc ms for live and livepvr
    val targetSeek: PlaybackSource.Position
        get() = if (seekProcessDirection != 0) {
            val p = (System.currentTimeMillis() - seekProcessLastPointTimestamp) * seekProcessDirection * seekProcessSeed + seekProcessLastPoint
            channelPlaybackSource?.takeIf { !it.isHaveStartAndDuration }?.let {
                PlaybackSource.AbsPosition(p)
            } ?: PlaybackSource.SeekAndDuration(p, duration).fix()
        } else {
            channelPlaybackSource?.takeIf { !it.isHaveStartAndDuration }?.let {
                PlaybackSource.AbsPosition(System.currentTimeMillis() - (liveOffset ?: it.startLiveOffset ?: 0))
            } ?: PlaybackSource.SeekAndDuration(seek, duration).fix()
        }

    val userReadableSeek: PlaybackSource.Position
        get() {
            val p = targetSeek
            if (p is PlaybackSource.AbsPosition) {
                channelPlaybackSource?.liveProgram?.let { liveProgram ->
                    return PlaybackSource.SeekAndDuration(p.utcMS - liveProgram.startTimeMS, liveProgram.durationTimeMS)
                }
            }
            return p
        }

    private var seekProcessDirectionField: Int = 0

    fun discardSeekProcess(seek_: Long? = null) {
        if (seekProcessDirectionField == 0)return
        seek_?.let { seek = it }
        super.pause = isPauseAfterSeekProcess
        seekProcessDirectionField = 0
    }

    var seekProcessDirection: Int
        get() = seekProcessDirectionField
        set (v) {
            if (seekProcessDirectionField == v)return
            val targetSeek = targetSeek
            if (v == 0) {
                when (targetSeek) {
                    is PlaybackSource.AbsPosition -> {
                        val channelPlaybackSource = channelPlaybackSource!!
                        if (targetSeek.utcMS >= System.currentTimeMillis()) {
                            PlaybackSource.ChannelPlaybackSource(channelPlaybackSource.genre, channelPlaybackSource.channel)
                        } else {
                            PlaybackSource.ChannelPlaybackSource(channelPlaybackSource.genre, channelPlaybackSource.channel,
                                channelPlaybackSource.liveProgram?.takeIf { targetSeek.utcMS <= it.startTimeMS }?.let {
                                    it.startTimeMS
                                } ?: targetSeek.utcMS
                            )
                        }.loadUrlAndPlay(this, isPauseAfterSeekProcess)
                    }
                    is PlaybackSource.SeekAndDuration -> {
                        seek = targetSeek.seek
                        super.pause = isPauseAfterSeekProcess
                    }
                }
                seekProcessDirectionField = v
            } else {
                if (v > 0) {
                    if (channelPlaybackSource?.isLive == true)return
                } else {
                    (userReadableSeek as? PlaybackSource.SeekAndDuration)?.seek?.takeIf {
                        it < if (channelPlaybackSource?.isHaveStartAndDuration == false) 15000 else 5000 }?.let {
                        if (showGoToLiveDialog(-1, false))
                            return
                    }
                }
                seekProcessLastPoint = when (targetSeek) {
                    is PlaybackSource.AbsPosition -> targetSeek.utcMS
                    is PlaybackSource.SeekAndDuration -> targetSeek.seek
                }
                seekProcessLastPointTimestamp = System.currentTimeMillis()
                isPauseAfterSeekProcess = pause
                seekProcessDirectionField = v
                super.pause = true
            }
        }


    private var isPlaybackEndedHandled = false

    override fun onPlayerChange() {
        if  (isPlayReady) {
            isRertyAfterError = null
        }
        if (isPlaybackEnded && !isPlaybackEndedHandled) {
            isPlaybackEndedHandled = true
            seekProcessDirection = 0
            if (loadAndPlayJob?.isActive != true && goToLiveDialog == null) {
                channelPlaybackSource?.let {
                    showGoToLiveDialog(1, true)
                }
            }
            (playbackSource as? PlaybackSource.MoviePlaybackSource)?.let { ps->
                Analytics.analyticsOnVodPlaybackEnded(ps.vod)
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

    var isRertyAfterError: Throwable? = null
        private set

    fun handleError(immediate: Boolean = false) {
        val error = currentError ?: return
        if (pause) return
        //too old
        channelPlaybackSource?.let { src->
            (src.program?.startTimeMS ?: src.startAbsTime)?.let { startTime->
                if (System.currentTimeMillis() - startTime > 2 * 24 * 60 * 60000) {
                    PlaybackSource.ChannelPlaybackSource(src.genre, src.channel).loadUrlAndPlay(this)
                    return
                }
            }
        }
        if (isShouldRestartNowException(error)) {
            Firebase.analytics.logEvent(name = "app_error"){
                param("message", "isShouldRestartNowException")
                channelPlaybackSource?.channel?.name?.let { param("channel", it) }
            }
            Timber.e("isShouldRestartNowException")
            if (channelPlaybackSource?.isLive == true) {
                restartStream()
            } else {
                channelPlaybackSource?.loadUrlAndPlay(this)
            }
        } else {
            scope?.launch {
                if (!immediate && System.currentTimeMillis() - lastUserSetAudioTrackTimestamp > DELAY_AFTER_ERROR * 2) {
                    delay(DELAY_AFTER_ERROR)
                }
                if (currentError != null && !pause) {
                    isRertyAfterError = currentError
                    if (!isSourceException(currentError)) {
                        GlobalScope.launch(Dispatchers.Main) {
                            reinit()
                            playbackSource?.loadUrlAndPlay(this@PlayerView)
                        }
                    } else {
                        playbackSource?.loadUrlAndPlay(this@PlayerView)
                    }
                }
            }
        }
    }

    var playReadyCount = 0
        private set

    fun tick() {
        if (isPlayReady)
            playReadyCount += 1
        else if (!isBuffering)
            playReadyCount = 0

        if (seekProcessDirection != 0) {
            val targetSeek = targetSeek
            when (targetSeek) {
                is PlaybackSource.AbsPosition -> {
                    val channelSource = channelPlaybackSource!!
                    if (targetSeek.utcMS >= System.currentTimeMillis()) {
                        discardSeekProcess()
                        PlaybackSource.ChannelPlaybackSource(channelSource.genre, channelSource.channel).loadUrlAndPlay(this, isPauseAfterSeekProcess)
                    } else {
                        channelSource.liveProgram?.takeIf { targetSeek.utcMS <= it.startTimeMS }?.let {
                            discardSeekProcess()
                            PlaybackSource.ChannelPlaybackSource(channelSource.genre, channelSource.channel, it.startTimeMS).loadUrlAndPlay(this, isPauseAfterSeekProcess)
                        } ?: run {
                            if (isPlayReady && loadAndPlayJob?.isActive != true) {
                                seekProcessLastPoint = targetSeek.utcMS
                                seekProcessLastPointTimestamp = System.currentTimeMillis()
                                PlaybackSource.ChannelPlaybackSource(channelSource.genre, channelSource.channel, targetSeek.utcMS)
                                    .loadUrlAndPlay(this, null, true, false)
                            }
                        }
                    }
                }
                is PlaybackSource.SeekAndDuration -> {
                    if (targetSeek.seek <= 0) {
                        discardSeekProcess(0)
                    } else if (targetSeek.seek >= targetSeek.duration) {
                        discardSeekProcess(targetSeek.duration)
                        showGoToLiveDialog(1, true)
                    } else if (isPlayReady && loadAndPlayJob?.isActive != true) {
                        seekProcessLastPoint = targetSeek.seek
                        seekProcessLastPointTimestamp = System.currentTimeMillis()
                        seek = targetSeek.seek
                    }
                }
            }
        }

        if (playReadyCount * MainActivity.TICK_DT > UserLocalData.ADD_TO_HISTORY_AFTER_TIME) {
            addContentToHistory()
        }
    }

    val GO_TO_LIVE_DLG_TAG = "GoToLiveDialog"

    val goToLiveDialog: GoToLiveDialog?
        get() = MainActivity.get(context)?.supportFragmentManager?.findFragmentByTag(GO_TO_LIVE_DLG_TAG) as? GoToLiveDialog

    fun showGoToLiveDialog(dir: Int, force: Boolean): Boolean {
        val channelPlaybackSource = channelPlaybackSource ?: return false
        val prevnextSource = if (dir > 0) channelPlaybackSource.jumpNext() else channelPlaybackSource.jumpPrev()
        val act = UiUtil.getActivity<MainActivity>(context)
        val existDlg = goToLiveDialog
        val noChoice = (prevnextSource as? PlaybackSource.ChannelPlaybackSource)?.isLive == true
        if (prevnextSource == null || act == null || noChoice) {
            if (force || noChoice) {
                existDlg?.dismiss()
                PlaybackSource.ChannelPlaybackSource(channelPlaybackSource.genre, channelPlaybackSource.channel).loadUrlAndPlay(this)
            }
            return false
        }

        if (existDlg != null) return false

        GoToLiveDialog().also {
            it.dir = dir
            it.channelPlaybackSource = channelPlaybackSource
            it.nextPlaybackSource = prevnextSource
            it.player = this
        }.show(act.supportFragmentManager, GO_TO_LIVE_DLG_TAG)

        return true
    }

    class GoToLiveDialog : UserDialog() {
        var dir : Int = 0
        var channelPlaybackSource: PlaybackSource.ChannelPlaybackSource? = null
        var nextPlaybackSource: PlaybackSource? = null
        var player: PlayerView? = null

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setStyleQuestion()
            setTexts(resources.getString(R.string.go_to_live_dlg_title), resources.getString(R.string.go_to_live_dlg_message))
            addButton(resources.getString(R.string.go_to_live_dlg_btn_live)) {
                dismiss()
                channelPlaybackSource?.let { ps->
                    PlaybackSource.ChannelPlaybackSource(ps.genre, ps.channel).loadUrlAndPlay(player!!, false)
                }
            }
            addButton( if (dir > 0) resources.getString(R.string.go_to_live_dlg_btn_next) else resources.getString(R.string.go_to_live_dlg_btn_prev)) {
                dismiss()
                nextPlaybackSource?.loadUrlAndPlay(player!!)
            }
        }
    }

    fun onPreStreamClose() {
        addContentToHistory()
        if (!isPlaybackEnded) {
            (playbackSource as? PlaybackSource.MoviePlaybackSource)?.let { ps ->
                Analytics.analyticsOnVodPlaybackNotEnded(ps.vod, (seek / 1000).toInt())
            }
            (playbackSource as? PlaybackSource.SeriesPlaybackSource)?.let { ps ->
                Analytics.analyticsOnVodPlaybackNotEnded(ps.vod, (seek / 1000).toInt())
            }
        }
    }

    fun addContentToHistory() {
        val ps = playbackSource
        when (ps) {
            is PlaybackSource.ChannelPlaybackSource -> UserLocalData.inst.addChannelToHistory(ps.channel)
            is PlaybackSource.MoviePlaybackSource -> UserLocalData.inst.addMovieToHistory(ps.movie, seek)
            is PlaybackSource.SeriesPlaybackSource -> UserLocalData.inst.addEpisodeToHistory(ps.series, ps.season.id, ps.episode.id, seek, duration)
        }
    }
}
