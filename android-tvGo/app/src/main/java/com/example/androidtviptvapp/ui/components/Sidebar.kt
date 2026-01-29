package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Movie

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.SportsEsports

import androidx.compose.material.icons.outlined.Home

import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings

import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.SportsEsports

import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.filled.Tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke

import androidx.tv.material3.*

import com.example.androidtviptvapp.data.TvRepository

// Dark sidebar background color
private val SidebarBackground = Color(0xFF121218)
// Selected item color (light gray/white like screenshot)
private val SelectedColor = Color(0xFFE8E8E8)
// Unselected icon color
private val UnselectedGray = Color(0xFF5A5A65)
// Item background color (dark)
private val ItemBackground = Color(0xFF1E1E26)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Sidebar(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    viewMode: ViewMode = ViewMode.GRID,
    onViewModeChange: ((ViewMode) -> Unit)? = null,
    showViewToggle: Boolean = false
) {
    // Hide sidebar if on Login screen
    if (selectedRoute == "login") return

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(56.dp)  // Slimmer sidebar (was 72.dp)
            .background(SidebarBackground)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // App Logo
        AppLogo(
            modifier = Modifier.padding(bottom = 12.dp),
            size = 32.dp  // Smaller logo
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Home
        SidebarItem(
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Filled.Home,
            label = "Home",
            isSelected = selectedRoute == "home",
            onClick = { onNavigate("home") }
        )

        // Conditional Live TV (Channels)
        val showLiveTv = TvRepository.features?.enableLiveTv ?: true
        if (showLiveTv) {
            SidebarItem(
                icon = Icons.Outlined.Tv,
                selectedIcon = Icons.Filled.Tv,
                label = "Channels",
                isSelected = selectedRoute == "channels",
                onClick = { onNavigate("channels") }
            )
        }

        // Conditional VOD (Movies)
        val showVod = TvRepository.features?.enableVod ?: true
        if (showVod) {
            SidebarItem(
                icon = Icons.Outlined.Movie,
                selectedIcon = Icons.Filled.Movie,
                label = "Movies",
                isSelected = selectedRoute == "movies",
                onClick = { onNavigate("movies") }
            )
        }

        // Games
        SidebarItem(
            icon = Icons.Outlined.SportsEsports,
            selectedIcon = Icons.Filled.SportsEsports,
            label = "Games",
            isSelected = selectedRoute == "games",
            onClick = { onNavigate("games") }
        )

        // Messages
        SidebarItem(
            icon = Icons.Outlined.Email,
            selectedIcon = Icons.Filled.Email,
            label = "Messages",
            isSelected = selectedRoute == "messages",
            onClick = { onNavigate("messages") }
        )

        // Settings
        SidebarItem(
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings,
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
                    .background(ItemBackground)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Grid View Button
                IconButton(
                    onClick = { onViewModeChange(ViewMode.GRID) },
                    colors = IconButtonDefaults.colors(
                        containerColor = if (viewMode == ViewMode.GRID) SelectedColor else Color.Transparent,
                        contentColor = if (viewMode == ViewMode.GRID) Color(0xFF1A1A1A) else UnselectedGray,
                        focusedContainerColor = SelectedColor,
                        focusedContentColor = Color(0xFF1A1A1A)
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = "Grid View",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // List View Button
                IconButton(
                    onClick = { onViewModeChange(ViewMode.LIST) },
                    colors = IconButtonDefaults.colors(
                        containerColor = if (viewMode == ViewMode.LIST) SelectedColor else Color.Transparent,
                        contentColor = if (viewMode == ViewMode.LIST) Color(0xFF1A1A1A) else UnselectedGray,
                        focusedContainerColor = SelectedColor,
                        focusedContentColor = Color(0xFF1A1A1A)
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewList,
                        contentDescription = "List View",
                        modifier = Modifier.size(18.dp)
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
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Selected: light background with dark icon
    // Unselected: dark background with gray icon
    // Focused: white border
    val containerColor = if (isSelected) SelectedColor else ItemBackground
    val contentColor = if (isSelected) Color(0xFF1A1A1A) else UnselectedGray
    val currentIcon = if (isSelected) selectedIcon else icon

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .size(44.dp),  // Smaller items for slimmer sidebar
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = ItemBackground,
            focusedContentColor = Color.White,
            pressedContainerColor = SelectedColor,
            pressedContentColor = Color(0xFF1A1A1A)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(12.dp)
            ),
            border = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.05f
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = currentIcon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)  // Smaller icons
            )
        }
    }
}
