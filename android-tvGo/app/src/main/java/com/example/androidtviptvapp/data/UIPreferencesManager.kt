package com.example.androidtviptvapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ChannelViewSize {
    SMALL, MEDIUM, LARGE
}

/**
 * UIPreferencesManager - Handles persistent UI preference storage.
 * Stores channel view size and other UI preferences.
 */
object UIPreferencesManager {
    private const val PREFS_NAME = "tvgo_ui_prefs"
    private const val KEY_CHANNEL_VIEW_SIZE = "channel_view_size"

    private var prefs: SharedPreferences? = null

    var channelViewSize by mutableStateOf(ChannelViewSize.MEDIUM)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Load saved preference
        val saved = prefs?.getString(KEY_CHANNEL_VIEW_SIZE, null)
        channelViewSize = try {
            if (saved != null) ChannelViewSize.valueOf(saved) else ChannelViewSize.MEDIUM
        } catch (e: Exception) {
            ChannelViewSize.MEDIUM
        }
    }

    fun setViewSize(size: ChannelViewSize) {
        channelViewSize = size
        prefs?.edit()?.apply {
            putString(KEY_CHANNEL_VIEW_SIZE, size.name)
            apply()
        }
    }
}
