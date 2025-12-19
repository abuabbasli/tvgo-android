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
    val isFavorite: Boolean = false
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
    val thumbnail: String,
    val category: String,
    val year: Int,
    val rating: String,
    val description: String,
    val videoUrl: String,
    val genre: List<String>
)

data class Category(
    val id: String,
    val name: String
)
