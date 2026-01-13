package com.mc2soft.ontv.ontvapp.tv

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.run_helpers.RunOnTvActivityHelper
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.common.ui.getAttrDrawable
import com.mc2soft.ontv.common.ui.getAttrDrawableId
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.GenreTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import org.neonwindwalker.hardtiles.CachedTileVM
import org.neonwindwalker.hardtiles.HardtileCachedCommonFrameLayout

class TVGenreTileVM(val genresColVM: TVGenresColVM, initialGenre: NetGenre) :
    CachedTileVM<TVGenreTileVM, TVGenreTileView>(
        TVGenreTileView::class.java,
        ViewGroup.LayoutParams.MATCH_PARENT,
        BaseApp.getDimension(R.dimen.genre_tile_height),
        genresColVM
    ) {

    var genre: NetGenre = initialGenre
        set(v) {
            if (field === v) return
            field = v
            view?.update()
        }
}

class TVGenreTileView(con: Context) : HardtileCachedCommonFrameLayout<TVGenreTileVM>(con) {
    val vb = GenreTileBinding.inflate(LayoutInflater.from(context), this)

    init {
        isFocusable = true
        isFocusableInTouchMode = true

        setBackgroundResource(context.theme.getAttrDrawableId(R.attr.theme_genre_bg) ?: 0)

        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                vm?.let { vm ->
                    vm.genresColVM.menuVM.channels.genre = vm.genre
                }
            }
            vb.name.isSelected = hasFocus
            vm?.genresColVM?.menuVM?.menuView?.updateExpandStatus()
        }

        setOnClickListener {
            if (vm?.genre?.id == "-video-") {
                RunOnTvActivityHelper.run(context, true)
            }
        }
    }

    override fun update() {
        vb.name.text = LocalizeStringMap.translate(vm?.genre?.title)
        updateSelectedState()
    }

    fun updateSelectedState() {
        isSelected =
            vm?.genre?.id == vm?.genresColVM?.menuVM?.channels?.genre?.id && vm?.genre?.id != null
    }
}