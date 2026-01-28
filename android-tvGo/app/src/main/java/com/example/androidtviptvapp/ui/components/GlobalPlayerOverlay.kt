package com.example.androidtviptvapp.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.androidtviptvapp.player.SharedPlayerManager
import timber.log.Timber

/**
 * GlobalPlayerOverlay - A single player view that NEVER moves between containers.
 *
 * This composable should be placed at the ROOT level (MainActivity) and it
 * animates its size/position based on whether we're in preview or fullscreen mode.
 *
 * KEY BENEFITS:
 * - No MediaCodec errors from view parent changes
 * - Seamless transitions between preview and fullscreen
 * - Single player instance throughout app lifecycle
 *
 * IMPORTANT: In preview mode, this sits at exact preview bounds.
 * In fullscreen mode, this covers the entire screen.
 */
@Composable
fun GlobalPlayerOverlay(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Observe state from SharedPlayerManager
    val isFullscreen by SharedPlayerManager.isFullscreen.collectAsState()
    val isVisible by SharedPlayerManager.isOverlayVisible.collectAsState()
    val previewBounds by SharedPlayerManager.previewBounds.collectAsState()

    // Animation specs - fast and smooth with easing
    // 150ms is quick enough to feel instant but smooth enough to see the transition
    val animationSpec = tween<androidx.compose.ui.unit.Dp>(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    // Calculate target dimensions based on mode
    val targetX by animateDpAsState(
        targetValue = if (isFullscreen) 0.dp else previewBounds.x.dp,
        animationSpec = animationSpec,
        label = "playerX"
    )
    val targetY by animateDpAsState(
        targetValue = if (isFullscreen) 0.dp else previewBounds.y.dp,
        animationSpec = animationSpec,
        label = "playerY"
    )
    val targetWidth by animateDpAsState(
        targetValue = if (isFullscreen) screenWidth else previewBounds.width.dp,
        animationSpec = animationSpec,
        label = "playerWidth"
    )
    val targetHeight by animateDpAsState(
        targetValue = if (isFullscreen) screenHeight else previewBounds.height.dp,
        animationSpec = animationSpec,
        label = "playerHeight"
    )

    // Corner radius - 16dp in preview, 0 in fullscreen (faster animation for corners)
    val cornerRadius by animateDpAsState(
        targetValue = if (isFullscreen) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "cornerRadius"
    )

    // Only show if visible and has valid bounds (or fullscreen)
    val shouldShow = isVisible && (isFullscreen || (previewBounds.width > 0 && previewBounds.height > 0))

    if (shouldShow) {
        Box(
            modifier = Modifier
                // Position at exact coordinates - NOT using fillMaxSize!
                .offset(x = targetX, y = targetY)
                .size(width = targetWidth, height = targetHeight)
                // Z-index: high in fullscreen to cover everything, normal in preview
                .zIndex(if (isFullscreen) 100f else 1f)
                // Clip to rounded corners in preview mode
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black)
        ) {
            // The actual player view - attached ONCE, never moved
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Attach player to this container - ONCE and FOREVER
                        SharedPlayerManager.attachToGlobalContainer(this, ctx)
                        Timber.d("GlobalPlayerOverlay: Player attached to global container")
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { _ ->
                    // Nothing to update - player stays attached
                }
            )
        }
    }

    // Cleanup on dispose (app exit)
    DisposableEffect(Unit) {
        onDispose {
            Timber.d("GlobalPlayerOverlay: Disposing")
        }
    }
}
