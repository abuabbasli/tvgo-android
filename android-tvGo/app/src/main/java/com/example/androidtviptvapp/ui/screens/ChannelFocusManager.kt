package com.example.androidtviptvapp.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Singleton to remember the last played channel when navigating between sections.
 * This ensures that when the user returns to the channels screen,
 * they see the same channel they were playing before switching sections.
 */
object ChannelFocusManager {
    // Store the last played channel ID (only when actually playing, not just focusing)
    var lastPlayedChannelId by mutableStateOf<String?>(null)
        private set

    /**
     * Update the last played channel.
     * Call this when a channel actually starts playing (preview or fullscreen).
     */
    fun updatePlayedChannel(channelId: String?) {
        lastPlayedChannelId = channelId
    }

    /**
     * Get the last played channel ID, or null if none.
     */
    fun getLastFocusedChannel(): String? {
        return lastPlayedChannelId
    }

    /**
     * Clear the stored channel (useful on logout or data refresh).
     */
    fun clear() {
        lastPlayedChannelId = null
    }
}
