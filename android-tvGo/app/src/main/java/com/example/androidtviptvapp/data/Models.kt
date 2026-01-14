package com.example.androidtviptvapp.data

data class Channel(
    val id: String,
    val name: String,
    val logo: String,
    val category: String,
    val streamUrl: String,
    val description: String,
    val logoColor: String,
    val schedule: List<ScheduleItem> = emptyList(),
    val isFavorite: Boolean = false,
    val hasArchive: Boolean = true,  // OnTV-main: isHaveArchive - whether channel supports timeshift/archive
    val isMulticast: Boolean = false  // OnTV-main: isMulticast - UDP multicast stream
)

data class ScheduleItem(
    val time: String,
    val title: String,
    val duration: String,
    val isLive: Boolean = false
)

data class Movie(
    val id: String,
    val title: String,
    val thumbnail: String,        // poster
    val backdrop: String = "",    // landscape/hero image
    val category: String,
    val year: Int,
    val rating: String,
    val description: String,      // synopsis
    val videoUrl: String,         // user's stream URL
    val trailerUrl: String = "",  // YouTube trailer
    val genre: List<String>,
    val runtime: Int = 0,         // minutes
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList()
)

data class Category(
    val id: String,
    val name: String
)

// System Messages from Admin
data class SystemMessage(
    val id: String,
    val title: String,
    val body: String,
    val url: String? = null,  // If present, show QR code
    val createdAt: String? = null,
    val isRead: Boolean = false
)

