package com.example.androidtviptvapp.ui.components


import androidx.compose.foundation.Image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

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
private const val DEBUG_LOGGING = false  // Disable verbose logging for performance

/**
 * AppLogo component that displays the brand logo from remote URL with disk caching.
 * Falls back to local drawable if remote fails or URL is not available.
 *
 * Features:
 * - Proper reactive state observation of TvRepository.appConfig
 * - Disk caching for offline access and faster loads
 * - Memory caching for instant display
 * - Graceful fallback to local app_logo.png
 * - NO flicker: Shows nothing until we know what to display
 *
 * @param forceStaticLogo If true, always shows the static logo (useful for login screen)
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    forceStaticLogo: Boolean = false
) {
    // If forcing static logo, skip all remote logic
    if (forceStaticLogo) {
        FallbackLogo(modifier = modifier, size = size)
        return
    }

    // Directly observe appConfig - this will recompose when config changes
    val appConfig = TvRepository.appConfig
    val logoUrl = appConfig?.logoUrl
    val context = LocalContext.current

    // Track if config has been loaded at least once
    val configLoaded = appConfig != null

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Case 1: Config not yet loaded - show static logo immediately
            !configLoaded -> {
                FallbackLogo(size = size)
            }

            // Case 2: Config loaded but no logo URL - show fallback
            logoUrl.isNullOrEmpty() -> {
                FallbackLogo(size = size)
            }

            // Case 3: Has logo URL - try to load it
            else -> {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .memoryCacheKey(logoUrl)  // Use URL as cache key
                        .diskCacheKey(logoUrl)    // Use URL as disk cache key
                        .crossfade(false)  // No crossfade to prevent flicker
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Fit,
                    loading = {
                        // Show static logo while loading
                        FallbackLogo(size = size)
                    },
                    error = {
                        // On error, show the local fallback
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
