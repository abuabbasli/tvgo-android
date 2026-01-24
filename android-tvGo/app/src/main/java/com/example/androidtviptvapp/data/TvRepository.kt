package com.example.androidtviptvapp.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.androidtviptvapp.data.api.ApiClient
import com.example.androidtviptvapp.data.api.BrandConfig
import com.example.androidtviptvapp.data.api.CurrentProgram
import com.example.androidtviptvapp.data.api.FeaturesConfig
import com.example.androidtviptvapp.data.api.LoginRequest
import com.example.androidtviptvapp.data.api.ScheduleProgramItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import com.example.androidtviptvapp.ui.screens.BabyLockManager

/**
 * Optimized TvRepository with:
 * - Proper coroutine scope management (no leaks)
 * - Background loading with loading states
 * - Schedule caching to reduce API calls
 * - Efficient memory usage
 * - No main thread blocking
 */
object TvRepository {
    // Managed coroutine scope - tied to app lifecycle
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Loading states for UI feedback
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow("")
    val loadingProgress: StateFlow<String> = _loadingProgress.asStateFlow()

    private val _isDataReady = MutableStateFlow(false)
    val isDataReady: StateFlow<Boolean> = _isDataReady.asStateFlow()

    // Auth & Config State
    var isAuthenticated by mutableStateOf(false)
    var authToken by mutableStateOf<String?>(null)
    var appConfig by mutableStateOf<BrandConfig?>(null)
    var features by mutableStateOf<FeaturesConfig?>(null)

    // Data State - using Compose snapshot-aware lists
    val channels = mutableStateListOf<Channel>()
    val movies = mutableStateListOf<Movie>()

    // Current programs for each channel (channelId -> current program)
    val currentPrograms = mutableStateMapOf<String, CurrentProgram>()

    // Schedule cache to reduce API calls (channelId -> cached schedule with timestamp)
    private val scheduleCache = mutableMapOf<String, CachedSchedule>()

    data class CachedSchedule(
        val programs: List<ScheduleProgramItem>,
        val timestamp: Long
    )
    
    // =========================================================================
    // LAZY EPG LOADING (OnTV-main Pattern)
    // Only fetch program schedules when a channel is actually viewed
    // =========================================================================
    
    private const val TAG = "TvRepository"
    private const val SCHEDULE_CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    private const val EPG_RELOAD_MIN_INTERVAL_MS = 20 * 60 * 1000L // 20 minutes
    
    /**
     * Lazy EPG loading per channel - only fetches programs when needed.
     * Based on OnTV-main's ChannelsCache.ChannelPrograms pattern.
     */
    class ChannelPrograms(private val channelId: String) {
        private val _programs = MutableStateFlow<List<ScheduleProgramItem>>(emptyList())
        val programs: StateFlow<List<ScheduleProgramItem>> = _programs.asStateFlow()
        
        private var loadJob: Job? = null
        var lastLoadTime = 0L
            private set
        val isLoaded: Boolean get() = lastLoadTime > 0
        
        /**
         * Trigger a reload if stale or never loaded. Returns current programs flow.
         * Uses retry mechanism (OnTV-main pattern)
         */
        fun maybeNeedReload(): StateFlow<List<ScheduleProgramItem>> {
            val now = System.currentTimeMillis()
            if (loadJob?.isActive != true &&
                lastLoadTime + EPG_RELOAD_MIN_INTERVAL_MS < now) {
                loadJob = repositoryScope.launch {
                    try {
                        android.util.Log.d(TAG, "Loading EPG for channel $channelId")
                        // Use standard retry for EPG (OnTV-main pattern)
                        val response = ApiClient.retry { ApiClient.service.getChannelSchedule(channelId) }
                        _programs.value = response.programs
                        lastLoadTime = System.currentTimeMillis()
                        android.util.Log.d(TAG, "Loaded ${response.programs.size} programs for $channelId")
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Coroutine cancelled - not an error, just log debug
                        android.util.Log.d(TAG, "EPG load cancelled for $channelId")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to load EPG for $channelId: ${e.message}")
                    }
                    loadJob = null
                }
            }
            return programs
        }
        
        /**
         * Mark this channel's programs as needing a refresh
         */
        fun markNeedsReload() {
            lastLoadTime = 0L
        }
    }
    
