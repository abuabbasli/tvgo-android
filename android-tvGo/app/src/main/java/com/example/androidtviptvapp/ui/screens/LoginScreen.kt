package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.example.androidtviptvapp.data.TvRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dynamic Brand Configuration
    val brandConfig = TvRepository.appConfig

    // Focus requesters for TV remote navigation
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val loginButtonFocusRequester = remember { FocusRequester() }

    // Auto-focus username field on launch
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            usernameFocusRequester.requestFocus()
        } catch (e: Exception) {
            android.util.Log.w("LoginScreen", "Failed to request focus: ${e.message}")
        }
    }

    // Dark background matching mockup
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        // Login card container
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(480.dp)
                .background(
                    Color(0xFF1A1A1F),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF2A2A2F),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(48.dp)
        ) {
            // tvGO Logo - always use static logo on login screen
            com.example.androidtviptvapp.ui.components.AppLogo(
                size = 80.dp,
                forceStaticLogo = true,  // Always show static logo before login
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Welcome Back heading
            Text(
                text = "Welcome Back",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sign in to continue subheading
            Text(
                text = "Sign in to continue",
                fontSize = 16.sp,
                color = Color(0xFF9CA3AF)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Username Input Field (Light background with icon)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF2A2A2F),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Username",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                androidx.compose.material3.TextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = {
                        androidx.compose.material3.Text(
                            "Username",
                            color = Color(0xFF6B7280)
                        )
                    },
                    singleLine = true,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        passwordFocusRequester.requestFocus()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Password Input Field (Dark background with border and icon)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF1A1A1F),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF3A3A3F),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Password",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                androidx.compose.material3.TextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = {
                        androidx.compose.material3.Text(
                            "Password",
                            color = Color(0xFF6B7280)
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        scope.launch {
                            isLoading = true
                            error = null
                            val success = TvRepository.login(username, password, context)
                            isLoading = false
                            if (success) {
                                onLoginSuccess()
                            } else {
                                error = "Invalid Credentials"
                            }
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                )
            }

            // Error message
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    color = Color(0xFFEF4444),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sign In Button with arrow icon
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        val success = TvRepository.login(username, password, context)
                        isLoading = false
                        if (success) {
                            onLoginSuccess()
                        } else {
                            error = "Invalid Credentials"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(loginButtonFocusRequester),
                enabled = !isLoading,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text(
                        text = if (isLoading) "Signing in..." else "Sign In",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Try Demo Account link
            Text(
                text = "Try Demo Account",
                fontSize = 14.sp,
                color = Color(0xFF60A5FA),
                modifier = Modifier.clickable {
                    username = "demo"
                    password = "demo123"
                }
            )
        }
    }
}
