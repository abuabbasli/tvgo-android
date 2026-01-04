package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.androidtviptvapp.data.Channel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListItem(
    channel: Channel,
    onClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    
    ListItem(
        selected = false,
        onClick = { onClick(channel) },
        headlineContent = {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) Color.White else Color(0xFFE0E0E0)
            )
        },
        supportingContent = {
            // Show current program title if available
            val currentProgram = com.example.androidtviptvapp.data.TvRepository.currentPrograms[channel.id]
            Text(
                text = currentProgram?.title ?: channel.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (currentProgram != null) Color(0xFF60A5FA) else Color(0xFF9CA3AF),
                maxLines = 1
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White), // Logo bg remains white to show logo clearly
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(channel.logo)
                        .apply {
                            if (channel.logo.endsWith(".svg", ignoreCase = true)) {
                                decoderFactory(SvgDecoder.Factory())
                            }
                        }
                        .crossfade(true)
                        .build(),
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(4.dp),
                    loading = {
                        Text(
                            text = channel.name.take(2).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                    },
                    error = {
                        Text(
                            text = channel.name.take(2).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.DarkGray
                        )
                    }
                )
            }
        },
        trailingContent = {
             Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Star,
                contentDescription = "Favorite",
                tint = if (isFocused) Color.White else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF2A2A2A), // Dark gray focus
            selectedContainerColor = Color(0xFF2A2A2A),
            contentColor = Color(0xFFE0E0E0),
            focusedContentColor = Color.White
        ),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
    )
}
