package com.example.androidtviptvapp.ui.components

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
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.Channel

/**
 * ChannelListItem with logos - optimized for smooth scrolling.
 * Uses simple AsyncImage with no crossfade for performance.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListItem(
    channel: Channel,
    onClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Stable image request with memory caching, NO crossfade
    val imageRequest = remember(channel.id) {
        if (channel.logo.isNotBlank() && channel.logo.startsWith("http")) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .memoryCacheKey(channel.id)
                .diskCacheKey(channel.id)
                .crossfade(false)
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
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
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
        modifier = modifier.fillMaxWidth()
    )
}
