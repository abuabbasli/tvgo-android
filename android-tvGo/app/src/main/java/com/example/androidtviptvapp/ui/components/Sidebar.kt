package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.androidtviptvapp.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.androidtviptvapp.data.TvRepository

/**
 * App Logo component - displays either remote logo from config or local drawable
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = LocalContext.current
    val logoUrl = TvRepository.appConfig?.logoUrl

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrEmpty()) {
            // Load logo from server
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "App Logo",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback to local drawable
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "App Logo",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Sidebar(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    viewMode: ViewMode = ViewMode.GRID,
    onViewModeChange: ((ViewMode) -> Unit)? = null,
    showViewToggle: Boolean = false
) {
    // Hide sidebar if on Login screen (should be handled by parent but extra check)
    if (selectedRoute == "login") return

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // App Logo - using dedicated component
        AppLogo(
            modifier = Modifier.padding(bottom = 8.dp),
            size = 48.dp
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        SidebarItem(
            icon = Icons.Default.Home,
            label = "Home",
            isSelected = selectedRoute == "home",
            onClick = { onNavigate("home") }
        )
        
        // Conditional Live TV
        val showLiveTv = com.example.androidtviptvapp.data.TvRepository.features?.enableLiveTv ?: true
        if (showLiveTv) {
            SidebarItem(
                icon = Icons.Default.LiveTv,
                label = "Channels",
                isSelected = selectedRoute == "channels",
                onClick = { onNavigate("channels") }
            )
        }

        // Conditional VOD
        val showVod = com.example.androidtviptvapp.data.TvRepository.features?.enableVod ?: true
        if (showVod) {
            SidebarItem(
                icon = Icons.Default.Movie,
                label = "Movies",
                isSelected = selectedRoute == "movies",
                onClick = { onNavigate("movies") }
            )
        }
        
        // Games section
        SidebarItem(
            icon = Icons.Default.SportsEsports,
            label = "Games",
            isSelected = selectedRoute == "games",
            onClick = { onNavigate("games") }
        )
        
        SidebarItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            isSelected = selectedRoute == "settings",
            onClick = { onNavigate("settings") }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // View Mode Toggle at bottom (only shown on channels screen)
        if (showViewToggle && onViewModeChange != null) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Grid View Button
                IconButton(
                    onClick = { onViewModeChange(ViewMode.GRID) },
                    colors = IconButtonDefaults.colors(
                        containerColor = if (viewMode == ViewMode.GRID) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        contentColor = if (viewMode == ViewMode.GRID) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = "Grid View",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // List View Button
                IconButton(
                    onClick = { onViewModeChange(ViewMode.LIST) },
                    colors = IconButtonDefaults.colors(
                        containerColor = if (viewMode == ViewMode.LIST) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        contentColor = if (viewMode == ViewMode.LIST) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewList,
                        contentDescription = "List View",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SidebarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Determine colors based on selection state manually
    // because standard Surface/ClickableSurfaceDefaults doesn't handle "selected" state effectively for colors
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val focusedContentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)), // Pill shape
        modifier = Modifier.padding(vertical = 4.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
            pressedContainerColor = containerColor,
            pressedContentColor = contentColor
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            )
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                elevation = 12.dp
            )
        )
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
