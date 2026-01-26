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
 * ChannelListItem - Compact design for faster scrolling.
 * Smaller height = more items visible = faster perceived scrolling.
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
            .height(62.dp),  // 40% larger (44dp * 1.4 = ~62dp)
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(6.dp)
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF2A2A2A)
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
            // Logo - 40% larger
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
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

            // Channel name - larger text
            Text(
                text = channel.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
