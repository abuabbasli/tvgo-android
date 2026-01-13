package com.mc2soft.ontv.ontvapp.model

import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre.Companion.ALL_ID
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeriesList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MoviesCache {
    const val RELOAD_MOVIES_MIN_TIME = 20 * 60000
    const val RELOAD_SEARCH_MIN_TIME = 5 * 60000

    class MoviesList(val genre: NetGenre?) {
        private val _movies = MutableStateFlow(emptyList<NetMovieOrSeries>())
        val movies = _movies.asStateFlow()
        private var loadJob: Job? = null
        private var lastLoadTime = 0L
        private var loadPage = 1
        var isLoaded: Boolean = false
            private set

        fun load() {
            if (loadJob?.isActive == true || isLoaded) return
            loadJob = AuthorizedUser.scope.launch {
                try {
                    val loadStartTime = System.currentTimeMillis()
                    val resp = if (genre?.isSearch == true) {
                        if (genre.title.isNotBlank())
                            AuthorizedUser.getSearchMovies(loadPage, genre.title)
                        else
                            NetMovieOrSeriesList()
                    } else {
                        AuthorizedUser.getMovies(loadPage, genre?.id ?: ALL_ID)
                    }
                    _movies.value = movies.value.plus(resp.data)
                    loadPage += 1
                    lastLoadTime = System.currentTimeMillis()
                    isLoaded = movies.value.size >= resp.total_items
                    UserLocalData.inst.updateMoviesCache(resp.data)
                    FavoriteMoviesCache.removeOldRecords(resp.data, loadStartTime)
                } catch (ex: Exception) {
                    BaseApp.handleError(ex)
                }
                loadJob = null

                //hack remove in future by increase tiles in page
                if (loadPage == 2) {
                    load()
                }
            }
        }

        val isOld: Boolean
            get() = System.currentTimeMillis() - lastLoadTime >  if (genre?.isSearch == true) RELOAD_SEARCH_MIN_TIME else RELOAD_MOVIES_MIN_TIME
    }

    private val moviesMap = HashMap<String, MoviesList>()

    private fun mapKeyByGenre(genre: NetGenre?): String {
        val g = genre ?: return ALL_ID
        if (g.isSearch)return "#search:" + genre.title
        return g.id
    }

    fun loadMovies(genre: NetGenre?): StateFlow<List<NetMovieOrSeries>> {
        return moviesMap.getOrPut(mapKeyByGenre(genre)) { MoviesList(genre) }.apply {
            load()
        }.movies
    }

    fun clearOldMovies(excludeGenre: NetGenre?) {
        moviesMap.keys.filter {
            moviesMap[it]!!.isOld && moviesMap[it]!!.genre != excludeGenre
        }.forEach {
            moviesMap.remove(it)
        }
    }

    fun isMoviesListLoadComplete(id: String?): Boolean {
        return moviesMap.get(id)?.isLoaded ?: false
    }

    fun invalidateFavorites() {
        moviesMap.remove(NetGenre.favorites.id)
    }

    fun clear() {
        moviesMap.clear()
    }
}