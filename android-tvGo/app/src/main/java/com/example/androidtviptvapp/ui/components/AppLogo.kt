package com.example.androidtviptvapp.ui.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.androidtviptvapp.R
import com.example.androidtviptvapp.data.TvRepository

private const val TAG = "AppLogo"

/**
 * AppLogo component that displays the brand logo from remote URL with disk caching.
 * Falls back to local drawable if remote fails or URL is not available.
 * 
 * Features:
 * - Proper reactive state observation of TvRepository.appConfig
 * - Disk caching for offline access and faster loads  
 * - Memory caching for instant display
 * - Graceful fallback to local app_logo.png
 * - Loading indicator during fetch
 * - CrossFade animation for smooth transitions
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    // Directly observe appConfig - this will recompose when config changes
    val appConfig = TvRepository.appConfig
    val logoUrl = appConfig?.logoUrl
    val context = LocalContext.current
    
    // Log whenever we recompose
    LaunchedEffect(logoUrl) {
        Log.d(TAG, "Logo URL changed to: $logoUrl")
    }
    
    Log.d(TAG, "Rendering AppLogo - appConfig: ${appConfig != null}, logoUrl: $logoUrl")
    
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Case 1: No logo URL yet (config not loaded) - show local fallback
            logoUrl.isNullOrEmpty() -> {
                Log.d(TAG, "No logo URL available, showing fallback")
                FallbackLogo(size = size)
            }
            
            // Case 2: Has logo URL - try to load it 
            else -> {
                Log.d(TAG, "Loading logo from URL: $logoUrl")
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .crossfade(300)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .listener(
                            onStart = { Log.d(TAG, "Starting to load: $logoUrl") },
                            onSuccess = { _, _ -> Log.d(TAG, "Successfully loaded: $logoUrl") },
                            onError = { _, result -> Log.e(TAG, "Failed to load: $logoUrl, error: ${result.throwable.message}") }
                        )
                        .build(),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Fit,
                    loading = {
                        // Show a subtle loading state
                        Box(
                            modifier = Modifier
                                .size(size)
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(size / 2),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    },
                    error = {
                        // On error, show the local fallback
                        Log.e(TAG, "Error loading logo, showing fallback")
                        FallbackLogo(size = size)
                    },
                    success = {
                        SubcomposeAsyncImageContent()
                    }
                )
            }
        }
    }
}

@Composable
private fun FallbackLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Image(
        painter = painterResource(id = R.drawable.app_logo),
        contentDescription = "App Logo",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}
