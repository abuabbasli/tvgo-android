package com.example.androidtviptvapp.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Singleton to remember the last played channel AND category when navigating between sections.
 * This ensures that when the user returns to the channels screen,
 * they see the same category and channel they were in before switching sections.
 */
object ChannelFocusManager {
    // Store the last played channel ID (only when actually playing, not just focusing)
    var lastPlayedChannelId by mutableStateOf<String?>(null)
        private set

    // Store the category that was selected when the channel was played
    var lastSelectedCategory by mutableStateOf<String>("all")
        private set

    /**
     * Update the last played channel and the category it was played from.
     * Call this when a channel actually starts playing (preview or fullscreen).
     */
    fun updatePlayedChannel(channelId: String?, category: String = "all") {
        lastPlayedChannelId = channelId
        lastSelectedCategory = category
    }

    /**
     * Get the last played channel ID, or null if none.
     */
    fun getLastFocusedChannel(): String? {
        return lastPlayedChannelId
    }

    /**
     * Get the category that was selected when the last channel was played.
     */
    fun getLastCategory(): String {
        return lastSelectedCategory
    }

    /**
     * Clear the stored channel and category (useful on logout or data refresh).
     */
    fun clear() {
        lastPlayedChannelId = null
        lastSelectedCategory = "all"
    }
}
