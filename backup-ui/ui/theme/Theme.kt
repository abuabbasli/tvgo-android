package com.example.androidtviptvapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun AndroidTvIptvAppTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = PrimaryAccent,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        background = AppBackground,
        surface = SurfaceColor,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = OnSurfaceColor,
        onSurface = OnSurfaceColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.tv.material3.Typography(),
        content = content
    )
}
