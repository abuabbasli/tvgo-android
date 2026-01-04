package com.example.androidtviptvapp.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.AppConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName

// Define the response wrapper matching Backend API
data class ChannelsResponse(
    val items: List<ChannelDto>,
    val total: Int
)

data class ChannelDto(
    val id: String,
    val name: String,
    val group: String?,
    val category: String?,
    val description: String?,
    val logoColor: String?,
    val logo: String?,
    val streamUrl: String
)

// Auth & Config Models
data class LoginRequest(
    val username: String,
    val password: String,
    @SerializedName("mac_address")
    val macAddress: String = "00:00:00:00:00:00",
    @SerializedName("device_name")
    val deviceName: String = "Android TV"
)

data class LoginResponse(
    val accessToken: String,
    val config: ConfigResponse?
)

data class ConfigResponse(
    val brand: BrandConfig,
    val features: FeaturesConfig?
)

data class BrandConfig(
    val appName: String,
    val logoUrl: String,
    val accentColor: String?,
    val backgroundColor: String?
)

data class FeaturesConfig(
    val enableFavorites: Boolean,
    val enableSearch: Boolean,
    val autoplayPreview: Boolean,
    val enableLiveTv: Boolean,
    val enableVod: Boolean
)

interface ApiService {
    @GET("api/channels?limit=1000")
    suspend fun getChannels(): ChannelsResponse

    @GET("api/config")
    suspend fun getPublicConfig(): ConfigResponse

    @POST("api/auth/subscriber/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/movies?limit=1000")
    suspend fun getMovies(): MoviesApiResponse

    @GET("api/channels/{id}/epg")
    suspend fun getPrograms(
        @retrofit2.http.Path("id") id: String,
        @retrofit2.http.Query("start") start: String? = null,
        @retrofit2.http.Query("end") end: String? = null
    ): EpgResponse

    @GET("api/messages")
    suspend fun getMessages(): MessagesResponse

    @GET("api/messages/broadcast")
    suspend fun getBroadcastMessages(): MessagesResponse

    @POST("api/messages/{id}/read")
    suspend fun markMessageRead(@retrofit2.http.Path("id") id: String): Any

    @GET("api/games")
    suspend fun getGames(): GamesResponse

    @GET("api/epg/now")
    suspend fun getCurrentPrograms(): CurrentProgramsResponse

    @GET("api/epg/schedule/{channelId}")
    suspend fun getChannelSchedule(
        @retrofit2.http.Path("channelId") channelId: String,
        @retrofit2.http.Query("hours") hours: Int = 12
    ): ChannelScheduleResponse
}

// ===== EPG SCHEDULE API =====

data class ChannelScheduleResponse(
    @SerializedName("channel_id")
    val channelId: String,
    val programs: List<ScheduleProgramItem>
)

data class ScheduleProgramItem(
    val id: String?,
    val title: String?,
    val start: String?,
    val end: String?,
    val description: String?,
    val category: String?,
    val isLive: Boolean = false
)

// ===== CURRENT PROGRAMS API =====

data class CurrentProgramsResponse(
    val programs: Map<String, CurrentProgram>
)

data class CurrentProgram(
    val title: String?,
    val start: String?,
    val end: String?,
    val description: String?,
    val category: String?
)

// ===== MOVIES API =====
// Simplified DTOs that match API response exactly

data class MoviesApiResponse(
    val total: Int,
    val items: List<MovieApiItem>,
    val nextOffset: Int?
)

data class MovieApiItem(
    val id: String,
    val title: String,
    val year: Int?,
    val genres: List<String>?,
    val rating: Double?,
    val runtimeMinutes: Int?,
    val synopsis: String?,
    val images: MovieApiImages?,
    val media: MovieApiMedia?,
    val badges: List<String>?,
    val credits: MovieApiCredits?,
    val availability: MovieApiAvailability?
)

data class MovieApiImages(
    val poster: String?,
    val landscape: String?,
    val hero: String?
)

data class MovieApiMedia(
    val streamUrl: String?,
    val trailerUrl: String?,
    val drm: Any?
)

data class MovieApiCredits(
    val directors: List<String>?,
    val cast: List<String>?
)

data class MovieApiAvailability(
    val start: String?,
    val end: String?
)

// ===== EPG API =====

data class EpgResponse(
    val channelId: String,
    val items: List<EpgProgramItem>,
    val nextOffset: Int?
)

data class EpgProgramItem(
    val id: String?,
    val title: String?,
    val start: String?, // ISO string
    val end: String?,
    val description: String?,
    val category: String?,
    val isLive: Boolean
)

// ===== MESSAGES API =====

data class MessagesResponse(
    val items: List<MessageItem>,
    val total: Int,
    @SerializedName("unread_count")
    val unreadCount: Int = 0
)

data class MessageItem(
    val id: String,
    val title: String,
    val body: String,
    val url: String?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("is_read")
    val isRead: Boolean = false
)

// ===== GAMES API =====

data class GamesResponse(
    val total: Int,
    val items: List<GameItem>,
    val categories: List<String>
)

data class GameItem(
    val id: String,
    val name: String,
    val description: String?,
    @SerializedName("imageUrl")
    val imageUrl: String?,
    @SerializedName("gameUrl")
    val gameUrl: String,
    val category: String?,
    @SerializedName("isActive")
    val isActive: Boolean,
    val order: Int?
)

object ApiClient {
    // Base URL - uses AppConfig
    private val BASE_URL = AppConfig.BASE_URL

    // Optimized OkHttp client with timeouts and connection pooling
    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        // Connection pooling for faster subsequent requests
        .connectionPool(okhttp3.ConnectionPool(5, 30, java.util.concurrent.TimeUnit.SECONDS))
        // Retry on connection failure
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: ApiService = retrofit.create(ApiService::class.java)
}
