package com.example.androidtviptvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing a popup message
 */
data class PopupMessage(
    val id: String,
    val title: String,
    val body: String,
    val url: String? = null
)

/**
 * Singleton manager for handling message popups efficiently
 * Uses StateFlow for reactive updates without memory leaks
 */
object MessagePopupManager {
    private val _currentMessage = MutableStateFlow<PopupMessage?>(null)
    val currentMessage: StateFlow<PopupMessage?> = _currentMessage.asStateFlow()

    private val messageQueue = mutableListOf<PopupMessage>()
    private val shownMessages = mutableSetOf<String>()

    fun showMessage(message: PopupMessage) {
        // Avoid showing duplicate messages
        if (message.id in shownMessages) return

        if (_currentMessage.value == null) {
            _currentMessage.value = message
            shownMessages.add(message.id)
        } else {
            // Queue for later
            if (message.id !in messageQueue.map { it.id }) {
                messageQueue.add(message)
            }
        }
    }

    fun dismissCurrent() {
        _currentMessage.value = null

        // Show next queued message if any
        if (messageQueue.isNotEmpty()) {
            val next = messageQueue.removeAt(0)
            shownMessages.add(next.id)
            _currentMessage.value = next
        }
    }

    fun clearAll() {
        _currentMessage.value = null
        messageQueue.clear()
    }
}

/**
 * Global message popup dialog - lightweight and efficient
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GlobalMessagePopup() {
    val message by MessagePopupManager.currentMessage.collectAsState()

    message?.let { msg ->
        Dialog(onDismissRequest = { MessagePopupManager.dismissCurrent() }) {
            Surface(
                modifier = Modifier
                    .width(400.dp)
                    .wrapContentHeight(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF1E1E2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = msg.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = msg.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { MessagePopupManager.dismissCurrent() }
                        ) {
                            Text("OK")
                        }

                        if (!msg.url.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    // Handle URL click if needed
                                    MessagePopupManager.dismissCurrent()
                                }
                            ) {
                                Text("Learn More")
                            }
                        }
                    }
                }
            }
        }
    }
}
