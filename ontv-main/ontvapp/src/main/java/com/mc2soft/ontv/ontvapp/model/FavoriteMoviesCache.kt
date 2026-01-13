package com.mc2soft.ontv.ontvapp.model

import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FavoriteMoviesCache {
    data class Record(val fav: Boolean, val timestamp: Long)

    private val _overlaps = MutableStateFlow(emptyMap<Int, Record>())
    val overlaps = _overlaps.asStateFlow()

    fun isInFav(m: NetMovieOrSeries?): Boolean {
        m ?: return false
        return overlaps.value.get(m.id)?.fav ?: m.isFav
    }

    private val addToFavDispatcher = Dispatchers.IO.limitedParallelism(1)

    fun setFav(m: NetMovieOrSeries, fav: Boolean) {
        _overlaps.value = overlaps.value.toMutableMap().apply {
            put(m.id, Record(fav, System.currentTimeMillis()))
        }
        AuthorizedUser.scope.launch(addToFavDispatcher) {
            overlaps.value[m.id]?.let { rec->
                if (rec.fav)
                    AuthorizedUser.addMovieToFav(m.id)
                else
                    AuthorizedUser.removeMovieFromFav(m.id)
            }
        }
        MoviesCache.invalidateFavorites()
    }

    fun clear() {
        _overlaps.value = emptyMap()
    }

    fun removeOldRecords(movies: List<NetMovieOrSeries>, loadStartTime: Long) {
        _overlaps.value = overlaps.value.filter { kvp->
            kvp.value.timestamp > loadStartTime &&
                    (movies.find { it.id == kvp.key }?.let { it.isFav != kvp.value.fav } ?: true)
        }
    }
}