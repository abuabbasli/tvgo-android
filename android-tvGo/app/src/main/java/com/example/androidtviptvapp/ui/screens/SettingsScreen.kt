package com.example.androidtviptvapp.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.example.androidtviptvapp.data.TvRepository

/**
 * Baby Lock Manager - handles baby mode state and password
 */
object BabyLockManager {
    private const val TAG = "BabyLockManager"
    private const val PREFS_NAME = "baby_lock_prefs"
    private const val KEY_BABY_MODE_ACTIVE = "baby_mode_active"
    private const val KEY_BABY_LOCK_PIN = "baby_lock_pin"
    private const val DEFAULT_PIN = "1234"

    private var _isBabyModeActive = mutableStateOf(false)
    val isBabyModeActive: Boolean get() = _isBabyModeActive.value

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isBabyModeActive.value = prefs.getBoolean(KEY_BABY_MODE_ACTIVE, false)
        android.util.Log.d(TAG, "Initialized - baby mode active: ${_isBabyModeActive.value}")
    }

    fun isInitialized(): Boolean {
        return ::prefs.isInitialized
    }

    /**
     * Check if the server has flagged baby lock for reset.
     * Call this after login to sync with admin-triggered resets.
     */
    suspend fun checkAndApplyServerReset() {
        try {
            val profile = com.example.androidtviptvapp.data.api.ApiClient.service.getSubscriberProfile()
            if (profile.babyLockResetPending) {
                android.util.Log.d(TAG, "Server flagged baby lock reset - applying reset")
                resetBabyLock()
                // Clear the flag on server
                com.example.androidtviptvapp.data.api.ApiClient.service.clearBabyLockReset()
                android.util.Log.d(TAG, "Baby lock reset applied and server flag cleared")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to check baby lock reset status: ${e.message}")
            // Non-critical - continue without checking
        }
    }

    fun getPin(): String {
        return if (::prefs.isInitialized) {
            prefs.getString(KEY_BABY_LOCK_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        } else DEFAULT_PIN
    }

    fun setPin(newPin: String) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_BABY_LOCK_PIN, newPin).apply()
        }
    }

    fun verifyPin(pin: String): Boolean {
        return pin == getPin()
    }

    fun activateBabyMode() {
        _isBabyModeActive.value = true
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_BABY_MODE_ACTIVE, true).apply()
        }
        android.util.Log.d("BabyLock", "Baby mode activated")
    }

    fun deactivateBabyMode() {
        _isBabyModeActive.value = false
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_BABY_MODE_ACTIVE, false).apply()
        }
        android.util.Log.d("BabyLock", "Baby mode deactivated")
    }

    fun resetBabyLock() {
        _isBabyModeActive.value = false
        if (::prefs.isInitialized) {
            prefs.edit()
                .putBoolean(KEY_BABY_MODE_ACTIVE, false)
                .putString(KEY_BABY_LOCK_PIN, DEFAULT_PIN)
                .apply()
        }
        android.util.Log.d("BabyLock", "Baby lock reset to defaults")
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current

    // Initialize BabyLockManager
    LaunchedEffect(Unit) {
        BabyLockManager.init(context)
        android.util.Log.d("SettingsScreen", "Baby mode state after init: ${BabyLockManager.isBabyModeActive}")
    }

    // State for dialogs
    var showBabyLockDialog by remember { mutableStateOf(false) }
    var babyLockAction by remember { mutableStateOf<BabyLockAction>(BabyLockAction.None) }

    // Observe baby mode state
    val isBabyModeActive = BabyLockManager.isBabyModeActive

    // Debug log whenever state changes
    LaunchedEffect(isBabyModeActive) {
        android.util.Log.d("SettingsScreen", "Baby mode active changed to: $isBabyModeActive")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 80.dp, vertical = 32.dp)
        ) {
            // ── Profile Mode Section ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Profile Mode",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Profile Mode",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                ProfileModeCard(
                    icon = Icons.Default.Person,
                    title = "Parent Mode",
                    description = "Full access to all content and settings",
                    isSelected = !isBabyModeActive,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isBabyModeActive) {
                            babyLockAction = BabyLockAction.Deactivate
                            showBabyLockDialog = true
                        }
                    },
                    onLongClick = {
                        BabyLockManager.deactivateBabyMode()
                    }
                )

                ProfileModeCard(
                    icon = Icons.Default.ChildCare,
                    title = "Child Mode",
                    description = "Kid-safe content and limited access",
                    isSelected = isBabyModeActive,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!isBabyModeActive) {
                            babyLockAction = BabyLockAction.Activate
                            showBabyLockDialog = true
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info Box - compact
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF232323))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Parent Mode: full access.  Child Mode: age-appropriate content only.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Channel View Size Section ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Channel View Size",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Channel View Size",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Size option cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                val currentSize = com.example.androidtviptvapp.data.UIPreferencesManager.channelViewSize

                ViewSizeCard(
                    icon = Icons.Default.ZoomOut,
                    title = "Small",
                    description = "Compact · More channels",
                    isSelected = currentSize == com.example.androidtviptvapp.data.ChannelViewSize.SMALL,
                    onClick = { com.example.androidtviptvapp.data.UIPreferencesManager.setViewSize(com.example.androidtviptvapp.data.ChannelViewSize.SMALL) },
                    modifier = Modifier.weight(1f)
                )

                ViewSizeCard(
                    icon = Icons.Default.Tv,
                    title = "Medium",
                    description = "Default · Balanced",
                    isSelected = currentSize == com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM,
                    onClick = { com.example.androidtviptvapp.data.UIPreferencesManager.setViewSize(com.example.androidtviptvapp.data.ChannelViewSize.MEDIUM) },
                    modifier = Modifier.weight(1f)
                )

                ViewSizeCard(
                    icon = Icons.Default.ZoomIn,
                    title = "Large",
                    description = "Larger · Fewer channels",
                    isSelected = currentSize == com.example.androidtviptvapp.data.ChannelViewSize.LARGE,
                    onClick = { com.example.androidtviptvapp.data.UIPreferencesManager.setViewSize(com.example.androidtviptvapp.data.ChannelViewSize.LARGE) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Logout Button ── prominent, full width
            Surface(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF7F1D1D),
                    focusedContainerColor = Color(0xFFDC2626)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFEF4444))
                    ),
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF991B1B))
                    )
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Log Out",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Baby Lock PIN Dialog
    if (showBabyLockDialog) {
        ModernPinDialog(
            action = babyLockAction,
            onSuccess = {
                when (babyLockAction) {
                    BabyLockAction.Activate -> BabyLockManager.activateBabyMode()
                    BabyLockAction.Deactivate -> BabyLockManager.deactivateBabyMode()
                    else -> {}
                }
                showBabyLockDialog = false
                babyLockAction = BabyLockAction.None
            },
            onDismiss = {
                showBabyLockDialog = false
                babyLockAction = BabyLockAction.None
            }
        )
    }
}

