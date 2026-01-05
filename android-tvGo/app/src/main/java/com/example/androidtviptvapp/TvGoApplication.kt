package com.example.androidtviptvapp

import android.app.Application
import android.content.ComponentCallbacks2
import coil.Coil
import com.example.androidtviptvapp.data.TvRepository

/**
 * Custom Application class for TV app optimizations.
 */
class TvGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize optimized Coil ImageLoader
        val imageLoader = TvRepository.getImageLoader(applicationContext)
        Coil.setImageLoader(imageLoader)
        
        // Note: Player is now View-based, initialized when needed
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                android.util.Log.d("TvGoApplication", "UI hidden - trimming memory")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                android.util.Log.d("TvGoApplication", "System low on memory - clearing caches")
                TvRepository.clearImageCache(applicationContext)
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                android.util.Log.d("TvGoApplication", "Background memory pressure - clearing all")
                TvRepository.clearImageCache(applicationContext)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        android.util.Log.w("TvGoApplication", "Low memory - clearing all caches")
        TvRepository.clearImageCache(applicationContext)
    }
}
