package com.example.androidtviptvapp.data

/**
 * Application configuration - API endpoints
 */
object AppConfig {
    // Lambda API endpoint - BASE_URL must end with /api/ for Retrofit
    const val BASE_URL = "https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws/api/"

    // For images/assets
    const val IMAGE_BASE_URL = "https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws"

    // Not needed for Lambda but kept for compatibility
    const val SERVER_IP = "hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws"
}
