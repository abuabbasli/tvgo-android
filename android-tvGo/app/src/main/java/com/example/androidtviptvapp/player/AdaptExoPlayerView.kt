package com.example.androidtviptvapp.player

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
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
import java.util.Locale

/**
 * AdaptExoPlayerView - View-based ExoPlayer following OnTV-main pattern exactly.
 * Implements Player.Listener AND AnalyticsListener for full event handling.
 * Supports UDP, HLS, DASH, and Progressive streams.
 */
open class AdaptExoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), Player.Listener, AnalyticsListener {

    companion object {
        private const val TAG = "AdaptExoPlayerView"
        const val RESIZE_MODE_FIT = AspectRatioFrameLayout.RESIZE_MODE_FIT
        const val RESIZE_MODE_FILL = AspectRatioFrameLayout.RESIZE_MODE_FILL
        const val RESIZE_MODE_ZOOM = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        
        // Setting to toggle TextureView vs SurfaceView
        var useTextureView: Boolean = false
    }

    // Player state properties (matching OnTV-main)
    open var pause: Boolean
        get() = player?.playWhenReady == false
        set(v) {
            player?.playWhenReady = !v
        }

    var seek: Long
        get() = player?.currentPosition ?: 0
        set(v) {
            player?.seekTo(v)
        }

    val liveOffset: Long?
        get() = player?.currentLiveOffset?.takeIf { it != C.TIME_UNSET }

    val duration: Long
        get() = player?.duration ?: 0

    val isPlayReady: Boolean
        get() = mediaSource != null && player?.playbackState == Player.STATE_READY

    val isBuffering: Boolean
        get() = player?.playbackState == Player.STATE_BUFFERING

    val isPlaybackEnded: Boolean
        get() = player?.playbackState == Player.STATE_ENDED

    val isInitialized: Boolean
        get() = player != null

    val totalBufferedDuration: Long
        get() = player?.totalBufferedDuration ?: 0

    var currentError: Throwable? = null
        protected set

    // Video components
    private var aspectRatioFrameLayout: AspectRatioFrameLayout? = null
    private var videoView: View? = null
    protected var player: ExoPlayer? = null
    private var mediaSource: MediaSource? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private var loadControl: DefaultLoadControl? = null

    var scope: CoroutineScope? = null
        protected set

    // Stream URL tracking
    var streamUrl: String? = null
        private set

    // Resize mode
    var resizeMode: Int = RESIZE_MODE_FILL
        set(v) {
            field = v
            aspectRatioFrameLayout?.resizeMode = v
        }

