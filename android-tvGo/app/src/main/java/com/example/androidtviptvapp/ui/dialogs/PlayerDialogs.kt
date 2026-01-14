package com.example.androidtviptvapp.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.example.androidtviptvapp.player.AdaptExoPlayerView

/**
 * Audio Track Selection Dialog - OnTV-main style
 * Uses TrackInfo (new ontv-main pattern with TrackGroup)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudioTrackSelectionDialog(
    tracks: List<AdaptExoPlayerView.TrackInfo>,
    selectedTrack: AdaptExoPlayerView.TrackInfo?,
    onTrackSelected: (AdaptExoPlayerView.TrackInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Audio Track",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (tracks.isEmpty()) {
                    Text(
                        text = "No audio tracks available",
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tracks) { track ->
                            val isSelected = selectedTrack == track

                            Surface(
                                onClick = {
                                    onTrackSelected(track)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isSelected) Modifier.focusRequester(focusRequester) else Modifier),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.3f)
                                ),
                                border = if (isSelected) ClickableSurfaceDefaults.border(
                                    border = Border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                ) else ClickableSurfaceDefaults.border()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.name,
                                            color = Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        track.language?.let { lang ->
                                            Text(
                                                text = lang.uppercase(),
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}

/**
 * Resume Position Dialog - OnTV-main style "Resume from X:XX?"
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResumePositionDialog(
    savedPosition: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val formattedTime = remember(savedPosition) {
        val totalSeconds = savedPosition / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Resume Playback?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Continue from $formattedTime",
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = {
                        onStartOver()
                        onDismiss()
                    }) {
                        Text("Start Over", color = Color.White)
                    }

                    Button(
                        onClick = {
                            onResume()
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Text("Resume")
                    }
                }
            }
        }
    }
}
