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
                    .maxSizePercent(0.15) // 15% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB disk cache
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // Use our cache settings
            .build()
    }

    /**
     * Initialize app - called from Application class or MainActivity
     * Runs entirely in background, doesn't block UI
     */
    fun loadData() {
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

    suspend fun login(username: String, pass: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.service.login(LoginRequest(username, pass))
                withContext(Dispatchers.Main) {
                    authToken = response.accessToken
                    isAuthenticated = true

                    if (response.config != null) {
                        appConfig = response.config.brand
                        features = response.config.features
                    }
                }

                loadChannelsAsync()
                true
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Login failed: ${e.message}")
                false
            }
        }
    }

    private fun resolveUrl(path: String?): String {
        if (path.isNullOrEmpty()) return ""

        val baseUrl = AppConfig.IMAGE_BASE_URL

        // If already a full URL, return as-is
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }

        // Build full URL from path
        return when {
            path.startsWith("/") -> "$baseUrl$path"
            else -> "$baseUrl/$path"
        }
    }

    private suspend fun loadChannelsAsync() {
        try {
            android.util.Log.d("TvRepository", "Loading channels from API...")
            val response = ApiClient.service.getChannels()
            android.util.Log.d("TvRepository", "Got ${response.items.size} channels from API")

            val domainChannels = response.items.map { dto ->
                Channel(
                    id = dto.id,
                    name = dto.name,
                    logo = resolveUrl(dto.logo),
                    category = dto.category ?: dto.group ?: "all",
                    streamUrl = dto.streamUrl,
                    description = dto.description ?: "",
                    logoColor = dto.logoColor ?: "#000000",
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
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Failed to load schedule for $channelId: ${e.message}")
                cached?.programs ?: emptyList()
            }
        }
    }

    private suspend fun loadMoviesAsync() {
        try {
            android.util.Log.d("TvRepository", "=== LOADING MOVIES ===")
            val response = ApiClient.service.getMovies()
            android.util.Log.d("TvRepository", "Got ${response.total} movies from API")

            val domainMovies = response.items.map { item ->
                Movie(
                    id = item.id,
                    title = item.title,
                    thumbnail = resolveUrl(item.images?.poster),
                    backdrop = resolveUrl(item.images?.landscape ?: item.images?.hero),
                    category = item.genres?.firstOrNull()?.lowercase() ?: "all",
                    year = item.year ?: 2024,
                    rating = item.rating?.toString() ?: "0.0",
                    description = item.synopsis ?: "",
                    videoUrl = item.media?.streamUrl ?: "",
                    trailerUrl = item.media?.trailerUrl ?: "",
                    genre = item.genres ?: emptyList(),
                    runtime = item.runtimeMinutes ?: 0,
                    directors = item.credits?.directors ?: emptyList(),
                    cast = item.credits?.cast ?: emptyList()
                )
            }

            android.util.Log.d("TvRepository", "Mapped ${domainMovies.size} movies")

            withContext(Dispatchers.Main) {
                movies.clear()
                movies.addAll(domainMovies)
                android.util.Log.d("TvRepository", "=== MOVIES READY: ${movies.size} ===")
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
    }

    // Pre-computed filtered lists for better performance
    // These are computed once and cached rather than filtering on every recomposition

    /**
     * Get movies filtered by category - cached computation
     */
    private val moviesByCategoryCache = mutableMapOf<String, List<Movie>>()

    fun getMoviesByCategory(categoryId: String): List<Movie> {
        return moviesByCategoryCache.getOrPut(categoryId) {
            if (categoryId == "all") movies.toList()
            else movies.filter { it.category == categoryId }
        }
    }

    /**
     * Invalidate category cache when movies change
     */
    fun invalidateMovieCache() {
        moviesByCategoryCache.clear()
    }
}
