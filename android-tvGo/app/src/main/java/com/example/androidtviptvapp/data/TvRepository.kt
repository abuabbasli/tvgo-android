package com.example.androidtviptvapp.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.androidtviptvapp.data.api.ApiClient
import com.example.androidtviptvapp.data.api.BrandConfig
import com.example.androidtviptvapp.data.api.CurrentProgram
import com.example.androidtviptvapp.data.api.FeaturesConfig
import com.example.androidtviptvapp.data.api.LoginRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TvRepository {
    // Auth & Config State
    var isAuthenticated by mutableStateOf(false)
    var authToken by mutableStateOf<String?>(null)
    var appConfig by mutableStateOf<BrandConfig?>(null)
    var features by mutableStateOf<FeaturesConfig?>(null)

    // Data State
    val channels = mutableStateListOf<Channel>()
    
    // Current programs for each channel (channelId -> current program)
    val currentPrograms = mutableStateMapOf<String, CurrentProgram>()
    
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

    // Initialization
    fun init() {
        loadPublicConfig()
    }

    private fun loadPublicConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("TvRepository", "Loading public config...")
                val config = ApiClient.service.getPublicConfig()
                android.util.Log.d("TvRepository", "Config loaded: appName=${config.brand.appName}, logoUrl=${config.brand.logoUrl}")
                withContext(Dispatchers.Main) {
                    appConfig = config.brand
                    features = config.features
                    android.util.Log.d("TvRepository", "appConfig set: ${appConfig?.logoUrl}")
                }
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Failed to load config: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    suspend fun login(username: String, pass: String): Boolean {
        return try {
            val response = ApiClient.service.login(LoginRequest(username, pass))
            authToken = response.accessToken
            isAuthenticated = true
            
            if (response.config != null) {
                appConfig = response.config.brand
                features = response.config.features
            }
            
            loadChannels()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun resolveUrl(path: String?): String {
        if (path.isNullOrEmpty()) return ""
        
        var url = when {
             path.startsWith("http") -> path
             path.startsWith("/") -> "${AppConfig.IMAGE_BASE_URL}$path"
             else -> "${AppConfig.IMAGE_BASE_URL}/$path"
        }
        
        // Fix: If server returns localhost/0.0.0.0, replace with configured SERVER_IP
        // This handles cases where backend doesn't know its external IP
        if (url.contains("localhost") || url.contains("0.0.0.0") || url.contains("127.0.0.1")) {
            url = url.replace("localhost", AppConfig.SERVER_IP)
                     .replace("0.0.0.0", AppConfig.SERVER_IP)
                     .replace("127.0.0.1", AppConfig.SERVER_IP)
        }
        
        return url
    }

    private fun loadChannels() {
        CoroutineScope(Dispatchers.IO).launch {
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
                
                // Extract unique categories from channels - use original name as ID
                val uniqueCategories = domainChannels
                    .map { it.category }
                    .filter { it.isNotBlank() && it != "all" }
                    .distinct()
                    .sorted()
                    .map { Category(it, it) } // Use same value for ID and name
                
                android.util.Log.d("TvRepository", "Extracted ${uniqueCategories.size} categories: ${uniqueCategories.map { it.name }}")
                
                withContext(Dispatchers.Main) {
                    channels.clear()
                    channels.addAll(domainChannels)
                    
                    // Build category list: "All" first, then dynamic categories
                    channelCategories.clear()
                    channelCategories.add(Category("all", "All Channels"))
                    channelCategories.addAll(uniqueCategories)
                    
                    android.util.Log.d("TvRepository", "Loaded ${channels.size} channels and ${channelCategories.size} categories")
                }
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Error loading channels", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Load current programs for all channels based on current time
     */
    fun loadCurrentPrograms() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("TvRepository", "Loading current programs...")
                val response = ApiClient.service.getCurrentPrograms()
                android.util.Log.d("TvRepository", "Got ${response.programs.size} current programs")
                
                withContext(Dispatchers.Main) {
                    currentPrograms.clear()
                    currentPrograms.putAll(response.programs)
                }
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Error loading current programs: ${e.message}", e)
            }
        }
    }
    
    // Call this on app start to load data
    fun loadData() {
        init()
        loadChannels()
        loadMovies()
        loadCurrentPrograms()
    }
    
    /**
     * Force refresh all data from the server.
     * Call this when you expect updates (e.g., after backend changes).
     * Also clears Coil's image cache to ensure fresh images are loaded.
     */
    fun refreshData(context: android.content.Context? = null) {
        android.util.Log.d("TvRepository", "=== REFRESHING ALL DATA ===")
        
        // Clear image caches if context is provided
        context?.let { clearImageCache(it) }
        
        // Reload all data from server
        loadPublicConfig()
        channels.clear()
        movies.clear()
        loadChannels()
        loadMovies()
    }
    
    /**
     * Clear Coil's memory and disk cache.
     * Call this when images on the server have been updated.
     */
    fun clearImageCache(context: android.content.Context) {
        android.util.Log.d("TvRepository", "Clearing image caches...")
        val imageLoader = coil.ImageLoader(context)
        imageLoader.memoryCache?.clear()
        // Note: Disk cache clearing requires async operation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                imageLoader.diskCache?.clear()
                android.util.Log.d("TvRepository", "Image caches cleared")
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "Error clearing disk cache: ${e.message}")
            }
        }
    }

    // Movies - loaded from API
    val movies = mutableStateListOf<Movie>()
    
    private fun loadMovies() {
        CoroutineScope(Dispatchers.IO).launch {
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
                
                // Debug: Log first 5 movies with their poster URLs
                domainMovies.take(5).forEach { movie ->
                    android.util.Log.d("TvRepository", "Movie: ${movie.title}, Poster: ${movie.thumbnail}")
                }
                
                // Count movies with posters
                val moviesWithPosters = domainMovies.count { it.thumbnail.isNotEmpty() }
                android.util.Log.d("TvRepository", "Movies with posters: $moviesWithPosters / ${domainMovies.size}")
                
                withContext(Dispatchers.Main) {
                    movies.clear()
                    movies.addAll(domainMovies)
                    android.util.Log.d("TvRepository", "=== MOVIES READY: ${movies.size} ===")
                }
            } catch (e: Exception) {
                android.util.Log.e("TvRepository", "MOVIE LOAD ERROR: ${e.message}", e)
            }
        }
    }
}
