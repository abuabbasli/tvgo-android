package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme

enum class ViewMode { GRID, LIST }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ViewModeToggle(
    currentMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Grid View Button
        IconButton(
            onClick = { onModeChange(ViewMode.GRID) },
            colors = IconButtonDefaults.colors(
                containerColor = if (currentMode == ViewMode.GRID) Color.Transparent else Color.Transparent,
                contentColor = if (currentMode == ViewMode.GRID) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
            onClick = { onModeChange(ViewMode.LIST) },
            colors = IconButtonDefaults.colors(
                containerColor = if (currentMode == ViewMode.LIST) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                contentColor = if (currentMode == ViewMode.LIST) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
