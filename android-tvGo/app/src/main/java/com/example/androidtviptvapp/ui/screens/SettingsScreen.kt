package com.example.androidtviptvapp.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    }

    // State for dialogs
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showBabyLockDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var babyLockAction by remember { mutableStateOf<BabyLockAction>(BabyLockAction.None) }

    // Observe baby mode state
    val isBabyModeActive = BabyLockManager.isBabyModeActive

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Title
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Settings items
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Baby Lock Section
            Text(
                text = "Parental Controls",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Baby Mode Toggle
            SettingsItem(
                icon = Icons.Default.ChildCare,
                title = "Baby Mode",
                subtitle = if (isBabyModeActive) "Active - Only kids content shown" else "Tap to enable",
                isHighlighted = isBabyModeActive,
                onClick = {
                    if (isBabyModeActive) {
                        // Deactivating - need PIN
                        babyLockAction = BabyLockAction.Deactivate
                        showBabyLockDialog = true
                    } else {
                        // Activating - need PIN to set
                        babyLockAction = BabyLockAction.Activate
                        showBabyLockDialog = true
                    }
                }
            )

            // Change PIN (only visible when baby mode is not active)
            if (!isBabyModeActive) {
                SettingsItem(
                    icon = Icons.Default.Pin,
                    title = "Change Baby Lock PIN",
                    subtitle = "Change the 4-digit PIN for baby mode",
                    onClick = {
                        showChangePinDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Account Section
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Logout
            SettingsItem(
                icon = Icons.Default.Logout,
                title = "Log Out",
                subtitle = "Sign out of your account",
                isDanger = true,
                onClick = { showLogoutDialog = true }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // App Info
        Text(
            text = "tvGO v1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        ConfirmationDialog(
            title = "Log Out",
            message = "Are you sure you want to log out?",
            confirmText = "Log Out",
            onConfirm = {
                TvRepository.logout()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    // Baby Lock PIN Dialog
    if (showBabyLockDialog) {
        BabyLockPinDialog(
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

    // Change PIN Dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            onSuccess = { showChangePinDialog = false },
            onDismiss = { showChangePinDialog = false }
        )
    }
}

enum class BabyLockAction {
    None, Activate, Deactivate
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isHighlighted: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    // Use sidebar-like colors - no red, use white/gray theme
    val backgroundColor = when {
        isHighlighted -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        else -> Color(0xFF1E1E26)  // Match sidebar item background
    }

    val iconTint = when {
        isHighlighted -> Color(0xFF4CAF50)
        else -> Color(0xFF9CA3AF)  // Gray like sidebar
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = Color(0xFF2A2A2A),  // Slightly lighter on focus
            pressedContainerColor = backgroundColor
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    Color.White  // White border like sidebar
                )
            )
        ),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.01f  // Very subtle scale - reduced from default
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(28.dp)  // Slightly smaller icon
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White  // White text like sidebar
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)  // Gray subtitle
                )
            }

            if (isHighlighted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFE53935)
                        )
                    ) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BabyLockPinDialog(
    action: BabyLockAction,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val title = when (action) {
        BabyLockAction.Activate -> "Enter PIN to Enable Baby Mode"
        BabyLockAction.Deactivate -> "Enter PIN to Disable Baby Mode"
        else -> "Enter PIN"
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Input - 4 digit boxes
                PinInput(
                    pin = pin,
                    onPinChange = { newPin ->
                        if (newPin.length <= 4 && newPin.all { it.isDigit() }) {
                            pin = newPin
                            error = null
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester)
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = Color(0xFFE53935),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (pin.length != 4) {
                                error = "PIN must be 4 digits"
                            } else if (!BabyLockManager.verifyPin(pin)) {
                                error = "Incorrect PIN"
                                pin = ""
                            } else {
                                onSuccess()
                            }
                        },
                        enabled = pin.length == 4
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChangePinDialog(
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1 = current, 2 = new, 3 = confirm
    var error by remember { mutableStateOf<String?>(null) }

    val title = when (step) {
        1 -> "Enter Current PIN"
        2 -> "Enter New PIN"
        3 -> "Confirm New PIN"
        else -> ""
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Pin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                // Progress indicator
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (index < step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val currentValue = when (step) {
                    1 -> currentPin
                    2 -> newPin
                    3 -> confirmPin
                    else -> ""
                }

                PinInput(
                    pin = currentValue,
                    onPinChange = { newValue ->
                        if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                            error = null
                            when (step) {
                                1 -> currentPin = newValue
                                2 -> newPin = newValue
                                3 -> confirmPin = newValue
                            }
                        }
                    }
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = Color(0xFFE53935),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (step > 1) {
                                step--
                                error = null
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(if (step > 1) "Back" else "Cancel")
                    }

                    Button(
                        onClick = {
                            when (step) {
                                1 -> {
                                    if (currentPin.length != 4) {
                                        error = "PIN must be 4 digits"
                                    } else if (!BabyLockManager.verifyPin(currentPin)) {
                                        error = "Incorrect PIN"
                                        currentPin = ""
                                    } else {
                                        step = 2
                                    }
                                }
                                2 -> {
                                    if (newPin.length != 4) {
                                        error = "PIN must be 4 digits"
                                    } else {
                                        step = 3
                                    }
                                }
                                3 -> {
                                    if (confirmPin != newPin) {
                                        error = "PINs do not match"
                                        confirmPin = ""
                                    } else {
                                        BabyLockManager.setPin(newPin)
                                        onSuccess()
                                    }
                                }
                            }
                        },
                        enabled = currentValue.length == 4
                    ) {
                        Text(if (step == 3) "Save" else "Next")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PinInput(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Visual PIN display (4 boxes)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (index < pin.length) {
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Number pad
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1-3
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { digit ->
                    NumberButton(
                        digit = digit,
                        onClick = {
                            if (pin.length < 4) {
                                onPinChange(pin + digit)
                            }
                        }
                    )
                }
            }
        }

        // Bottom row: Clear, 0, Backspace
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NumberButton(
                digit = "C",
                onClick = { onPinChange("") }
            )
            NumberButton(
                digit = "0",
                onClick = {
                    if (pin.length < 4) {
                        onPinChange(pin + "0")
                    }
                }
            )
            NumberButton(
                digit = "⌫",
                onClick = {
                    if (pin.isNotEmpty()) {
                        onPinChange(pin.dropLast(1))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NumberButton(
    digit: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                )
            )
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
