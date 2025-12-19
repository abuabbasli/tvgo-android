package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.example.androidtviptvapp.data.Category

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryFilter(
    categories: List<Category>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TvLazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(categories) { category ->
            val isSelected = category.id == selectedCategory
            val icon = getCategoryIcon(category.id)

            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category.id) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.colors(
                    containerColor = Color(0xFF1E1E1E),
                    focusedContainerColor = Color(0xFF2A2A2A),
                    selectedContainerColor = Color(0xFFE0E0E0),
                    contentColor = Color(0xFFC0C0C0),
                    focusedContentColor = Color.White,
                    selectedContentColor = Color.Black,
                    focusedSelectedContainerColor = Color(0xFFFFFFFF),
                    focusedSelectedContentColor = Color.Black
                ),
                shape = FilterChipDefaults.shape(shape = CircleShape),
                border = FilterChipDefaults.border(
                    border = Border(BorderStroke(1.dp, Color(0x33FFFFFF))),
                    focusedBorder = Border(BorderStroke(1.dp, Color(0xFFE0E0E0))),
                    selectedBorder = Border.None,
                    focusedSelectedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
                ),
                scale = FilterChipDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp)
                )
            }
        }
    }
}

private fun getCategoryIcon(categoryId: String): ImageVector {
    return when (categoryId) {
        "all" -> Icons.Filled.Menu
        "favorites" -> Icons.Filled.Favorite
        "kids" -> Icons.Filled.ChildCare
        "sports" -> Icons.Filled.EmojiEvents
        "news" -> Icons.Filled.Newspaper
        "entertainment" -> Icons.Filled.SentimentVerySatisfied
        "movies" -> Icons.Filled.Movie
        else -> Icons.Filled.Settings
    }
}
