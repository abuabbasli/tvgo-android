package com.example.androidtviptvapp.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.example.androidtviptvapp.data.api.ApiClient
import com.example.androidtviptvapp.data.api.MessageItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen() {
    var messages by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedMessageId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            android.util.Log.d("MessagesScreen", "Fetching broadcast messages...")
            // Use broadcast messages endpoint - doesn't require authentication
            val response = ApiClient.service.getBroadcastMessages()
            android.util.Log.d("MessagesScreen", "Received ${response.items.size} messages")
            response.items.forEach { msg ->
                android.util.Log.d("MessagesScreen", "Message: ${msg.title}, URL: ${msg.url}")
            }
            messages = response.items
            loading = false
        } catch (e: Exception) {
            android.util.Log.e("MessagesScreen", "Failed to load messages: ${e.message}", e)
            error = e.message ?: "Failed to load messages"
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF0A0A0A))
            .padding(32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = "Messages",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading messages...",
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = error ?: "Error",
                            color = androidx.compose.ui.graphics.Color.Red
                        )
                    }
                }
                messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = androidx.compose.ui.graphics.Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No messages yet",
                                color = androidx.compose.ui.graphics.Color.Gray,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageCard(
                                message = message,
                                isExpanded = expandedMessageId == message.id,
                                onExpandToggle = {
                                    expandedMessageId = if (expandedMessageId == message.id) null else message.id
                                    // Mark as read
                                    if (!message.isRead) {
                                        scope.launch {
                                            try {
                                                ApiClient.service.markMessageRead(message.id)
                                            } catch (e: Exception) {
                                                // Silent fail - marking as read is not critical
                                                android.util.Log.w("MessagesScreen", "Failed to mark message read: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MessageCard(
    message: MessageItem,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    Surface(
        onClick = onExpandToggle,
        modifier = Modifier
            .fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
            focusedContainerColor = androidx.compose.ui.graphics.Color(0xFF2D4A6F),
            pressedContainerColor = androidx.compose.ui.graphics.Color(0xFF3B5998),
            contentColor = androidx.compose.ui.graphics.Color.White,
            focusedContentColor = androidx.compose.ui.graphics.Color.White,
            pressedContentColor = androidx.compose.ui.graphics.Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(3.dp, androidx.compose.ui.graphics.Color(0xFF60A5FA)),
                shape = RoundedCornerShape(12.dp)
            ),
            pressedBorder = Border(
                border = BorderStroke(3.dp, androidx.compose.ui.graphics.Color(0xFF3B82F6)),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(
            scale = 1f,
            focusedScale = 1.02f,
            pressedScale = 0.98f
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (message.isRead) Icons.Outlined.MarkEmailRead else Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (message.isRead) 
                            androidx.compose.ui.graphics.Color.Gray 
                        else 
                            androidx.compose.ui.graphics.Color(0xFF3B82F6)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message.title,
                        fontSize = 18.sp,
                        fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (message.url != null) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Has QR Code",
                        modifier = Modifier.size(20.dp),
                        tint = androidx.compose.ui.graphics.Color(0xFF10B981)
                    )
                }
            }
            
            if (!isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message.body,
                    fontSize = 14.sp,
                    color = androidx.compose.ui.graphics.Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = message.body,
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.LightGray,
                    lineHeight = 24.sp
                )
                
                // Show QR Code if URL is present
                if (message.url != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            QRCodeImage(
                                url = message.url,
                                size = 200
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Scan to open link",
                                fontSize = 14.sp,
                                color = androidx.compose.ui.graphics.Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.url,
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF3B82F6),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // Date
                if (message.createdAt != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Received: ${formatDate(message.createdAt)}",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun QRCodeImage(url: String, size: Int) {
    val bitmap = remember(url, size) {
        generateQRCode(url, size)
    }
    
    if (bitmap != null) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(8.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code for $url",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun generateQRCode(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun formatDate(isoDate: String): String {
    return try {
        // Simple date formatting - just show date part
        isoDate.substringBefore("T").replace("-", "/")
    } catch (e: Exception) {
        isoDate
    }
}
