package com.example.androidtviptvapp

import android.app.Application
import android.content.ComponentCallbacks2
import coil.Coil
import com.example.androidtviptvapp.data.TvRepository
import com.example.androidtviptvapp.data.api.ApiClient
import com.example.androidtviptvapp.player.SharedPlayerManager
import timber.log.Timber

/**
 * Custom Application class for TV app optimizations.
 * Enhanced with OnTV-main style memory management for TV boxes.
 * Uses Timber for logging (OnTV-main pattern).
 */
class TvGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber (OnTV-main pattern)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Set up auth token provider for API requests
        ApiClient.setAuthTokenProvider { TvRepository.authToken }

        // Initialize optimized Coil ImageLoader
        val imageLoader = TvRepository.getImageLoader(applicationContext)
        Coil.setImageLoader(imageLoader)

        Timber.d("Application created - TV Go ready")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Timber.d("UI hidden - trimming memory")
                // Just log, don't release player yet
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Timber.w("System running low on memory - clearing caches")
                TvRepository.clearImageCache(applicationContext)
                TvRepository.checkMemoryAndCleanup()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Timber.e("CRITICAL: System very low on memory!")
                TvRepository.clearImageCache(applicationContext)
                TvRepository.checkMemoryAndCleanup()
                // OnTV-main pattern: release player on critical memory
                SharedPlayerManager.releaseCompletely()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Timber.d("Background memory pressure - clearing caches")
                TvRepository.clearImageCache(applicationContext)
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Timber.w("Complete memory pressure - clearing all")
                TvRepository.clearImageCache(applicationContext)
                TvRepository.checkMemoryAndCleanup()
                // Release player completely when system needs maximum memory
                SharedPlayerManager.releaseCompletely()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Timber.e("LOW MEMORY - releasing all resources!")
        TvRepository.clearImageCache(applicationContext)
        // OnTV-main pattern: release player on low memory
        SharedPlayerManager.releaseCompletely()
        // Force GC
        System.gc()
    }

    override fun onTerminate() {
        Timber.d("Application terminating - cleanup")
        SharedPlayerManager.releaseCompletely()
        TvRepository.cleanup()
        super.onTerminate()
    }
}
