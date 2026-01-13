package com.mc2soft.ontv.ontvapp.movies

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.run_helpers.RunOnTvActivityHelper
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.common.ui.getAttrDrawableId
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.GenreTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import org.neonwindwalker.hardtiles.CachedTileVM
import org.neonwindwalker.hardtiles.HardtileCachedCommonFrameLayout
import org.neonwindwalker.hardtiles.IHardtileViewModel

interface IMovieGenreTileVM : IHardtileViewModel {
    var genre: NetGenre
}

class MovieGenreTileVM(val genresColVM: MovieGenresColVM, initialGenre: NetGenre) :
        CachedTileVM<MovieGenreTileVM, MovieGenreTileView>(MovieGenreTileView::class.java, ViewGroup.LayoutParams.MATCH_PARENT, BaseApp.getDimension(R.dimen.genre_tile_height), genresColVM), IMovieGenreTileVM {

    override var genre: NetGenre = initialGenre
        set(v) {
            if (field === v)return
            field = v
            view?.update()
        }
}

class MovieGenreTileView(context: Context) : HardtileCachedCommonFrameLayout<MovieGenreTileVM>(context) {
    val vb = GenreTileBinding.inflate(LayoutInflater.from(context), this)

    init {
        isFocusable = true
        isFocusableInTouchMode = true

        setBackgroundResource(context.theme.getAttrDrawableId(R.attr.theme_genre_bg) ?: 0)

        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                vm?.let {
                    vm?.genresColVM?.menuVM?.genre = it.genre
                }
            }
            vb.name.isSelected = hasFocus
        }

        setOnClickListener {
            vm?.genresColVM?.menuVM?.grid?.tilesContainerView?.focusToTileEx(0)
            if (vm?.genre?.id == "-tv-"){
                RunOnTvActivityHelper.run(context, false)
            }
        }
    }

    override fun update() {
        val genre = vm?.genre
        vb.name.text = LocalizeStringMap.translate(genre?.title)
        updateSelectedState()
    }

    fun updateSelectedState() {
        isSelected = vm?.genre?.id == vm?.genresColVM?.menuVM?.genre?.id && vm?.genre?.id != null
    }
}