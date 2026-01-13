package com.mc2soft.ontv.ontvapp.movies

import android.content.Context
import android.view.LayoutInflater
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.ui.asDpToPx
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.MovieTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.ui.load
import org.neonwindwalker.hardtiles.CachedTileVM
import org.neonwindwalker.hardtiles.HardtileCachedCommonFrameLayout

class MovieTileVM(val parent: MoviesGridVM, initialMovie: NetMovieOrSeries) :
        CachedTileVM<MovieTileVM, MovieTileView>(MovieTileView::class.java, BaseApp.getDimension(R.dimen.movie_tile_width), BaseApp.getDimension(R.dimen.movie_tile_height), parent) {

    var movie: NetMovieOrSeries = initialMovie
        set(v) {
            if (field === v)return
            field = v
            view?.update()
        }
}

class MovieTileView(context: Context) : HardtileCachedCommonFrameLayout<MovieTileVM>(context) {
    val vb = MovieTileBinding.inflate(LayoutInflater.from(context), this)

    init {
        setPadding(5.asDpToPx(), 5.asDpToPx(), 5.asDpToPx(), 5.asDpToPx())
        setOnClickListener {
            MainActivity.get(context)?.let { act->
                act.openMovieDetails(vm?.movie)
            }
        }
        setOnFocusChangeListener { view, hasFocus ->
            vb.name.isSelected = hasFocus
        }
    }

    override fun update() {
        val movie = vm?.movie
        vb.name.text = LocalizeStringMap.translate(movie?.name)
        vb.image.load(movie?.tileImageUrl)
    }
}