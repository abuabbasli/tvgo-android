package com.example.androidtviptvapp.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import com.example.androidtviptvapp.data.Channel
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
    val brand: BrandConfig
)

data class BrandConfig(
    val appName: String,
    val logoUrl: String,
    val accentColor: String?,
    val backgroundColor: String?
)

interface ApiService {
    @GET("channels?limit=1000")
    suspend fun getChannels(): ChannelsResponse
    
    @GET("config")
    suspend fun getPublicConfig(): ConfigResponse

    @POST("auth/subscriber/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @GET("movies?limit=1000")
    suspend fun getMovies(): MoviesApiResponse
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

object ApiClient {
    // Emulator localhost alias: 10.0.2.2
    private const val BASE_URL = "http://10.0.2.2:8000/api/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: ApiService = retrofit.create(ApiService::class.java)
}
