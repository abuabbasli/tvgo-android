package com.example.androidtviptvapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
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
    
    // Dynamic Brand Configuration
    val brandConfig = TvRepository.appConfig

    // Keyboard Controller
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .width(400.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(vertical = 32.dp) // Extra padding for scroll
        ) {
            // Brand Logo
            if (brandConfig?.logoUrl != null) {
                AsyncImage(
                    model = brandConfig.logoUrl,
                    contentDescription = "Logo",
                    modifier = Modifier.height(100.dp).padding(bottom = 32.dp)
                )
            } else {
               Text("TV GO", style = MaterialTheme.typography.displayMedium)
            }

            Text("Subscriber Login", style = MaterialTheme.typography.headlineMedium)

            // Inputs
            androidx.compose.material3.TextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            androidx.compose.material3.TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    scope.launch {
                        isLoading = true
                        val success = TvRepository.login(username, password)
                        isLoading = false
                        if (success) {
                            onLoginSuccess()
                        } else {
                            error = "Login Failed"
                        }
                    }
                }),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                 Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            // Login Button
            Button(
                onClick = { 
                    keyboardController?.hide()
                    scope.launch {
                        isLoading = true
                        error = null
                        val success = TvRepository.login(username, password)
                        isLoading = false
                        if (success) {
                            onLoginSuccess()
                        } else {
                            error = "Invalid Credentials"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    Text("Logging in...")
                } else {
                    Text("Login")
                }
            }
            
            Spacer(modifier = Modifier.height(200.dp)) // Extra space to push content up when scrolled
        }
    }
}
