package com.example.androidtviptvapp.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.androidtviptvapp.data.AppConfig
import com.example.androidtviptvapp.data.api.ApiClient
import com.example.androidtviptvapp.data.api.GameItem
import kotlinx.coroutines.launch

// Helper to resolve relative image URLs to full URLs
private fun resolveImageUrl(url: String?): String? {
    if (url == null) return null
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        "${AppConfig.IMAGE_BASE_URL}$url"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun GamesScreen(
    onBackToHome: () -> Unit = {}
) {
    var games by remember { mutableStateOf<List<GameItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Fetch games from API
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val response = ApiClient.service.getGames()
            games = response.items
            categories = listOf("All") + response.categories
            errorMessage = null
        } catch (e: Exception) {
            android.util.Log.e("GamesScreen", "Failed to load games", e)
            errorMessage = "Failed to load games: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    // Filter games by category
    val filteredGames = remember(games, selectedCategory) {
        if (selectedCategory == "All") {
            games
        } else {
            games.filter { it.category == selectedCategory }
        }
    }
    
    // Handle back button
    BackHandler(enabled = true) {
        if (selectedGame != null) {
            selectedGame = null
        } else {
            onBackToHome()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        if (selectedGame != null) {
            // Show WebView for selected game
            GameWebView(
                game = selectedGame!!,
                onClose = { selectedGame = null }
            )
        } else {
            // Show game grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp)
            ) {
                // Header
                Text(
                    text = "🎮 Games",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Category filter
                if (categories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        items(categories) { category ->
                            CategoryChip(
                                label = category,
                                isSelected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
                
                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading games...", color = Color.Gray, fontSize = 18.sp)
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(errorMessage!!, color = Color.Red, fontSize = 16.sp)
                        }
                    }
                    filteredGames.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No games found", color = Color.Gray, fontSize = 18.sp)
                        }
                    }
                    else -> {
                        // Game grid with extra top padding to prevent clipping on scale
                        TvLazyVerticalGrid(
                            columns = TvGridCells.Fixed(6),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredGames) { game ->
                                GameCard(
                                    game = game,
                                    onClick = { selectedGame = game }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E1E1E),
            focusedContainerColor = Color(0xFF3B82F6)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),  // White border like rest of app
                shape = RoundedCornerShape(20.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.01f  // Subtle scale like rest of app
        )
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(
    game: GameItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .onFocusChanged { isFocused = it.isFocused },
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1A1A2E),
            focusedContainerColor = Color(0xFF1A1A2E)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),  // White border like rest of app
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.01f  // Reduced from 1.05f to match app styling
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Game image - always try to load if URL exists
            val imageUrl = resolveImageUrl(game.imageUrl)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        android.util.Log.e("GameCard", "Failed to load image: $imageUrl")
                    }
                )
            }
            
            // Show placeholder/gradient on top if no image
            if (game.imageUrl == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2A2A4A), Color(0xFF1A1A2E))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎮",
                        fontSize = 48.sp
                    )
                }
            }
            
            // Gradient overlay for text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = game.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameWebView(
    game: GameItem,
    onClose: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Validate URL before loading
    val isValidUrl = remember(game.gameUrl) {
        game.gameUrl.isNotBlank() &&
        (game.gameUrl.startsWith("http://") || game.gameUrl.startsWith("https://"))
    }

    // Handle back button
    BackHandler(enabled = true) {
        onClose()
    }

    // Show error if URL is invalid
    if (!isValidUrl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Invalid game URL",
                    color = Color.Red,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = onClose,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF3B82F6),
                        focusedContainerColor = Color(0xFF60A5FA)
                    )
                ) {
                    Text(
                        text = "Go Back",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Navigation bar
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A2E))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onClose() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White
                            )
                        }
                        
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color(0xFF3B82F6)
                            )
                        }
                    }
                    
                    Text(
                        text = game.name,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFF3B82F6))
                )
            }
            
            // WebView with error handling
            if (hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "Failed to load game",
                            color = Color.Red,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                onClick = {
                                    hasError = false
                                    errorMessage = null
                                    webView?.reload()
                                },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF3B82F6),
                                    focusedContainerColor = Color(0xFF60A5FA)
                                )
                            ) {
                                Text(
                                    text = "Retry",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                            Surface(
                                onClick = onClose,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF374151),
                                    focusedContainerColor = Color(0xFF4B5563)
                                )
                            ) {
                                Text(
                                    text = "Go Back",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            // Forward D-pad to WebView
                            when (keyEvent.key) {
                                Key.DirectionUp, Key.DirectionDown,
                                Key.DirectionLeft, Key.DirectionRight,
                                Key.Enter, Key.NumPadEnter -> {
                                    val androidKeyCode = when (keyEvent.key) {
                                        Key.DirectionUp -> KeyEvent.KEYCODE_DPAD_UP
                                        Key.DirectionDown -> KeyEvent.KEYCODE_DPAD_DOWN
                                        Key.DirectionLeft -> KeyEvent.KEYCODE_DPAD_LEFT
                                        Key.DirectionRight -> KeyEvent.KEYCODE_DPAD_RIGHT
                                        Key.Enter, Key.NumPadEnter -> KeyEvent.KEYCODE_DPAD_CENTER
                                        else -> return@onKeyEvent false
                                    }
                                    val action = if (keyEvent.type == KeyEventType.KeyDown)
                                        KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                                    webView?.dispatchKeyEvent(KeyEvent(action, androidKeyCode))
                                    true
                                }
                                else -> false
                            }
                        },
                    factory = { context ->
                        try {
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                isFocusable = true
                                isFocusableInTouchMode = true

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                                    javaScriptCanOpenWindowsAutomatically = true
                                }

                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false

                                        // Inject focus styles
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var style = document.createElement('style');
                                                style.textContent = `
                                                    *:focus {
                                                        outline: 3px solid #3B82F6 !important;
                                                        outline-offset: 2px !important;
                                                        box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.4) !important;
                                                    }
                                                `;
                                                document.head.appendChild(style);
                                            })();
                                        """.trimIndent(), null)
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        super.onReceivedError(view, errorCode, description, failingUrl)
                                        android.util.Log.e("GameWebView", "Error loading game: $description (code: $errorCode)")
                                        hasError = true
                                        errorMessage = description ?: "Failed to load game"
                                        isLoading = false
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {}

                                loadUrl(game.gameUrl)
                                webView = this
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GameWebView", "Failed to create WebView", e)
                            hasError = true
                            errorMessage = "Failed to initialize game: ${e.message}"
                            // Return a placeholder WebView to avoid crash
                            WebView(context)
                        }
                    },
                    update = { view -> webView = view }
                )
            }
        }
        
        // Exit fullscreen button
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                IconButton(onClick = { isFullscreen = false }) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
