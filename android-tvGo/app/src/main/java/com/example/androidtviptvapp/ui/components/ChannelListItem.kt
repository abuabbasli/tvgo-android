package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.Channel

/**
 * ChannelListItem with logos - optimized for smooth scrolling.
 * Uses simple AsyncImage with aggressive caching for instant display.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListItem(
    channel: Channel,
    onClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // OPTIMIZED image request - aggressive caching, no animations
    val imageRequest = remember(channel.id) {  // Use channel.id for stability
        if (channel.logo.isNotBlank() && channel.logo.startsWith("http")) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .memoryCacheKey(channel.logo)
                .diskCacheKey(channel.logo)
                .crossfade(false)  // No animation for instant display
                .allowHardware(true)  // Use hardware bitmaps
                .size(96, 96)  // Fixed size for consistent caching
                .build()
        } else null
    }

    ListItem(
        selected = false,
        onClick = { onClick(channel) },
        headlineContent = {
            Text(
                text = channel.displayName,  // Shows "order  channelname"
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        },
        supportingContent = {
            Text(
                text = channel.description.take(40),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF),
                maxLines = 1
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    // Use SubcomposeAsyncImage with proper loading/error states
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(4.dp),
                        contentScale = ContentScale.Fit,
                        loading = {
                            // Show initials while loading
                            Text(
                                text = channel.name.take(2).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.Gray
                            )
                        },
                        error = {
                            // Show initials on error (logo failed to load)
                            Text(
                                text = channel.name.take(2).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.Gray
                            )
                        }
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Gray
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF2A2A2A),
            selectedContainerColor = Color(0xFF2A2A2A),
            contentColor = Color(0xFFE0E0E0),
            focusedContentColor = Color.White
        ),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ListItemDefaults.scale(focusedScale = 1.0f),  // No zoom animation for faster scrolling
        modifier = modifier.fillMaxWidth()
    )
}
