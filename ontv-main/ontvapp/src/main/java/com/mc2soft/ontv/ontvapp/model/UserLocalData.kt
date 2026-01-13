package com.mc2soft.ontv.ontvapp.model

import com.mc2soft.ontv.common.MutableStateFlowCustomSerializer
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.ontvapp.OnTVApp
import com.mc2soft.ontv.ontvapp.analytics.Analytics
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

@kotlinx.serialization.Serializable
class UserLocalData {
    @kotlinx.serialization.Serializable(with = MutableStateFlowCustomSerializer::class)
    private val _channelHistory = MutableStateFlow(emptyList<Int>())

    @kotlinx.serialization.Transient
    val channelHistory = _channelHistory.asStateFlow()

    @kotlinx.serialization.Serializable
    data class VODHistoryItem(
        val id: Int,
        val seasonId: Int? = null,
        val episodeId: Int? = null,
        var pos: Long = 0,
        var duration: Long = 0
    )

    @kotlinx.serialization.Serializable(with = MutableStateFlowCustomSerializer::class)
    private val _movieHistory = MutableStateFlow(emptyList<VODHistoryItem>())

    @kotlinx.serialization.Transient
    val movieHistory = _movieHistory.asStateFlow()

    @kotlinx.serialization.Serializable(with = MutableStateFlowCustomSerializer::class)
    private val _moviesCache = MutableStateFlow(emptyList<NetMovieOrSeries>())

    @kotlinx.serialization.Transient
    val moviesCache = _moviesCache.asStateFlow()


    @kotlinx.serialization.Serializable(with = MutableStateFlowCustomSerializer::class)
    private val _lockedMovies = MutableStateFlow(emptyList<Int>())

    @kotlinx.serialization.Transient
    val lockedMovies = _lockedMovies.asStateFlow()

    @kotlinx.serialization.Serializable(with = MutableStateFlowCustomSerializer::class)
    private val _lockedChannels = MutableStateFlow(emptyList<Int>())

    @kotlinx.serialization.Transient
    val lockedChannels = _lockedChannels.asStateFlow()

    @kotlinx.serialization.Serializable(with = MutableStateFlowCustomSerializer::class)
    private val _channelCmdCache = MutableStateFlow(emptyMap<Int, String>())

    @kotlinx.serialization.Transient
    val channelCmdCache = _channelCmdCache.asStateFlow()

    companion object {
        const val ADD_TO_HISTORY_AFTER_TIME = 5000

        val file: File
            get() = File(OnTVApp.inst.applicationContext.filesDir, "user_data.json")

        val inst: UserLocalData by lazy {
            try {
                val str = file.readText()
                Json { ignoreUnknownKeys = true }.decodeFromString<UserLocalData>(str)
            } catch (ex: Exception) {
                Timber.e(ex.message)
                UserLocalData()
            }
        }
    }

    var needSave = false
        private set

    init {
        NetChannel.getChannelsCmdCacheCallback = {
            _channelCmdCache.value.get(it)
        }
    }

    fun save() {
        needSave = false
        file.writeText(Json.encodeToString(this))
    }

    fun saveInNeed() {
        if (needSave)
            save()
    }

    fun clear() {
        _channelHistory.value = emptyList()
        _movieHistory.value = emptyList()
        _moviesCache.value = emptyList()
        _lockedMovies.value = emptyList()
        _lockedChannels.value = emptyList()
        _channelCmdCache.value = emptyMap()
        save()
    }

    fun clearChannelsHistory() {
        _channelHistory.value = emptyList()
        save()
    }

    fun clearMoviesHistory() {
        _movieHistory.value = emptyList()
        _moviesCache.value = emptyList()
        save()
    }

    fun isInGenre(ch: NetChannel, g: NetGenre?): Boolean {
        if (g == null || g.id == NetGenre.ALL_ID)
            return true
//      if (g === NetGenre.favorites)
//      if (g.id === "-favorites-")
        if (g.id === NetGenre.favorites.id)
            return FavoriteChannelsCache.isInFav(ch)
//        if (g === NetGenre.history)
//        if (g.id === "-history-")
        if (g.id === NetGenre.history.id)
            return channelHistory.value.contains(ch.id)
        return ch.tv_genre_id == g.id
    }

    fun addChannelToHistory(ch: NetChannel) {
        if (ch.isCensored) return
        val arr = _channelHistory.value
        if (arr.firstOrNull() != ch.id) {
            _channelHistory.value = listOf(ch.id).plus(arr.filter { it != ch.id })
            needSave = true
            Analytics.analyticsSetLastPlayedChannel(ch)
        }
    }

