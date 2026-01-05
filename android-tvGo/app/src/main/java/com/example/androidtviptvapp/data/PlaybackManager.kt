package com.example.androidtviptvapp.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.*
import java.io.File

/**
 * Ultra-optimized PlaybackManager for Android TV boxes:
 * - Aggressive buffering for instant playback start
 * - Disk caching for faster channel switching
 * - Hardware decoder preference with async queueing
 * - Low-latency live stream configuration
 * - HLS-specific optimizations
 * - Intelligent retry mechanism
 */
@OptIn(UnstableApi::class)
object PlaybackManager {
    private var exoPlayer: ExoPlayer? = null
    private var currentUrl: String? = null
    private var retryCount = 0
    private var retryJob: Job? = null
    private var cache: SimpleCache? = null
    private var dataSourceFactory: DataSource.Factory? = null
    private var appContext: Context? = null

    private const val SILENT_RETRIES = 3
    private const val MAX_RETRIES = 5
    private const val RETRY_DELAY_MS = 500L
    private const val DEBOUNCE_DELAY_MS = 200L

    // 20MB cache for low-end TV boxes
    private const val CACHE_SIZE_BYTES = 20L * 1024 * 1024

    var onPlaybackError: ((String, Boolean) -> Unit)? = null
    
    // Debounce job for rapid channel switching
    private var pendingPlayJob: Job? = null

    /**
     * Pre-warm the player for faster first playback
     */
    fun warmUp(context: Context) {
        if (appContext != null && exoPlayer != null) return // Already warmed up
        
        appContext = context.applicationContext
        if (cache == null) {
            val cacheDir = File(context.cacheDir, "media_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES))
        }
        if (dataSourceFactory == null) {
            dataSourceFactory = createCachedDataSourceFactory(context)
        }
        if (exoPlayer == null) {
            exoPlayer = createOptimizedPlayer(context)
            setupErrorListener()
        }
    }

    fun getPlayer(context: Context): ExoPlayer {
        warmUp(context)
        return exoPlayer!!
    }

    private fun createCachedDataSourceFactory(context: Context): DataSource.Factory {
        // Bandwidth meter with high initial estimate for fast startup
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(5_000_000) // Assume 5 Mbps
            .build()

        // HTTP data source with fast timeouts
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setUserAgent("TVGO-Android/1.0 ExoPlayer")
            .setTransferListener(bandwidthMeter)

        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // Add caching layer for faster channel switching
        return if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache!!)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(null) // Read-only for live
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }
    }

    private fun setupErrorListener() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (retryCount > 0) {
                            android.util.Log.d("PlaybackManager", "Recovered after $retryCount retries")
                            retryCount = 0
                            onPlaybackError?.invoke("", false)
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        android.util.Log.d("PlaybackManager", "Buffering...")
                    }
                }
            }
        })
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val errorMessage = getErrorMessage(error)
        android.util.Log.e("PlaybackManager", "Error ($retryCount): $errorMessage", error)

        if (retryCount < MAX_RETRIES) {
            retryCount++
            if (retryCount > SILENT_RETRIES) {
                onPlaybackError?.invoke("Reconnecting...", true)
            }

            retryJob?.cancel()
            retryJob = CoroutineScope(Dispatchers.Main).launch {
                delay(RETRY_DELAY_MS * retryCount)
                currentUrl?.let { url ->
                    exoPlayer?.let { player ->
                        try {
                            player.stop()
                            player.clearMediaItems()
                            val mediaSource = createMediaSource(url)
                            player.setMediaSource(mediaSource)
                            player.prepare()
                            player.playWhenReady = true
                        } catch (e: Exception) {
                            android.util.Log.e("PlaybackManager", "Retry failed", e)
                        }
                    }
                }
            }
        } else {
            retryCount = 0
            onPlaybackError?.invoke(errorMessage, false)
        }
    }

    private fun getErrorMessage(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network error"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Timeout"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                val cause = error.cause
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    "HTTP ${cause.responseCode}"
                } else "Stream unavailable"
            }
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Channel offline"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Decoder error"
            else -> "Playback error"
        }
    }

    /**
     * Create optimized media source based on stream type
     */
    private fun createMediaSource(url: String): MediaSource {
        val uri = Uri.parse(url)
        val factory = dataSourceFactory ?: return DefaultMediaSourceFactory(appContext!!)
            .createMediaSource(MediaItem.fromUri(uri))

        // Use HLS source for .m3u8 streams (most IPTV channels)
        return if (url.contains(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(factory)
                .setAllowChunklessPreparation(true) // Faster startup
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(3000)
                                .setMinOffsetMs(2000)
                                .setMaxOffsetMs(10000)
                                .setMinPlaybackSpeed(0.97f)
                                .setMaxPlaybackSpeed(1.03f)
                                .build()
                        )
                        .build()
                )
        } else {
            DefaultMediaSourceFactory(factory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(3000)
                                .setMinPlaybackSpeed(0.97f)
                                .setMaxPlaybackSpeed(1.03f)
                                .build()
                        )
                        .build()
                )
        }
    }

    fun playUrl(context: Context, url: String) {
        warmUp(context)
        val player = exoPlayer!!

        // Skip reload if same URL is already playing
        if (currentUrl == url && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
            return
        }

        pendingPlayJob?.cancel()
        retryCount = 0
        retryJob?.cancel()
        currentUrl = url

        try {
            player.stop()
            player.clearMediaItems()
            val mediaSource = createMediaSource(url)
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            onPlaybackError?.invoke("Failed to load", false)
        }
    }
    
    /**
     * Debounced playUrl for channel previews - waits before starting playback
     * Prevents stream thrashing when rapidly navigating through channels
     */
    fun playUrlDebounced(context: Context, url: String) {
        pendingPlayJob?.cancel()
        pendingPlayJob = CoroutineScope(Dispatchers.Main).launch {
            delay(DEBOUNCE_DELAY_MS)
            playUrl(context, url)
        }
    }

    fun retry(context: Context) {
        currentUrl?.let { url ->
            retryCount = 0
            currentUrl = null
            playUrl(context, url)
        }
    }

    fun release() {
        retryJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        currentUrl = null
        retryCount = 0
    }

    fun releaseAll() {
        release()
        try {
            cache?.release()
        } catch (e: Exception) {
            android.util.Log.e("PlaybackManager", "Cache release error", e)
        }
        cache = null
        dataSourceFactory = null
        appContext = null
    }

    private fun createOptimizedPlayer(context: Context): ExoPlayer {
        // ULTRA-AGGRESSIVE buffering for instant startup
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,    // Min buffer: 0.5s (start playing ASAP)
                15000,  // Max buffer: 15s (don't waste memory)
                250,    // Playback start buffer: 0.25s (instant start)
                500     // Rebuffer threshold: 0.5s
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(5000, true) // 5s back buffer
            .build()

        // Hardware decoders with async queueing for better performance
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            .forceEnableMediaCodecAsynchronousQueueing()

        // Bandwidth meter with high initial estimate
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(5_000_000)
            .build()

        // Track selector optimized for TV
        val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory()).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setForceHighestSupportedBitrate(false)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setMaxAudioChannelCount(6)
                    .setViewportSizeToPhysicalDisplaySize(context, true)
            )
        }

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory ?: DefaultDataSource.Factory(context))
            )
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setHandleAudioBecomingNoisy(false)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
}
