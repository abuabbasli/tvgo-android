package com.example.androidtviptvapp

import android.app.Application
import android.content.ComponentCallbacks2
import coil.Coil
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.player.SharedPlayerManager

/**
 * Custom Application class for TV app optimizations.
 * Enhanced with OnTV-main style memory management for TV boxes.
 */
class TvGoApplication : Application() {

    companion object {
        private const val TAG = "TvGoApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize optimized Coil ImageLoader
        val imageLoader = TvRepository.getImageLoader(applicationContext)
        Coil.setImageLoader(imageLoader)

        android.util.Log.d(TAG, "Application created - TV Go ready")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                android.util.Log.d(TAG, "UI hidden - trimming memory")
                // Just log, don't release player yet
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                android.util.Log.w(TAG, "System running low on memory - clearing caches")
                TvRepository.clearImageCache(applicationContext)
                TvRepository.checkMemoryAndCleanup()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                android.util.Log.e(TAG, "CRITICAL: System very low on memory!")
                TvRepository.clearImageCache(applicationContext)
                TvRepository.checkMemoryAndCleanup()
                // OnTV-main pattern: release player on critical memory
                SharedPlayerManager.releaseCompletely()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                android.util.Log.d(TAG, "Background memory pressure - clearing caches")
                TvRepository.clearImageCache(applicationContext)
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                android.util.Log.w(TAG, "Complete memory pressure - clearing all")
                TvRepository.clearImageCache(applicationContext)
                TvRepository.checkMemoryAndCleanup()
                // Release player completely when system needs maximum memory
                SharedPlayerManager.releaseCompletely()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        android.util.Log.e(TAG, "LOW MEMORY - releasing all resources!")
        TvRepository.clearImageCache(applicationContext)
        // OnTV-main pattern: release player on low memory
        SharedPlayerManager.releaseCompletely()
        // Force GC
        System.gc()
    }

    override fun onTerminate() {
        android.util.Log.d(TAG, "Application terminating - cleanup")
        SharedPlayerManager.releaseCompletely()
        TvRepository.cleanup()
        super.onTerminate()
    }
}