    fun addMovieToHistory(movie: NetMovieOrSeries, pos: Long) {
        if (movie.isCensored) return
        if (movieHistory.value.firstOrNull()?.id != movie.id) {
            _movieHistory.value = listOf(
                VODHistoryItem(
                    movie.id,
                    null,
                    null,
                    pos
                )
            ).plus(movieHistory.value.filter { it.id != movie.id })
            needSave = true
            updateMoviesCache(listOf(movie))
        }
        movieHistory.value.first().let {
            if (it.pos != pos) {
                it.pos = pos
                needSave = true
            }
        }
    }

    fun addEpisodeToHistory(
        series: NetMovieOrSeries,
        seasonId: Int,
        episodeId: Int,
        pos: Long,
        duration: Long
    ) {
        if (series.isCensored) return
        if (movieHistory.value.firstOrNull()
                ?.let { it.id == series.id && it.seasonId == seasonId && it.episodeId == episodeId } != true
        ) {
            _movieHistory.value = listOf(
                VODHistoryItem(
                    series.id,
                    seasonId,
                    episodeId,
                    pos
                )
            ).plus(movieHistory.value.filterNot { it.id == series.id && it.seasonId == seasonId && it.episodeId == episodeId })
            needSave = true
            updateMoviesCache(listOf(series))
        }
        movieHistory.value.first().let {
            if (it.pos != pos || it.duration != duration) {
                it.pos = pos
                it.duration = duration
                needSave = true
            }
        }
    }

    fun updateMoviesCache(newMovies: List<NetMovieOrSeries>) {
        val validIdList = movieHistory.value.map { it.id }.toHashSet()
        val list = moviesCache.value.filter { validIdList.contains(it.id) }.map { m ->
            newMovies.find { it.id == m.id } ?: m
        }.toMutableList()
        newMovies.forEach { m ->
            if (validIdList.contains(m.id) && !list.any { it.id == m.id }) {
                list.add(m)
            }
        }
        if (moviesCache.value != list) {
            _moviesCache.value = list
            needSave = true
        }
    }

    fun getMoviesHistory(): List<NetMovieOrSeries> {
        return movieHistory.value.mapNotNull { m ->
            moviesCache.value.find { it.id == m.id }
        }
    }

    fun getMovieLastPlayPosition(id: Int): PlaybackSource.SeekAndDuration? {
        return getMovieOrEpisodeLastPlayPosition(id, null, null)
    }

    fun getMovieOrEpisodeLastPlayPosition(
        id: Int?,
        seasonId: Int?,
        episodeId: Int?
    ): PlaybackSource.SeekAndDuration? {
        id ?: return null
        return movieHistory.value.find { it.id == id && it.seasonId == seasonId && it.episodeId == episodeId }
            ?.let {
                PlaybackSource.SeekAndDuration(it.pos, it.duration)
            }
    }

    fun isParentalLock(item: Any?): Boolean {
        if (item is NetMovieOrSeries) {
            return lockedMovies.value.contains(item.id)
        } else if (item is NetChannel) {
            return lockedChannels.value.contains(item.id)
        }
        return false
    }

    fun isParentalLockOrCensored(item: Any?): Boolean {
        if (item is NetMovieOrSeries) {
            return item.isCensored || isParentalLock(item)
        } else if (item is NetChannel) {
            return item.isCensored || isParentalLock(item)
        }
        return false
    }

    fun setParentalLock(item: Any?, lock: Boolean) {
        if (item is NetMovieOrSeries) {
            if (_lockedMovies.value.contains(item.id) != lock) {
                _lockedMovies.value =
                    _lockedMovies.value.let { if (lock) it.plus(item.id) else it.minus(item.id) }
                needSave = true
            }
        } else if (item is NetChannel) {
            if (_lockedChannels.value.contains(item.id) != lock) {
                _lockedChannels.value =
                    _lockedChannels.value.let { if (lock) it.plus(item.id) else it.minus(item.id) }
                needSave = true
            }
        }
    }

    fun updateChannelsCmdFields(list: List<NetChannel>) {
        val entrys = channelCmdCache.value.toMutableMap()
        var changedFlag = false
        for (ch in list) {
            ch.cmd?.takeIf { it.isNotBlank() }?.let {
                if (entrys[ch.id] != it) {
                    entrys[ch.id] = it
                    changedFlag = true
                }
            }
        }
        if (changedFlag) {
            _channelCmdCache.value = entrys
            needSave = true
        }
    }
}