    /**
     * outputRect - OnTV-main pattern for preview window
     * When set to a Rect, constrains the player to that specific area
     * When null, player goes fullscreen (MATCH_PARENT)
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

    // Audio tracks (OnTV-main pattern)
    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val audioTracks = _audioTracks.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val selectedAudioTrack = _selectedAudioTrack.asStateFlow()
    
    // Last used audio language preference
    var lastUsedPrefLanguage: String? = null

    data class AudioTrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val trackGroup: TrackGroup,
        val name: String,
        val language: String?
    )

    /**
     * Initialize the player view - must call this before using
     */
    fun init() {
        Log.d(TAG, "init")

        // Create aspect ratio container
        aspectRatioFrameLayout = AspectRatioFrameLayout(context).apply {
            resizeMode = this@AdaptExoPlayerView.resizeMode
        }
        addView(aspectRatioFrameLayout, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        // Create video surface view (OnTV-main: can switch between SurfaceView/TextureView)
        videoView = if (useTextureView) TextureView(context) else SurfaceView(context)
        aspectRatioFrameLayout?.addView(videoView, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        scope = CoroutineScope(Dispatchers.Main)

        // Track selector (OnTV-main default)
        trackSelector = DefaultTrackSelector(context)

        // Renderers with extension mode (OnTV-main pattern)
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        // Bandwidth meter
        bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

        // Default load control (OnTV-main uses defaults)
        loadControl = DefaultLoadControl()

        // Build player (OnTV-main exact pattern)
        player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl!!)
            .setBandwidthMeter(bandwidthMeter!!)
            .build().apply {
                addListener(this@AdaptExoPlayerView)
                addAnalyticsListener(this@AdaptExoPlayerView)
                // Add EventLogger for debugging (OnTV-main pattern)
                addAnalyticsListener(EventLogger())
                
                // Set video surface
                when (val view = videoView) {
                    is SurfaceView -> setVideoSurfaceView(view)
                    is TextureView -> setVideoTextureView(view)
                }
                
                playWhenReady = true
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
    }

    /**
     * Refresh output view - switch between SurfaceView and TextureView
     */
    fun refreshOutputView() {
        val aspectRatioFrameLayout = aspectRatioFrameLayout ?: return
        val newVideoView: View = if (useTextureView) {
            if (videoView is TextureView) return
            TextureView(context)
        } else {
            if (videoView is SurfaceView) return
            SurfaceView(context)
        }
        
        Log.d(TAG, "Switching output surface to ${newVideoView.javaClass.simpleName}")
        aspectRatioFrameLayout.addView(newVideoView, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        
        when (newVideoView) {
            is SurfaceView -> player?.setVideoSurfaceView(newVideoView)
            is TextureView -> player?.setVideoTextureView(newVideoView)
        }
        
        aspectRatioFrameLayout.removeView(videoView)
        videoView = newVideoView
    }

    /**
     * Destroy player and clean up resources
     */
    open fun destroy() {
        Log.d(TAG, "destroy")

        scope?.cancel()
        scope = null

        closeStream()

        player?.release()
        player = null
        trackSelector = null
        loadControl = null
        bandwidthMeter = null

        removeAllViews()
        aspectRatioFrameLayout = null
        videoView = null
    }

    /**
     * Close current stream - OnTV-main pattern
     */
    protected fun closeStream() {
        currentError = null
        mediaSource = null
        streamUrl = null
        isCurrentStreamVod = false
        player?.stop()
        _audioTracks.value = emptyList()
        _selectedAudioTrack.value = null
        postPlayerChange()
    }

    /**
     * Open and play a stream - OnTV-main exact pattern with UDP support
     */
    protected fun openStream(url: String, vod: Boolean, startPosition: Long? = null, pauseAfter: Boolean? = null) {
        if (url == streamUrl) return

        val player = player ?: return

        currentError = null
        mediaSource = null
        streamUrl = null
        player.stop()
        _audioTracks.value = emptyList()
        _selectedAudioTrack.value = null

        Log.d(TAG, "open stream $url")

        // Set preferred audio languages (OnTV-main pattern)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
            val langs = ArrayList<String>()
            lastUsedPrefLanguage?.let { langs += it }
            Locale.getDefault().language.let { if (!langs.contains(it)) langs += it }
            "en".let { if (!langs.contains(it)) langs += it }
            
            when (langs.size) {
                0 -> setPreferredAudioLanguages()
                1 -> setPreferredAudioLanguages(langs[0])
                2 -> setPreferredAudioLanguages(langs[0], langs[1])
                else -> setPreferredAudioLanguages(langs[0], langs[1], langs[2])
            }
        }.build()

        try {
            val uri = Uri.parse(url) ?: return
            val mediaItem = MediaItem.Builder().setUri(uri).build()

            // Data source factory - UDP support (OnTV-main pattern)
            val dataSourceFactory: DataSource.Factory = if (uri.scheme == "udp") {
                Log.d(TAG, "Using UDP DataSource for multicast stream")
                DataSource.Factory { UdpDataSource(3000, 10000) }
            } else {
                DefaultHttpDataSource.Factory()
            }

            // Create media source based on stream type (OnTV-main exact logic)
            val mediaSourceFactory = if (vod) {
                ProgressiveMediaSource.Factory(dataSourceFactory)
            } else {
                when {
                    uri.lastPathSegment?.endsWith(".mpd") == true -> {
                        DashMediaSource.Factory(dataSourceFactory)
                    }
                    uri.lastPathSegment?.endsWith(".m3u8") == true -> {
                        // HLS with special TS flags (OnTV-main exact pattern)
                        val hlsExtractorFactory = DefaultHlsExtractorFactory(
                            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or 
                            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
                            true
                        )
                        HlsMediaSource.Factory(dataSourceFactory)
                            .setExtractorFactory(hlsExtractorFactory)
                            .setAllowChunklessPreparation(false)
                    }
                    else -> {
                        val extractorsFactory = DefaultExtractorsFactory()
                        ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    }
                }
            }

            mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            streamUrl = url
            isCurrentStreamVod = vod

            if (startPosition != null) {
                player.setMediaSource(mediaSource!!, startPosition)
            } else {
                player.setMediaSource(mediaSource!!)
            }

            pauseAfter?.let {
                player.playWhenReady = !it
            }

            player.prepare()
            postPlayerChange()

        } catch (ex: RuntimeException) {
            Log.e(TAG, "Error opening stream", ex)
            mediaSource = null
            currentError = ex
            onError(ex)
        }
    }

    // =========================================================================
    // Player.Listener callbacks (OnTV-main pattern)
    // =========================================================================

    override fun onPlayerError(error: PlaybackException) {
        scope?.launch {
            onError(error)
        }
    }

    open fun onError(error: Throwable) {
        currentError = error
        Log.e(TAG, "PlayerError ${error.javaClass.simpleName}: ${error.message}")
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            currentError = null
        }
        postPlayerChange()
    }

    /**
     * Post player state change to UI thread - OnTV-main pattern
     * This ensures UI updates happen correctly and avoids race conditions
     */
    fun postPlayerChange() {
        scope?.launch {
            onPlayerChange()
        }
    }

    open fun onPlayerChange() {
        // Override in subclass
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        Log.d(TAG, "onVideoSizeChanged ${videoSize.width}x${videoSize.height}")
        if (videoSize.width > 0 && videoSize.height > 0) {
            val aspect = videoSize.pixelWidthHeightRatio * videoSize.width.toFloat() / videoSize.height.toFloat()
            aspectRatioFrameLayout?.setAspectRatio(aspect)
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        updateAudioTracks(tracks)
    }

    private fun updateAudioTracks(tracks: Tracks) {
        val list = mutableListOf<AudioTrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, trackGroup ->
            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until trackGroup.length) {
                    if (trackGroup.isTrackSupported(trackIndex)) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        if (format.selectionFlags and C.SELECTION_FLAG_FORCED == 0) {
                            val trackInfo = AudioTrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                trackGroup = trackGroup.mediaTrackGroup,
                                name = format.label ?: "Track ${trackIndex + 1}",
                                language = format.language
                            )
                            if (trackGroup.isTrackSelected(trackIndex)) {
                                _selectedAudioTrack.value = trackInfo
                            }
                            list.add(trackInfo)
                        }
                    }
                }
            }
        }
        _audioTracks.value = list
        Log.d(TAG, "Audio tracks: ${list.map { "[${it.name}]" }.joinToString("")}")
    }

    /**
     * Select audio track (OnTV-main pattern)
     */
    fun selectAudioTrack(trackInfo: AudioTrackInfo) {
        val player = player ?: return
        
        lastUsedPrefLanguage = trackInfo.language
        _selectedAudioTrack.value = trackInfo
        
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(trackInfo.trackGroup, trackInfo.trackIndex))
            .build()
        
        Log.d(TAG, "Selected audio track: ${trackInfo.name}")
    }

    // =========================================================================
    // AnalyticsListener callbacks (OnTV-main pattern - for audio sink errors)
    // =========================================================================

    override fun onAudioSinkError(
        eventTime: AnalyticsListener.EventTime,
        audioSinkError: Exception
    ) {
        Log.e(TAG, "onAudioSinkError: ${audioSinkError.message}")
        // Audio sink errors often need instant restart
        if (isShouldRestartNowException(audioSinkError)) {
            scope?.launch {
                onError(audioSinkError)
            }
        }
    }

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        Log.d(TAG, "onLoadStarted: ${loadEventInfo.uri}")
    }

    // =========================================================================
    // Error recovery helpers (OnTV-main exact pattern with ExoPlaybackException)
    // =========================================================================

    private fun isShouldRestartNowExceptionType(ex: Throwable?): Boolean {
        if (ex == null) return false
        // Direct type check like OnTV-main
        if (ex is BehindLiveWindowException) return true
        // Check class name for audio sink errors
        val className = ex.javaClass.simpleName
        return className.contains("UnexpectedDiscontinuityException") ||
               className.contains("AudioSink")
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

    /**
     * Check if error is a source/network error (OnTV-main uses ExoPlaybackException.type)
     */
    fun isSourceException(ex: Throwable?): Boolean {
        // First try ExoPlaybackException.type (more precise, OnTV-main style)
        val exo = ex as? ExoPlaybackException
        if (exo != null) {
            return exo.type == ExoPlaybackException.TYPE_SOURCE || 
                   exo.type == ExoPlaybackException.TYPE_REMOTE
        }
        
        // Fallback to PlaybackException.errorCode
        val e = ex as? PlaybackException ?: return false
        return e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
               e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
               e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
               e.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
               e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    /**
     * Check if error is a render/decoder error (OnTV-main uses ExoPlaybackException.type)
     */
    fun isRenderError(ex: Throwable?): Boolean {
        // First try ExoPlaybackException.type (more precise, OnTV-main style)
        val exo = ex as? ExoPlaybackException
        if (exo != null) {
            return exo.type == ExoPlaybackException.TYPE_RENDERER
        }
        
        // Fallback to PlaybackException.errorCode
        val e = ex as? PlaybackException ?: return false
        return e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
               e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
               e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
               e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
    }

    /**
     * Restart stream at live position
     */
    fun restartStream() {
        Log.d(TAG, "restartStream - seeking to default position and preparing")
        currentError = null
        player?.seekToDefaultPosition()
        player?.prepare()
    }

    /**
     * Reinitialize player completely - OnTV-main pattern
     * Saves current stream state, destroys player, reinits, and restores playback
     */
    open fun reinit() {
        Log.d(TAG, "reinit - full player reinitialization")
        val savedUrl = streamUrl
        val savedVod = isCurrentStreamVod
        val savedPosition = seek
        destroy()
        init()
        savedUrl?.let { url ->
            Log.d(TAG, "reinit - restoring stream: $url at position $savedPosition")
            openStream(url, savedVod, if (savedVod && savedPosition > 0) savedPosition else null)
        }
    }

    // Track if current stream is VOD (for reinit restoration)
    protected var isCurrentStreamVod: Boolean = false
        private set

    /**
     * Get the player instance
     */
    fun exoPlayer(): ExoPlayer? = player
}