    // Map of channel-specific EPG loaders (lazy initialization)
    private val channelProgramsMap = mutableMapOf<String, ChannelPrograms>()
    
    /**
     * Get or create lazy EPG loader for a channel. Triggers background load if stale.
     * Use this instead of getChannelSchedule for reactive UI updates.
     */
    fun triggerUpdatePrograms(channelId: String): StateFlow<List<ScheduleProgramItem>> {
        return channelProgramsMap.getOrPut(channelId) { ChannelPrograms(channelId) }
            .maybeNeedReload()
    }
    
    /**
     * Get cached programs if available (no API call)
     */
    fun getCachedPrograms(channelId: String): List<ScheduleProgramItem>? {
        return channelProgramsMap[channelId]?.programs?.value?.takeIf { it.isNotEmpty() }
    }
    
    /**
     * Mark all channel programs as needing refresh (e.g., after subscription update)
     */
    fun markAllProgramsNeedReload() {
        channelProgramsMap.values.forEach { it.markNeedsReload() }
    }
    
    // =========================================================================
    // LOOPED CHANNEL NAVIGATION (OnTV-main Pattern)
    // =========================================================================
    
    /**
     * Get next/previous channel with looping support.
     * @param currentId Current channel ID
     * @param direction +1 for next, -1 for previous
     * @param category Optional category filter (null or "all" = all channels)
     */
    fun getNextChannel(currentId: String, direction: Int, category: String? = null): Channel? {
        val list = if (category == null || category == "all") {
            channels.toList()
        } else {
            channels.filter { it.category == category }
        }
        if (list.isEmpty()) return null
        
        val currentIndex = list.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return list.firstOrNull()
        
        val newIndex = (currentIndex + direction + list.size) % list.size
        return list.getOrNull(newIndex)?.takeIf { it.id != currentId }
    }
    
    /**
     * Get channel by ID
     */
    fun getChannel(channelId: String): Channel? {
        return channels.find { it.id == channelId }
    }

    /**
     * Get channel by order number (for remote number input)
     */
    fun getChannelByOrder(order: Int): Channel? {
        return channels.find { it.order == order }
    }

    // =========================================================================
    // MEMORY-AWARE CLEANUP (TV Box Optimization)
    // =========================================================================
    
    /**
     * Check memory usage and cleanup caches if too high.
     * Call this periodically (e.g., every 60 seconds) on TV boxes.
     */
    fun checkMemoryAndCleanup() {
        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        val usagePercent = (usedMem.toFloat() / maxMem * 100).toInt()
        
        android.util.Log.d(TAG, "Memory: ${usedMem/1024/1024}MB / ${maxMem/1024/1024}MB ($usagePercent%)")
        
        if (usagePercent > 80) {
            android.util.Log.w(TAG, "High memory usage ($usagePercent%) - clearing caches")
            imageLoader?.memoryCache?.clear()
            scheduleCache.clear()
            // Keep channel programs but clear the data
            channelProgramsMap.values.forEach { it.markNeedsReload() }
        }
    }
    
    // Movie playback positions (movieId -> position in ms)
    private val moviePositions = mutableMapOf<String, Long>()
    
    /**
     * Save movie playback position for resume
     */
    fun saveMoviePosition(movieId: String, positionMs: Long) {
        if (positionMs > 0) {
            moviePositions[movieId] = positionMs
            android.util.Log.d("TvRepository", "Saved position for movie $movieId: ${positionMs/1000}s")
        }
    }
    
    /**
     * Get saved movie position
     */
    fun getMoviePosition(movieId: String): Long {
        return moviePositions[movieId] ?: 0L
    }

    // =========================================================================
    // CHANNEL HISTORY (OnTV-main Pattern)
    // =========================================================================

    private val channelHistory = mutableListOf<String>()
    private const val MAX_HISTORY_SIZE = 50

    /**
     * Add channel to viewing history (OnTV-main pattern)
     */
    fun addChannelToHistory(channel: Channel) {
        // Remove if already exists (to move to front)
        channelHistory.remove(channel.id)
        // Add to front
        channelHistory.add(0, channel.id)
        // Trim to max size
        while (channelHistory.size > MAX_HISTORY_SIZE) {
            channelHistory.removeAt(channelHistory.size - 1)
        }
        android.util.Log.d(TAG, "Added channel ${channel.name} to history")
    }

