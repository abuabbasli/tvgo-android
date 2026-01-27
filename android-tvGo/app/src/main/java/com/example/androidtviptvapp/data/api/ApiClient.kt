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
    val streamUrl: String,
    val order: Int? = null  // Channel order number from backend
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

    @GET("api/auth/subscriber/me")
    suspend fun getSubscriberProfile(): SubscriberProfileResponse

    @POST("api/auth/subscriber/clear-baby-lock-reset")
    suspend fun clearBabyLockReset(): Any
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
    val id: String = "",
    val title: String?,
    val start: String?,
    val end: String?,
    val description: String?,
    val category: String?
) {
    // Computed properties for timestamps (OnTV-main pattern)
    // Parse ISO date strings to Unix timestamps in milliseconds
    val startTime: Long
        get() = parseIsoDateToMillis(start)

    val stopTime: Long
        get() = parseIsoDateToMillis(end)

    companion object {
        /**
         * Parse ISO date string to Unix timestamp in milliseconds.
         * Handles formats like:
         * - "2024-01-15T10:00:00"
         * - "2024-01-15T10:00:00Z"
         * - "2024-01-15T10:00:00+04:00"
         * - Unix timestamp as string (e.g., "1705312800000")
         */
        private fun parseIsoDateToMillis(dateStr: String?): Long {
            if (dateStr.isNullOrEmpty()) return 0L

            return try {
                // First, try to parse as a simple Long (Unix timestamp)
                dateStr.toLongOrNull()?.let { return it }

                // Check if string has timezone info (Z, +, or - after position 10)
                // Date format: "2024-01-15T10:00:00" - minus signs at pos 4 and 7 are date separators
                // Timezone offset like "-05:00" would have minus after position 10
                val hasTimezone = dateStr.contains("Z") ||
                    dateStr.contains("+") ||
                    (dateStr.length > 10 && dateStr.indexOf('-', 10) > 0)

                // Parse ISO 8601 date string
                val formatter = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    java.time.ZonedDateTime.parse(
                        if (hasTimezone) {
                            dateStr
                        } else {
                            "${dateStr}Z" // Assume UTC if no timezone
                        }
                    ).toInstant().toEpochMilli()
                } else {
                    // Fallback for older Android versions
                    val simpleDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    simpleDateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val cleanedDate = dateStr.replace(Regex("[Z+].*"), "") // Remove timezone for SimpleDateFormat
                    simpleDateFormat.parse(cleanedDate)?.time ?: 0L
                }
                formatter
            } catch (e: Exception) {
                android.util.Log.w("CurrentProgram", "Failed to parse date: $dateStr - ${e.message}")
                0L
            }
        }
    }
}

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

// ===== SUBSCRIBER PROFILE API =====

data class SubscriberProfileResponse(
    val id: String,
    val username: String?,
    @SerializedName("display_name")
    val displayName: String?,
    @SerializedName("mac_address")
    val macAddress: String?,
    @SerializedName("baby_lock_reset_pending")
    val babyLockResetPending: Boolean = false
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

    // Auth token provider - gets token from TvRepository
    private var authTokenProvider: (() -> String?)? = null
    
    fun setAuthTokenProvider(provider: () -> String?) {
        authTokenProvider = provider
    }

    // Auth Interceptor - adds Bearer token to all requests
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val token = authTokenProvider?.invoke()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    // Custom DNS to handle emulator DNS issues
    private val customDns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            try {
                // Try default DNS first
                return okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                android.util.Log.w("ApiClient", "DNS lookup failed for $hostname: ${e.message}")

                // Hardcoded fallback IPs for Lambda URL (for emulator DNS issues)
                if (hostname.contains("hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws")) {
                    android.util.Log.i("ApiClient", "Using hardcoded IP fallback for Lambda URL")
                    return listOf(
                        java.net.InetAddress.getByName("3.125.109.180"),
                        java.net.InetAddress.getByName("3.67.101.14"),
                        java.net.InetAddress.getByName("52.28.167.18")
                    )
                }

                // Fallback: try manual resolution
                try {
                    val addresses = java.net.InetAddress.getAllByName(hostname)
                    if (addresses.isNotEmpty()) {
                        return addresses.toList()
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("ApiClient", "Fallback DNS also failed: ${e2.message}")
                }
                throw e
            }
        }
    }

    // Optimized OkHttp client with timeouts and connection pooling
    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .dns(customDns)  // Add custom DNS
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

    // =========================================================================
    // RETRY MECHANISM (OnTV-main Pattern)
    // Exponential backoff for network resilience
    // =========================================================================

    /**
     * Retry a network call with exponential backoff - OnTV-main pattern
     *
     * @param retryCount Number of retries (default 2)
     * @param delayMS Base delay in milliseconds (default 400ms)
     * @param call The suspend function to call
     * @return The result of the call
     * @throws Exception if all retries fail
     */
    suspend inline fun <reified T> retry(
        retryCount: Int = 2,
        delayMS: Long = 400,
        crossinline call: suspend () -> T
    ): T {
        var lastException: Exception? = null

        for (i in 0..retryCount) {
            try {
                return call()
            } catch (e: retrofit2.HttpException) {
                // Don't retry on authentication/authorization errors - they won't succeed
                val code = e.code()
                if (code == 401 || code == 403 || code == 404) {
                    android.util.Log.w("ApiClient", "HTTP $code - not retrying: ${e.message}")
                    throw e
                }
                lastException = e
                android.util.Log.w("ApiClient", "Retry ${i + 1}/$retryCount failed: HTTP $code ${e.message}")

                if (i < retryCount) {
                    val actualDelay = delayMS * (i + 1)
                    android.util.Log.d("ApiClient", "Waiting ${actualDelay}ms before retry...")
                    kotlinx.coroutines.delay(actualDelay)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Don't retry on cancellation - propagate immediately
                throw e
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("ApiClient", "Retry ${i + 1}/$retryCount failed: ${e.message}")

                if (i < retryCount) {
                    // Exponential backoff: delay * (attempt + 1)
                    val actualDelay = delayMS * (i + 1)
                    android.util.Log.d("ApiClient", "Waiting ${actualDelay}ms before retry...")
                    kotlinx.coroutines.delay(actualDelay)
                }
            }
        }

        throw lastException ?: Exception("Retry failed with unknown error")
    }

    /**
     * Big retry for critical operations like channel list - OnTV-main pattern
     */
    suspend inline fun <reified T> bigRetry(
        crossinline call: suspend () -> T
    ): T = retry(retryCount = 10, delayMS = 200, call = call)

    /**
     * Video stream retry - OnTV-main pattern
     */
    suspend inline fun <reified T> streamRetry(
        crossinline call: suspend () -> T
    ): T = retry(retryCount = 5, delayMS = 500, call = call)
}
