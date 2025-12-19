package com.example.androidtviptvapp.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.androidtviptvapp.data.api.ApiClient
import com.example.androidtviptvapp.data.api.BrandConfig
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

    // Data State
    val channels = mutableStateListOf<Channel>()
    
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
                val config = ApiClient.service.getPublicConfig()
                withContext(Dispatchers.Main) {
                    appConfig = config.brand
                }
            } catch (e: Exception) {
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
            }
            
            loadChannels()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun loadChannels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("TvRepository", "Loading channels from API...")
                val response = ApiClient.service.getChannels()
                android.util.Log.d("TvRepository", "Got ${response.items.size} channels from API")
                
                val domainChannels = response.items.map { dto ->
                    // Convert relative logo paths to absolute URLs
                    val logoUrl = when {
                        dto.logo.isNullOrEmpty() -> ""
                        dto.logo.startsWith("http") -> dto.logo
                        dto.logo.startsWith("/") -> "http://10.0.2.2:8000${dto.logo}"
                        else -> dto.logo
                    }
                    
                    Channel(
                        id = dto.id,
                        name = dto.name,
                        logo = logoUrl,
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
    
    // Call this on app start to load data
    fun loadData() {
        init()
        loadChannels()
        loadMovies()
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
                        thumbnail = item.images?.poster ?: "",
                        category = item.genres?.firstOrNull()?.lowercase() ?: "all",
                        year = item.year ?: 2024,
                        rating = item.rating?.toString() ?: "0.0",
                        description = item.synopsis ?: "",
                        videoUrl = item.media?.streamUrl ?: "",
                        genre = item.genres ?: emptyList()
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
    }
}
