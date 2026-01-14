package com.example.androidtviptvapp.player

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.util.AttributeSet
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.UdpDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.video.VideoSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * AdaptExoPlayerView - View-based ExoPlayer following OnTV-main pattern EXACTLY.
 * Implements Player.Listener AND AnalyticsListener for full event handling.
 * Supports UDP, HLS, DASH, and Progressive streams.
 */
open class AdaptExoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), Player.Listener, AnalyticsListener {

    companion object {
        const val RESIZE_MODE_FIT = AspectRatioFrameLayout.RESIZE_MODE_FIT
        const val RESIZE_MODE_FILL = AspectRatioFrameLayout.RESIZE_MODE_FILL
        const val RESIZE_MODE_ZOOM = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        const val DEFAULT_VIDEO_RESIZE_MODE = AspectRatioFrameLayout.RESIZE_MODE_FILL

        // Setting to toggle TextureView vs SurfaceView (OnTV-main: ISharedSettingsStorage.isUseTextureViewInPlayer)
        var useTextureView: Boolean = false
    }

    // Player state properties (matching OnTV-main exactly)
    open var pause: Boolean
        get() = player?.playWhenReady == false
        set(v) {
            player?.playWhenReady = !v
        }

    var seek: Long
        get() = if (player != null) player!!.currentPosition else 0
        set(v) {
            player?.seekTo(v)
        }

    val liveOffset: Long?
        get() = player?.currentLiveOffset?.takeIf { it != C.TIME_UNSET }

    val duration: Long
        get() = if (player != null) player!!.duration else 0

    val isPlayReady: Boolean
        get() = mediaSource != null && player?.playbackState == Player.STATE_READY

    val isBuffering: Boolean
        get() = player?.playbackState == Player.STATE_BUFFERING

    val isPlaybackEnded: Boolean
        get() = player?.playbackState == Player.STATE_ENDED

    val isInitialized: Boolean
        get() = player != null

    var scope: CoroutineScope? = null
        private set

    private var videoSourceAspect: Float = 1.0f

    val totalBufferedDuration: Long
        get() = player?.totalBufferedDuration ?: 0

    var currentError: Throwable? = null
        private set

    private var aspectRatioFrameLayout: AspectRatioFrameLayout? = null
    private var systemHandler: Handler? = null
    private var videoView: View? = null
    protected var player: ExoPlayer? = null
    private var mediaSource: MediaSource? = null
    private var defaultBandwidthMeter: DefaultBandwidthMeter? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var loadControl: DefaultLoadControl? = null

    // RESIZE_MODE_FIT, RESIZE_MODE_FIXED_WIDTH, RESIZE_MODE_FIXED_HEIGHT, RESIZE_MODE_FILL, RESIZE_MODE_ZOOM
    var resizeMode: Int = DEFAULT_VIDEO_RESIZE_MODE
        set(v) {
            field = v
            aspectRatioFrameLayout?.resizeMode = v
        }

