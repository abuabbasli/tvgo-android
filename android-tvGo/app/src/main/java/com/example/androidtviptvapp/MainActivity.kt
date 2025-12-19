package com.example.androidtviptvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.androidtviptvapp.ui.AppNavigation
import com.example.androidtviptvapp.ui.Routes
import com.example.androidtviptvapp.ui.components.Sidebar
import com.example.androidtviptvapp.ui.components.ViewMode
import com.example.androidtviptvapp.ui.theme.AndroidTvIptvAppTheme

import com.example.androidtviptvapp.data.TvRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initial Data Load from API
        TvRepository.loadData()
        
        setContent {
            @OptIn(ExperimentalTvMaterial3Api::class)
            AndroidTvIptvAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME
                    
                    // Global view mode state for channels screen
                    var channelViewMode by remember { mutableStateOf(ViewMode.GRID) }
                    
                    val isPlayerScreen = currentRoute?.startsWith("player") == true

                    if (isPlayerScreen) {
                        // Full screen player - no sidebar, no Row layout
                        AppNavigation(
                            navController = navController,
                            channelViewMode = channelViewMode,
                            onPlayUrl = { url ->
                                val encodedUrl = android.util.Base64.encodeToString(url.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                                navController.navigate("player/$encodedUrl")
                            },
                            onPlayChannel = { channelId ->
                                navController.navigate("player_channel/$channelId")
                            }
                        )
                    } else {
                        // Normal screens with sidebar
                        Row(modifier = Modifier.fillMaxSize()) {
                            Sidebar(
                                selectedRoute = currentRoute ?: Routes.HOME,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                viewMode = channelViewMode,
                                onViewModeChange = { channelViewMode = it },
                                showViewToggle = currentRoute == Routes.CHANNELS
                            )
                            
                            AppNavigation(
                                navController = navController,
                                channelViewMode = channelViewMode,
                                onPlayUrl = { url ->
                                    val encodedUrl = android.util.Base64.encodeToString(url.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                                    navController.navigate("player/$encodedUrl")
                                },
                                onPlayChannel = { channelId ->
                                    navController.navigate("player_channel/$channelId")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
