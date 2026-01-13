package com.mc2soft.ontv.ontvapp.movies

import android.content.Context
import android.view.ViewGroup
import com.google.android.material.color.MaterialColors
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.ui.screenSize
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.model.FavoriteMoviesCache
import com.mc2soft.ontv.ontvapp.model.MoviesCache
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.IHardtileContainerView
import org.neonwindwalker.hardtiles.IHardtileView
import org.neonwindwalker.hardtiles.TilesContainerVM
import org.neonwindwalker.hardtiles.asView

class MoviesGridVM(val menuVM: MoviesMenuView.VM) : TilesContainerVM() {
    init {
        centerFocused = false
        centerFocusedOnKey = false
        vertical = true
        division = 8
        fixedSize = ViewGroup.LayoutParams.MATCH_PARENT
    }

    private var collectItemsJob: Job? = null
    private var collectFavoritesJob: Job? = null

    var genre: NetGenre? = null
        set(v) {
            if (field == v) return
            field = v
            rebuild()
        }

    fun rebuild() {
        collectItemsJob?.cancel()
        collectFavoritesJob?.cancel()
        array = emptyArray()
        MoviesCache.clearOldMovies(genre)
        optimalResize()

        if (genre?.id == NetGenre.history.id) {
            collectItemsJob = menuVM.scope.launch {
                UserLocalData.inst.movieHistory.collect {
                    rebuild(UserLocalData.inst.getMoviesHistory())
                }
            }
            rebuild(UserLocalData.inst.getMoviesHistory())
        } else {
            val source =
                MoviesCache.loadMovies(genre) //store favorite flow, no affect invalidateFavorites
            collectItemsJob = menuVM.scope.launch {
                source.collect {
                    rebuild(it)
                }
            }
//            if (genre == NetGenre.favorites) {
//            if (genre?.id == "-favorites-") {
            if (genre?.id == NetGenre.favorites.id) {
                collectFavoritesJob = menuVM.scope.launch {
                    FavoriteMoviesCache.overlaps.collect {
                        rebuild(source.value)
                    }
                }
            }

            rebuild(source.value)
        }
    }

    fun find(id: Int?): MovieTileVM? {
        val id = id ?: return null
        return array.find { (it as? MovieTileVM)?.movie?.id == id } as? MovieTileVM
    }

    fun rebuild(inMovies: List<NetMovieOrSeries>) {
//      val movies = if (genre?.id == "-favorites-") {
//      val movies = if (genre == NetGenre.favorites) {
        val movies = if (genre?.id == NetGenre.favorites.id) {
            inMovies.filter { FavoriteMoviesCache.isInFav(it) }
        } else inMovies
        val tiles = movies.map { m ->
            find(m.id)?.apply { movie = m } ?: MovieTileVM(this, m)
        }
        /*val unfinishUiMode = !when (genre) {
            NetGenre.history -> true
            else-> MoviesCache.isMoviesListLoadComplete(genre?.id)
        }
        val needAdditionalRebuild = isUnfinished != unfinishUiMode && tiles.size == array.size
        isUnfinished = unfinishUiMode
         */
        array = tiles.toTypedArray()
        /*if (needAdditionalRebuild) {
            triggerPostRebuildView(IHardtileContainerView.RebuildReason.LoadFinishRefresh)
        }
         */
    }

    override fun onTileFocused(index: Int) {
        if (index + division >= array.size &&
            (genre?.id != NetGenre.history.id && genre?.id != NetGenre.favorites.id)
        ) {
            MoviesCache.loadMovies(genre)
        }
    }

    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        return super.buildView(context, width, height).also {
            it.asView.apply {
                setBackgroundColor(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.backgroundColor
                    )
                )
            }
        }
    }

    fun optimalResize() {
        MainActivity.get(tilesContainerView?.context)?.screenSize?.let { screenSize ->
            val div = screenSize.width / BaseApp.getDimension(R.dimen.movie_tile_width)
            if (division != div) {
                division = div
                triggerPostRebuildView(IHardtileContainerView.RebuildReason.Resize)
            }
        }
    }
}