    /**
     * Refresh output view - switch between SurfaceView and TextureView (OnTV-main pattern)
     */
    fun refreshOutputView() {
        val aspectRatioFrameLayout = aspectRatioFrameLayout ?: return
        val newVideoView: View
        if (useTextureView) {
            if (videoView is TextureView) return
            newVideoView = TextureView(context)
        } else {
            if (videoView is SurfaceView) return
            newVideoView = SurfaceView(context)
        }
        Timber.e("recreate player output surface to ${newVideoView.javaClass.simpleName}")
        aspectRatioFrameLayout.addView(newVideoView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).also { setRectLayoutParams(it) })
        when (newVideoView) {
            is SurfaceView -> player?.setVideoSurfaceView(newVideoView)
            is TextureView -> player?.setVideoTextureView(newVideoView)
        }
        aspectRatioFrameLayout.removeView(videoView)
        videoView = newVideoView
    }

    /**
     * Output rect for video positioning (OnTV-main pattern)
     */
    var outputRect: Rect? = null
        set(rect) {
            field = rect?.let { Rect(it) }
            aspectRatioFrameLayout?.let {
                setRectLayoutParams(it.layoutParams as LayoutParams)
                it.requestLayout()
            }
        }

    private fun setRectLayoutParams(lp: LayoutParams): LayoutParams {
        return lp.apply {
            outputRect?.let { rect ->
                width = rect.width()
                height = rect.height()
                setMargins(rect.left, rect.top, 0, 0)
                gravity = Gravity.LEFT or Gravity.TOP
            } ?: run {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
                setMargins(0, 0, 0, 0)
            }
        }
    }

    fun init() {
        Timber.d("init")

        aspectRatioFrameLayout = AspectRatioFrameLayout(context)
        aspectRatioFrameLayout?.resizeMode = resizeMode
        addView(aspectRatioFrameLayout, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))

        videoView = if (useTextureView) TextureView(context) else SurfaceView(context)
        aspectRatioFrameLayout?.addView(videoView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).also { setRectLayoutParams(it) })

        scope = CoroutineScope(Dispatchers.Main)

        systemHandler = Handler()

        trackSelector = DefaultTrackSelector(context)

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        defaultBandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

        loadControl = DefaultLoadControl()

        player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl!!)
            .setBandwidthMeter(defaultBandwidthMeter!!)
            .build().also {
                it.addListener(this)
                it.addAnalyticsListener(this)
                val videoView = videoView
                when (videoView) {
                    is SurfaceView -> it.setVideoSurfaceView(videoView)
                    is TextureView -> it.setVideoTextureView(videoView)
                }
                it.setPlayWhenReady(true)
                it.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                it.addAnalyticsListener(EventLogger())
            }
    }

    fun destroy() {
        Timber.d("destroy")

        scope?.cancel()
        scope = null

        closeStream()

        player?.release()
        player = null
        systemHandler = null
        defaultBandwidthMeter = null
        trackSelector = null
        loadControl = null

        removeAllViews()
        aspectRatioFrameLayout = null
        videoView = null
    }

    var streamUrl: String? = null
        private set

    protected fun closeStream() {
        currentError = null
        mediaSource = null
        streamUrl = null
        player?.stop()
        setSelectedAudioTrack(null, false)
        _audioTracks.value = emptyList()
        outputVideoSize = null
        postPlayerChange()
    }

    protected fun openStream(url: String, vod: Boolean, startPosition: Long? = null, pauseAfter: Boolean? = null) {

        if (url == streamUrl)
            return

        val player = player ?: return

        currentError = null
        mediaSource = null
        streamUrl = null
        player.stop()
        setSelectedAudioTrack(null, false)
        _audioTracks.value = emptyList()
        outputVideoSize = null

        Timber.i("open stream $url")

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
            val langs = ArrayList<String>()
            lastUsedPrefLanguage?.let {
                langs += it
            }
            Locale.getDefault().language.let {
                if (!langs.contains(it))
                    langs += it
            }
            "ru".let {
                if (!langs.contains(it))
                    langs += it
            }
            when (langs.size) {
                0 -> setPreferredAudioLanguages()
                1 -> setPreferredAudioLanguages(langs[0])
                2 -> setPreferredAudioLanguages(langs[0], langs[1])
                3 -> setPreferredAudioLanguages(langs[0], langs[1], langs[2])
            }
        }.build()

        try {
            val uri = Uri.parse(url) ?: return
            val mediaItem: MediaItem = MediaItem.Builder().setUri(uri).build()

            val dataSourceFactory = if (uri.scheme == "udp") {
                DataSource.Factory {
                    UdpDataSource(3000, 10000)
                }
            } else DefaultHttpDataSource.Factory()

            val mediaSourceFactory = if (vod) {
                ProgressiveMediaSource.Factory(dataSourceFactory)
            } else {
                if (uri.lastPathSegment?.endsWith(".mpd") == true) {
                    DashMediaSource.Factory(dataSourceFactory)
                } else if (uri.lastPathSegment?.endsWith(".m3u8") == true) {
                    // https://github.com/google/ExoPlayer/issues/8560
                    val defaultHlsExtractorFactory = DefaultHlsExtractorFactory(
                        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS, true)
                    HlsMediaSource.Factory(dataSourceFactory)
                        .setExtractorFactory(defaultHlsExtractorFactory)
                        .setAllowChunklessPreparation(false)
                } else {
                    val extractorsFactory = DefaultExtractorsFactory()
                    ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                }
            }
            mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        } catch (ex: RuntimeException) {
            mediaSource = null
            onError(ExoPlaybackException.createForUnexpected(ex))
            return
        }

        streamUrl = url

        startPosition?.let {
            player.setMediaSource(mediaSource!!, it)
        } ?: run {
            player.setMediaSource(mediaSource!!)
        }

        pauseAfter?.let {
            player.playWhenReady = !it
        }

        player.prepare()

        postPlayerChange()
    }

    fun postPlayerChange() {
        scope?.launch {
            onPlayerChange()
        }
    }

    open fun onPlayerChange() {
    }

    /**
     * Audio sink error handler (OnTV-main pattern EXACTLY)
     */
    override fun onAudioSinkError(
        eventTime: AnalyticsListener.EventTime,
        audioSinkError: java.lang.Exception
    ) {
        Timber.e("onAudioSinkError handled")
        if (isShouldRestartNowException(audioSinkError)) {
            scope?.launch {
                onError(audioSinkError)
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        scope?.launch {
            onError(error)
        }
    }

    open fun onError(error: Throwable) {
        currentError = error
        Timber.e(error)
    }

    override fun onPlayerStateChanged(eventTime: AnalyticsListener.EventTime, playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == ExoPlayer.STATE_READY) {
            currentError = null
        }
        postPlayerChange()
    }

    var outputVideoSize: VideoSize? = null
        private set

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        Timber.i("onVideoSizeChanged ${videoSize.width}x${videoSize.height}")
        outputVideoSize = videoSize
        if (videoSize.width > 0 && videoSize.height > 0) {
            videoSourceAspect = videoSize.pixelWidthHeightRatio * videoSize.width.toFloat() / videoSize.height.toFloat()
            aspectRatioFrameLayout?.apply {
                setAspectRatio(videoSourceAspect)
                requestLayout()
            }
        }
    }

    /**
     * TrackInfo - OnTV-main pattern EXACTLY
     */
    data class TrackInfo(val trackGroup: TrackGroup, val trackIndex: Int, val name: String, val language: String?) {
        override fun equals(other: Any?): Boolean {
            if (other is TrackInfo) {
                return trackGroup.id == other.trackGroup.id && trackIndex == other.trackIndex
            }
            return super.equals(other)
        }

        override fun hashCode(): Int {
            var result = trackGroup.id.hashCode()
            result = 31 * result + trackIndex
            return result
        }
    }

    private val _audioTracks = MutableStateFlow(emptyList<TrackInfo>())
    val audioTracks = _audioTracks.asStateFlow()

    private val _userSelectedAudioTrack = MutableStateFlow<TrackInfo?>(null)
    val userSelectedAudioTrack = _userSelectedAudioTrack.asStateFlow()

    var lastUsedPrefLanguage: String? = null
        private set

    var lastUserSetAudioTrackTimestamp = 0L
        private set

    /**
     * Set selected audio track (OnTV-main pattern EXACTLY)
     */
    fun setSelectedAudioTrack(track: TrackInfo?, isFromUser: Boolean) {
        player?.trackSelectionParameters = player!!.trackSelectionParameters.buildUpon().apply {
            if (track == null) {
                clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            } else {
                Timber.e("audio track override ${track.name}")
                setOverrideForType(TrackSelectionOverride(track.trackGroup, listOf(track.trackIndex)))
            }
        }.build()
        if (isFromUser) {
            lastUsedPrefLanguage = track?.language
            lastUserSetAudioTrackTimestamp = System.currentTimeMillis()
        }
        _userSelectedAudioTrack.value = track
    }

    private val trackNameProvider = DefaultTrackNameProvider(context.resources)

    override fun onTracksChanged(tracks: Tracks) {
        val list = ArrayList<TrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, trackGroup ->
            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until trackGroup.length) {
                    if (trackGroup.isTrackSupported(trackIndex)) {
                        val trackFormat = trackGroup.getTrackFormat(trackIndex)
                        if (trackFormat.selectionFlags and C.SELECTION_FLAG_FORCED == 0) {
                            val trackName = trackNameProvider.getTrackName(trackFormat)
                            val trackInfo = TrackInfo(trackGroup.mediaTrackGroup, trackIndex, trackName, trackFormat.language)
                            if (trackGroup.isTrackSelected(trackIndex)) {
                                _userSelectedAudioTrack.value = trackInfo
                            }
                            list.add(trackInfo)
                        }
                    }
                }
            }
        }
        _audioTracks.value = list

        var codecslist = ""
        for (tr in audioTracks.value) {
            codecslist += "[${tr.name}]"
        }
        Timber.i("audio tracks $codecslist")
    }

    private fun isShouldRestartNowExceptionType(ex: Throwable?): Boolean {
        return ex is AudioSink.UnexpectedDiscontinuityException ||
                ex is BehindLiveWindowException
    }

    fun isShouldRestartNowException(ex: Throwable?): Boolean {
        if (isShouldRestartNowExceptionType(ex)) return true

        var cause: Throwable? = ex?.cause
        while (cause != null) {
            if (isShouldRestartNowExceptionType(cause)) {
                return true
            }
            cause = cause.cause
        }

        return false
    }

    fun isSourceException(ex: Throwable?): Boolean {
        val e = ex as? ExoPlaybackException ?: return false
        return e.type in setOf(ExoPlaybackException.TYPE_SOURCE, ExoPlaybackException.TYPE_REMOTE)
    }

    fun isRenderError(ex: Throwable?): Boolean {
        val e = ex as? ExoPlaybackException ?: return false
        return e.type == ExoPlaybackException.TYPE_RENDERER
    }

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        val uri = loadEventInfo.uri.toString()
        Timber.i("onLoadStarted $uri")
        super.onLoadStarted(eventTime, loadEventInfo, mediaLoadData)
    }

    fun restartStream() {
        player?.seekToDefaultPosition()
        player?.prepare()
    }

    fun reinit() {
        destroy()
        init()
    }
}
