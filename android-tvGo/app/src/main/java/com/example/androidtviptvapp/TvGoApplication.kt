package com.example.androidtviptvapp

import android.app.Application
import android.content.ComponentCallbacks2
import coil.Coil
import coil.ImageLoader
import com.example.androidtviptvapp.data.TvRepository

/**
 * Custom Application class for TV app optimizations:
 * - Early initialization of Coil ImageLoader
 * - Memory management for low-end TV boxes
 * - Proper cleanup on low memory situations
 */
class TvGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize optimized Coil ImageLoader as early as possible
        val imageLoader = TvRepository.getImageLoader(applicationContext)
        Coil.setImageLoader(imageLoader)
    }

    /**
     * Handle memory pressure from system
     * Critical for low-end TV boxes with limited RAM
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // App moved to background - clear some caches
                android.util.Log.d("TvGoApplication", "UI hidden - trimming memory")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // System is running low on memory while we're running
                android.util.Log.d("TvGoApplication", "System low on memory - clearing image cache")
                TvRepository.clearImageCache(applicationContext)
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // App is in background and may be killed soon
                android.util.Log.d("TvGoApplication", "Background memory pressure - clearing all caches")
                TvRepository.clearImageCache(applicationContext)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        android.util.Log.w("TvGoApplication", "Low memory warning - clearing all caches")
        TvRepository.clearImageCache(applicationContext)
    }
}