enum class BabyLockAction {
    None, Activate, Deactivate
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(180.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFFE0E0E0) else Color(0xFF2A2A2A),
            focusedContainerColor = if (isSelected) Color(0xFFF0F0F0) else Color(0xFF3A3A3A)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    3.dp,
                    if (isSelected) Color.White else Color(0xFF60A5FA)
                )
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon in circle
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFFB0B0B0) else Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color.Black else Color(0xFF9CA3AF),
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                color = if (isSelected) Color(0xFF666666) else Color(0xFF9CA3AF),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ViewSizeCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFFE0E0E0) else Color(0xFF2A2A2A),
            focusedContainerColor = if (isSelected) Color(0xFFF0F0F0) else Color(0xFF3A3A3A)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    3.dp,
                    if (isSelected) Color.White else Color(0xFF60A5FA)
                )
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) Color.Black else Color(0xFF9CA3AF),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.Black else Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isSelected) Color(0xFF666666) else Color(0xFF9CA3AF),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModernPinDialog(
    action: BabyLockAction,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val title = when (action) {
        BabyLockAction.Activate -> "Enter PIN to switch to Parent Mode"
        BabyLockAction.Deactivate -> "Enter PIN to switch to Parent Mode"
        else -> "Enter PIN Code"
    }

    // Request focus when dialog opens
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            android.util.Log.w("SettingsScreen", "Failed to request focus on dialog: ${e.message}")
        }
    }

    // Helper function to handle PIN input
    fun handlePinInput(digit: String) {
        if (pin.length < 4) {
            pin += digit
            error = null
            // Auto-verify when 4 digits entered
            if (pin.length == 4) {
                if (BabyLockManager.verifyPin(pin)) {
                    when (action) {
                        BabyLockAction.Activate -> BabyLockManager.activateBabyMode()
                        BabyLockAction.Deactivate -> BabyLockManager.deactivateBabyMode()
                        else -> {}
                    }
                    onSuccess()
                } else {
                    error = "Incorrect PIN"
                    pin = ""
                }
            }
        }
    }

    // Helper function to handle backspace
    fun handleBackspace() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            error = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2A2A2A))
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    // Capture remote control number keys
                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        when (event.key) {
                            androidx.compose.ui.input.key.Key.Zero, androidx.compose.ui.input.key.Key.NumPad0 -> {
                                handlePinInput("0")
                                true
                            }
                            androidx.compose.ui.input.key.Key.One, androidx.compose.ui.input.key.Key.NumPad1 -> {
                                handlePinInput("1")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Two, androidx.compose.ui.input.key.Key.NumPad2 -> {
                                handlePinInput("2")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Three, androidx.compose.ui.input.key.Key.NumPad3 -> {
                                handlePinInput("3")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Four, androidx.compose.ui.input.key.Key.NumPad4 -> {
                                handlePinInput("4")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Five, androidx.compose.ui.input.key.Key.NumPad5 -> {
                                handlePinInput("5")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Six, androidx.compose.ui.input.key.Key.NumPad6 -> {
                                handlePinInput("6")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Seven, androidx.compose.ui.input.key.Key.NumPad7 -> {
                                handlePinInput("7")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Eight, androidx.compose.ui.input.key.Key.NumPad8 -> {
                                handlePinInput("8")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Nine, androidx.compose.ui.input.key.Key.NumPad9 -> {
                                handlePinInput("9")
                                true
                            }
                            androidx.compose.ui.input.key.Key.Backspace, androidx.compose.ui.input.key.Key.Delete -> {
                                handleBackspace()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .focusable()
        ) {
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lock Icon
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Enter PIN Code",
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please enter your PIN to switch to Parent Mode",
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E26))
                                .border(
                                    2.dp,
                                    if (index == pin.length) Color.White else Color(0xFF3A3A3A),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < pin.length) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Demo PIN hint
                Text(
                    text = "Demo PIN: 1234",
                    color = Color(0xFF60A5FA),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Number Pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Rows 1-3
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { digit ->
                                PinNumberButton(
                                    digit = digit,
                                    onClick = { handlePinInput(digit) }
                                )
                            }
                        }
                    }

                    // Bottom row: 0, Backspace
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(62.dp)) // Spacer
                        PinNumberButton(
                            digit = "0",
                            onClick = { handlePinInput("0") }
                        )
                        PinNumberButton(
                            digit = "⌫",
                            onClick = { handleBackspace() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PinNumberButton(
    digit: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(62.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF3A3A3A),
            focusedContainerColor = Color(0xFF4A4A4A)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
            )
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = digit,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
