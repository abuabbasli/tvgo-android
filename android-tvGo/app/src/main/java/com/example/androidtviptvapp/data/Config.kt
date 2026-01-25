package com.example.androidtviptvapp.data

/**
 * Application configuration - API endpoints
 */
object AppConfig {
    // ===========================================
    // LOCAL DEVELOPMENT - uncomment to use local server
    // ===========================================
    // For Android Emulator: use 10.0.2.2 (maps to host localhost)
    // For Physical Device: use your computer's IP (e.g., 192.168.1.76)

    // Local backend - use your computer's IP for physical device
    // For emulator use: 10.0.2.2
    // For physical device use your computer IP (currently: 192.168.1.76)
    const val BASE_URL = "http://21.0.0.176:8000/"
    const val IMAGE_BASE_URL = "http://21.0.0.176:8000"
    const val SERVER_IP = "21.0.0.176"

    // ===========================================
    // PRODUCTION - Lambda URL (comment out when testing locally)
    // ===========================================
    // const val BASE_URL = "https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws/"
    // const val IMAGE_BASE_URL = "https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws"
    // const val SERVER_IP = "hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws"
}