    /**
     * Get recent channel history
     */
    fun getChannelHistory(): List<Channel> {
        return channelHistory.mapNotNull { id -> channels.find { it.id == id } }
    }

    // Dynamic Categories - populated from loaded channels
    val channelCategories = mutableStateListOf<Category>()

    // Movie categories (static for now)
    val movieCategories = listOf(
        Category("all", "All Movies"),
        Category("action", "Action"),
        Category("comedy", "Comedy"),
        Category("drama", "Drama"),
        Category("kids", "Kids & Family"),
        Category("scifi", "Sci-Fi"),
        Category("thriller", "Thriller"),
        Category("horror", "Horror"),
        Category("romance", "Romance")
    )

    // Optimized Coil ImageLoader singleton
    private var imageLoader: ImageLoader? = null

    /**
     * Get or create the optimized ImageLoader instance
     */
    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: createOptimizedImageLoader(context).also { imageLoader = it }
    }

    private fun createOptimizedImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15) // 15% - leave more RAM for player on TV boxes
                    .strongReferencesEnabled(true) // Keep strong refs for TV scrolling
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB - TV boxes have limited storage
                    .build()
            }
            .okHttpClient { getUnsafeOkHttpClient() }
            .crossfade(200) // Smooth transitions
            .respectCacheHeaders(false) // Force caching regardless of server headers
            .components {
                add(coil.decode.SvgDecoder.Factory()) // Add SVG support
            }
            .logger(object : coil.util.Logger {
                override var level: Int = android.util.Log.DEBUG
                override fun log(tag: String, priority: Int, message: String?, throwable: Throwable?) {
                    when (priority) {
                        android.util.Log.ERROR -> android.util.Log.e("CoilLoader", message ?: "", throwable)
                        android.util.Log.WARN -> android.util.Log.w("CoilLoader", message ?: "")
                        else -> android.util.Log.d("CoilLoader", message ?: "")
                    }
                }
            })
            .build()
    }
    
    /**
     * Preload channel logos for faster display when scrolling
     */
    fun preloadChannelLogos(context: Context) {
        val loader = getImageLoader(context)
        repositoryScope.launch {
            val logosToPreload = channels.take(50).mapNotNull { it.logo.takeIf { url -> url.isNotEmpty() } }
            android.util.Log.d("TvRepository", "Preloading ${logosToPreload.size} channel logos")
            
            logosToPreload.forEach { logoUrl ->
                try {
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(logoUrl)
                        .memoryCacheKey(logoUrl)
                        .diskCacheKey(logoUrl)
                        .size(120, 120) // Standard tile size
                        .build()
                    loader.enqueue(request)
                } catch (e: Exception) {
                    // Ignore preload errors
                }
            }
        }
    }

    /**
     * Creates an OkHttpClient that trusts all certificates.
     * Use only for legacy devices where root certs are outdated.
     */
    private fun getUnsafeOkHttpClient(): okhttp3.OkHttpClient {
        return try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            // Install the all-trusting trust manager
            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            android.util.Log.e("TvRepository", "Error creating unsafe client", e)
            okhttp3.OkHttpClient.Builder().build()
        }
    }

    /**
     * Initialize app - called from Application class or MainActivity
     * Runs entirely in background, doesn't block UI
     */
    
    // Auto-login disabled - user must enter credentials manually
    private const val AUTO_LOGIN_ENABLED = false
    
    fun loadData(context: Context? = null) {
        repositoryScope.launch {
            _isLoading.value = true
            _loadingProgress.value = "Initializing..."

            try {
                // Load config first (fast)
                _loadingProgress.value = "Loading configuration..."
                loadPublicConfigAsync()

                // Load channels and movies in parallel
                _loadingProgress.value = "Loading content..."
                val channelsDeferred = async { loadChannelsAsync() }
                val moviesDeferred = async { loadMoviesAsync() }

                // Wait for both to complete
                channelsDeferred.await()
                moviesDeferred.await()

                // Load current programs after channels are ready
                _loadingProgress.value = "Loading program guide..."
                loadCurrentProgramsAsync()

                _isDataReady.value = true
                _loadingProgress.value = "Ready"

            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Error during data load: ${e.message}", e)
                _loadingProgress.value = "Error loading data"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadPublicConfigAsync() {
        try {
            android.util.Log.d("TvRepository", "Loading public config...")
            val config = ApiClient.service.getPublicConfig()
            withContext(Dispatchers.Main) {
                appConfig = config.brand
                features = config.features
            }
            android.util.Log.d("TvRepository", "Config loaded: appName=${config.brand.appName}")
        } catch (e: Exception) {
            android.util.Log.e("TvRepository", "Failed to load config: ${e.message}")
        }
    }

    /**
     * Login with subscriber credentials
     * @param context Android context to get device ID
     * Note: Data loading happens separately via loadData() after navigation
     */
    suspend fun login(username: String, pass: String, context: Context? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get device ID (use Android ID as MAC substitute for TV boxes)
                val deviceId = context?.let {
                    try {
                        android.provider.Settings.Secure.getString(
                            it.contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: "00:00:00:00:00:00"
                    } catch (e: Exception) {
                        "00:00:00:00:00:00"
                    }
                } ?: "00:00:00:00:00:00"

                val deviceName = android.os.Build.MODEL ?: "Android TV"

                android.util.Log.d("TvRepository", "Login attempt: user=$username, deviceId=$deviceId, deviceName=$deviceName")
                android.util.Log.d("TvRepository", "Using API BASE_URL: ${AppConfig.BASE_URL}")

                val response = ApiClient.service.login(
                    LoginRequest(
                        username = username,
                        password = pass,
                        macAddress = deviceId,
                        deviceName = deviceName
                    )
                )

                withContext(Dispatchers.Main) {
                    authToken = response.accessToken
                    isAuthenticated = true

                    if (response.config != null) {
                        appConfig = response.config.brand
                        features = response.config.features
                    }
                }

                android.util.Log.d("TvRepository", "Login successful - token saved, data will load via loadData()")

                // Check if admin has flagged baby lock for reset
                try {
                    BabyLockManager.checkAndApplyServerReset()
                } catch (e: Exception) {
                    android.util.Log.e("TvRepository", "Error checking baby lock reset: ${e.message}")
                }

                true
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Login failed: ${e.message}", e)
                false
            }
        }
    }

    private fun resolveUrl(path: String?): String {
        if (path.isNullOrEmpty()) {
            android.util.Log.w("TvRepository", "resolveUrl: path is null or empty")
            return ""
        }

        val baseUrl = AppConfig.IMAGE_BASE_URL
        var url = path

        // If it's a relative path, prepend base URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
        }

        // Replace localhost/local IPs with Lambda host (same as stream URLs)
        val lambdaHost = "hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws"
        url = url
            .replace("localhost", lambdaHost)
            .replace("127.0.0.1", lambdaHost)
            .replace("0.0.0.0", lambdaHost)

        // Handle common local network patterns
        val localIpPattern = Regex("192\\.168\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+")
        url = localIpPattern.replace(url, lambdaHost)

        android.util.Log.d("TvRepository", "resolveUrl: '$path' -> '$url'")
        return url
    }

    /**
     * Resolve stream URLs - handles localhost/local IPs that need to be replaced
     */
    private fun resolveStreamUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""

        var streamUrl = url

        // If it's a relative path, prepend base URL
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            val baseUrl = AppConfig.IMAGE_BASE_URL
            streamUrl = if (streamUrl.startsWith("/")) "$baseUrl$streamUrl" else "$baseUrl/$streamUrl"
        }

        // Replace localhost/local IPs with the Lambda URL host
        val lambdaHost = "hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws"
        streamUrl = streamUrl
            .replace("localhost", lambdaHost)
            .replace("127.0.0.1", lambdaHost)
            .replace("0.0.0.0", lambdaHost)

        // Also handle common local network patterns
        val localIpPattern = Regex("192\\.168\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+")
        streamUrl = localIpPattern.replace(streamUrl, lambdaHost)

        android.util.Log.d("TvRepository", "Resolved stream URL: $streamUrl")
        return streamUrl
    }

    private suspend fun loadChannelsAsync() {
        try {
            android.util.Log.d("TvRepository", "Loading channels from API...")
            // Use bigRetry for channel list - critical operation (OnTV-main pattern)
            val response = ApiClient.bigRetry { ApiClient.service.getChannels() }
            android.util.Log.d("TvRepository", "Got ${response.items.size} channels from API")

            val domainChannels = response.items.mapIndexed { index, dto ->
                Channel(
                    id = dto.id,
                    name = dto.name,
                    logo = resolveUrl(dto.logo),
                    category = dto.category ?: dto.group ?: "all",
                    streamUrl = resolveStreamUrl(dto.streamUrl),
                    description = dto.description ?: "",
                    logoColor = dto.logoColor ?: "#000000",
                    order = dto.order ?: (index + 1),  // Use backend order or fallback to index
                    schedule = emptyList()
                )
            }

            // Extract unique categories from channels
            val uniqueCategories = domainChannels
                .map { it.category }
                .filter { it.isNotBlank() && it != "all" }
                .distinct()
                .sorted()
                .map { Category(it, it) }

            withContext(Dispatchers.Main) {
                channels.clear()
                channels.addAll(domainChannels)

                channelCategories.clear()
                channelCategories.add(Category("all", "All Channels"))
                channelCategories.addAll(uniqueCategories)

                android.util.Log.d("TvRepository", "Loaded ${channels.size} channels and ${channelCategories.size} categories")
                android.util.Log.d("TvRepository", "Channel categories: ${uniqueCategories.map { it.id }}")
            }
        } catch (e: Exception) {
            android.util.Log.e("TvRepository", "Error loading channels", e)
        }
    }

    private suspend fun loadCurrentProgramsAsync() {
        try {
            android.util.Log.d("TvRepository", "Loading current programs...")
            val response = ApiClient.service.getCurrentPrograms()
            android.util.Log.d("TvRepository", "Got ${response.programs.size} current programs")

            withContext(Dispatchers.Main) {
                currentPrograms.clear()
                currentPrograms.putAll(response.programs)
            }
        } catch (e: Exception) {
            android.util.Log.e("TvRepository", "Error loading current programs: ${e.message}")
        }
    }

    /**
     * Get cached schedule for a channel, or fetch if not cached/expired
     */
    suspend fun getChannelSchedule(channelId: String): List<ScheduleProgramItem> {
        val cached = scheduleCache[channelId]
        val now = System.currentTimeMillis()

        // Return cached if valid (5 minutes cache)
        val scheduleCacheDurationMs = 5 * 60 * 1000L
        if (cached != null && (now - cached.timestamp) < scheduleCacheDurationMs) {
            return cached.programs
        }

        // Fetch new schedule
        return withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.service.getChannelSchedule(channelId)
                scheduleCache[channelId] = CachedSchedule(response.programs, now)
                response.programs
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Coroutine was cancelled (user navigated away) - not an error
                throw e // Re-throw to properly propagate cancellation
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Failed to load schedule for $channelId: ${e.message}")
                cached?.programs ?: emptyList()
            }
        }
    }

    private suspend fun loadMoviesAsync() {
        try {
            android.util.Log.d("TvRepository", "=== LOADING MOVIES ===")
            // Use bigRetry for movies list - critical operation (OnTV-main pattern)
            val response = ApiClient.bigRetry { ApiClient.service.getMovies() }
            android.util.Log.d("TvRepository", "Got ${response.total} movies from API")

            val domainMovies = response.items.map { item ->
                // Debug: Log image URLs
                val posterUrl = item.images?.poster
                val resolvedThumbnail = resolveUrl(posterUrl)
                android.util.Log.d("TvRepository", "Movie: ${item.title}, poster: $posterUrl -> $resolvedThumbnail")

                Movie(
                    id = item.id,
                    title = item.title,
                    thumbnail = resolvedThumbnail,
                    backdrop = resolveUrl(item.images?.landscape ?: item.images?.hero),
                    category = item.genres?.firstOrNull()?.lowercase() ?: "all",
                    year = item.year ?: 2024,
                    rating = item.rating?.toString() ?: "0.0",
                    description = item.synopsis ?: "",
                    videoUrl = resolveStreamUrl(item.media?.streamUrl),
                    trailerUrl = item.media?.trailerUrl ?: "",
                    genre = item.genres ?: emptyList(),
                    runtime = item.runtimeMinutes ?: 0,
                    directors = item.credits?.directors ?: emptyList(),
                    cast = item.credits?.cast ?: emptyList()
                )
            }

            android.util.Log.d("TvRepository", "Mapped ${domainMovies.size} movies")

            // Log unique categories for debugging
            val uniqueCategories = domainMovies.map { it.category }.distinct()
            android.util.Log.d("TvRepository", "Movie categories found: $uniqueCategories")

            withContext(Dispatchers.Main) {
                movies.clear()
                movies.addAll(domainMovies)
                // IMPORTANT: Invalidate cache after loading new movies
                moviesByCategoryCache.clear()
                android.util.Log.d("TvRepository", "=== MOVIES READY: ${movies.size}, cache cleared ===")
            }
        } catch (e: Exception) {
            android.util.Log.e("TvRepository", "MOVIE LOAD ERROR: ${e.message}", e)
        }
    }

    /**
     * Force refresh all data from the server.
     */
    fun refreshData(context: Context? = null) {
        android.util.Log.d("TvRepository", "=== REFRESHING ALL DATA ===")

        // Clear caches
        context?.let { clearImageCache(it) }
        scheduleCache.clear()

        // Reload all data
        loadData()
    }

    /**
     * Clear Coil's memory and disk cache.
     */
    fun clearImageCache(context: Context) {
        android.util.Log.d("TvRepository", "Clearing image caches...")
        imageLoader?.memoryCache?.clear()
        repositoryScope.launch {
            try {
                imageLoader?.diskCache?.clear()
                android.util.Log.d("TvRepository", "Image caches cleared")
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Error clearing disk cache: ${e.message}")
            }
        }
    }

    /**
     * Clean up resources when app is destroyed
     */
    fun cleanup() {
        repositoryScope.cancel()
        imageLoader = null
        scheduleCache.clear()
        channelProgramsMap.clear()
    }

    /**
     * Logout - clear authentication and all user data
     */
    fun logout() {
        android.util.Log.d("TvRepository", "Logging out...")
        
        // Clear auth state
        authToken = null
        isAuthenticated = false
        
        // Clear all data
        channels.clear()
        movies.clear()
        currentPrograms.clear()
        channelCategories.clear()
        scheduleCache.clear()
        channelProgramsMap.clear()
        moviesByCategoryCache.clear()
        channelHistory.clear()
        moviePositions.clear()
        
        // Reset loading state
        _isDataReady.value = false
        _loadingProgress.value = ""
        
        android.util.Log.d("TvRepository", "Logout complete")
    }

    // Pre-computed filtered lists for better performance
    // These are computed once and cached rather than filtering on every recomposition

    /**
     * Get movies filtered by category - cached computation
     */
    private val moviesByCategoryCache = mutableMapOf<String, List<Movie>>()

    fun getMoviesByCategory(categoryId: String): List<Movie> {
        android.util.Log.d(TAG, "getMoviesByCategory($categoryId) called, movies.size=${movies.size}, cacheHit=${moviesByCategoryCache.containsKey(categoryId)}")

        // If cache has it, return directly
        val cached = moviesByCategoryCache[categoryId]
        if (cached != null) {
            android.util.Log.d(TAG, "getMoviesByCategory($categoryId): returning ${cached.size} from cache")
            return cached
        }

        // Compute fresh
        android.util.Log.d(TAG, "Computing category $categoryId from ${movies.size} movies")
        val result = if (categoryId == "all") {
            movies.toList()
        } else {
            val normalizedSearch = categoryId.lowercase()
            android.util.Log.d(TAG, "Filtering for: '$normalizedSearch'")

            // Debug: sample a few movies to see their categories
            if (movies.isNotEmpty()) {
                val sample = movies.take(5).map { "${it.title}:${it.category}" }
                android.util.Log.d(TAG, "Sample movies: $sample")
            }

            movies.filter { movie ->
                val cat = movie.category.lowercase()
                val genres = movie.genre.map { it.lowercase() }

                val directMatch = cat == normalizedSearch
                val genreMatch = genres.contains(normalizedSearch)
                val scifiMatch = normalizedSearch == "scifi" && (cat.contains("sci") || genres.any { it.contains("sci") })
                val partialCatMatch = cat.contains(normalizedSearch)
                val partialGenreMatch = genres.any { it.contains(normalizedSearch) }

                directMatch || genreMatch || scifiMatch || partialCatMatch || partialGenreMatch
            }
        }

        android.util.Log.d(TAG, "getMoviesByCategory($categoryId): found ${result.size} movies")
        moviesByCategoryCache[categoryId] = result
        return result
    }

    /**
     * Invalidate category cache when movies change
     */
    fun invalidateMovieCache() {
        moviesByCategoryCache.clear()
    }
}
