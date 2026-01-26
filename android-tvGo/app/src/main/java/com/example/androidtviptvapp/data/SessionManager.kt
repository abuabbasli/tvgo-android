package com.example.androidtviptvapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SessionManager - Handles persistent login session storage.
 * Uses EncryptedSharedPreferences for secure credential storage.
 */
object SessionManager {
    private const val PREFS_NAME = "tvgo_session"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private var prefs: SharedPreferences? = null

    /**
     * Initialize SessionManager with context.
     * Call this from Application.onCreate() or MainActivity.
     */
    fun init(context: Context) {
        if (prefs != null) return

        try {
            // Try to use encrypted preferences for security
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            // This can happen on some older TV boxes
            android.util.Log.w("SessionManager", "Encrypted prefs failed, using standard prefs: ${e.message}")
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Save login credentials after successful login
     */
    fun saveSession(username: String, password: String, authToken: String) {
        prefs?.edit()?.apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putString(KEY_AUTH_TOKEN, authToken)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
        android.util.Log.d("SessionManager", "Session saved for user: $username")
    }

    /**
     * Check if there's a saved session
     */
    fun hasSavedSession(): Boolean {
        return prefs?.getBoolean(KEY_IS_LOGGED_IN, false) == true
    }

    /**
     * Get saved username
     */
    fun getSavedUsername(): String? {
        return prefs?.getString(KEY_USERNAME, null)
    }

    /**
     * Get saved password
     */
    fun getSavedPassword(): String? {
        return prefs?.getString(KEY_PASSWORD, null)
    }

    /**
     * Get saved auth token
     */
    fun getSavedAuthToken(): String? {
        return prefs?.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Clear saved session (on logout or auth failure)
     */
    fun clearSession() {
        prefs?.edit()?.apply {
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            remove(KEY_AUTH_TOKEN)
            putBoolean(KEY_IS_LOGGED_IN, false)
            apply()
        }
        android.util.Log.d("SessionManager", "Session cleared")
    }

    /**
     * Attempt auto-login with saved credentials.
     * Returns true if auto-login succeeded, false otherwise.
     */
    suspend fun tryAutoLogin(context: Context): Boolean {
        if (!hasSavedSession()) {
            android.util.Log.d("SessionManager", "No saved session found")
            return false
        }

        val username = getSavedUsername()
        val password = getSavedPassword()

        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            android.util.Log.d("SessionManager", "Incomplete saved credentials")
            clearSession()
            return false
        }

        android.util.Log.d("SessionManager", "Attempting auto-login for user: $username")

        return try {
            val success = TvRepository.login(username, password, context)
            if (success) {
                android.util.Log.d("SessionManager", "Auto-login successful")
                // Update saved token with fresh one
                TvRepository.authToken?.let { token ->
                    saveSession(username, password, token)
                }
                true
            } else {
                android.util.Log.d("SessionManager", "Auto-login failed - credentials may have changed")
                clearSession()
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "Auto-login error: ${e.message}")
            // Don't clear session on network errors - user might just be offline
            false
        }
    }
}
