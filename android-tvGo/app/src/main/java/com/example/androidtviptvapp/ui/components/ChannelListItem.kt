package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api

import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.Channel
import com.example.androidtviptvapp.data.ChannelViewSize

/**
 * ChannelListItem - Compact design for faster scrolling.
 * Smaller height = more items visible = faster perceived scrolling.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListItem(
    channel: Channel,
    onClick: (Channel) -> Unit,
    isPlaying: Boolean = false,
    viewSize: ChannelViewSize = ChannelViewSize.MEDIUM,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Size values based on viewSize
    val itemHeight = when (viewSize) {
        ChannelViewSize.SMALL -> 46.dp
        ChannelViewSize.MEDIUM -> 62.dp
        ChannelViewSize.LARGE -> 80.dp
    }
    val logoBoxSize = when (viewSize) {
        ChannelViewSize.SMALL -> 32.dp
        ChannelViewSize.MEDIUM -> 45.dp
        ChannelViewSize.LARGE -> 56.dp
    }
    val logoImageSize = when (viewSize) {
        ChannelViewSize.SMALL -> 26.dp
        ChannelViewSize.MEDIUM -> 38.dp
        ChannelViewSize.LARGE -> 48.dp
    }
    val nameTextStyle = when (viewSize) {
        ChannelViewSize.SMALL -> MaterialTheme.typography.bodyMedium
        ChannelViewSize.MEDIUM -> MaterialTheme.typography.titleMedium
        ChannelViewSize.LARGE -> MaterialTheme.typography.titleLarge
    }
    val playIconSize = when (viewSize) {
        ChannelViewSize.SMALL -> 10.dp
        ChannelViewSize.MEDIUM -> 14.dp
        ChannelViewSize.LARGE -> 18.dp
    }
    val cornerRadius = when (viewSize) {
        ChannelViewSize.SMALL -> 4.dp
        ChannelViewSize.MEDIUM -> 6.dp
        ChannelViewSize.LARGE -> 8.dp
    }

    // OPTIMIZED image request - aggressive caching, no animations
    val imageRequest = remember(channel.id) {
        if (channel.logo.isNotBlank() && channel.logo.startsWith("http")) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .memoryCacheKey(channel.logo)
                .diskCacheKey(channel.logo)
                .crossfade(false)
                .allowHardware(true)
                .size(64, 64)  // Smaller size for compact items
                .build()
        } else null
    }

    // Compact custom item using Surface for focus handling
    androidx.tv.material3.Surface(
        onClick = { onClick(channel) },
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(cornerRadius)
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF2A2A2A)
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(cornerRadius)
            )
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(
            focusedScale = 1.0f  // No zoom for speed
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(logoBoxSize)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier.size(logoImageSize),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Text(
                                text = channel.name.take(2).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        },
                        error = {
                            Text(
                                text = channel.name.take(2).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel name
            Text(
                text = channel.displayName,
                style = nameTextStyle,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            // Play triangle indicator for currently playing channel
            if (isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(playIconSize)
                ) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}
