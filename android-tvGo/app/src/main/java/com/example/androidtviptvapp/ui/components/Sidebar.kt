package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
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
import androidx.tv.material3.*

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
        // App Logo
        val brandConfig = com.example.androidtviptvapp.data.TvRepository.appConfig
        if (brandConfig?.logoUrl != null) {
             coil.compose.AsyncImage(
                model = brandConfig.logoUrl,
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 8.dp)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        SidebarItem(
            icon = Icons.Default.Home,
            label = "Home",
            isSelected = selectedRoute == "home",
            onClick = { onNavigate("home") }
        )
        SidebarItem(
            icon = Icons.Default.LiveTv,
            label = "Channels",
            isSelected = selectedRoute == "channels",
            onClick = { onNavigate("channels") }
        )
        SidebarItem(
            icon = Icons.Default.Movie,
            label = "Movies",
            isSelected = selectedRoute == "movies",
            onClick = { onNavigate("movies") }
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
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 8.dp),
        colors = IconButtonDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Icon(icon, contentDescription = label)
    }
}
