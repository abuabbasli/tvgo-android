package com.example.androidtviptvapp.data

/**
 * Application configuration constants.
 * Centralized configuration for API endpoints and server settings.
 */
object AppConfig {
    // Base URL for API requests - change this for different environments
    const val SERVER_IP = "192.168.1.100"  // Change to your server IP
    const val BASE_URL = "http://$SERVER_IP:3000/api/"
    const val IMAGE_BASE_URL = "http://$SERVER_IP:3000"

    // Performance Configuration
    object Performance {
        // Pagination limits - optimized for TV boxes with limited memory
        const val CHANNELS_PAGE_SIZE = 50
        const val MOVIES_PAGE_SIZE = 30
        const val INITIAL_LOAD_SIZE = 20

        // Image cache settings
        const val IMAGE_MEMORY_CACHE_PERCENT = 0.15  // 15% of available memory
        const val IMAGE_DISK_CACHE_SIZE = 100L * 1024 * 1024  // 100MB

        // Network timeouts (milliseconds)
        const val CONNECT_TIMEOUT = 15_000L
        const val READ_TIMEOUT = 30_000L
        const val WRITE_TIMEOUT = 15_000L

        // Video buffer settings (milliseconds) - optimized for TV boxes
        const val MIN_BUFFER_MS = 2000
        const val MAX_BUFFER_MS = 30000
        const val BUFFER_FOR_PLAYBACK_MS = 1000
        const val BUFFER_FOR_REBUFFER_MS = 2000
    }

    // Feature Flags
    object Features {
        const val ENABLE_VIDEO_PREVIEW = true
        const val ENABLE_SCHEDULE_CACHING = true
        const val SCHEDULE_CACHE_DURATION_MS = 5 * 60 * 1000L  // 5 minutes
    }
}
