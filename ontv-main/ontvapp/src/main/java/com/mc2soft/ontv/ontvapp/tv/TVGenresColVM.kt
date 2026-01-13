package com.mc2soft.ontv.ontvapp.tv

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.core.view.forEach
import com.google.android.material.color.MaterialColors
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.consoleapi.NetConsoleInfo
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import org.neonwindwalker.hardtiles.IHardtileView
import org.neonwindwalker.hardtiles.SingleValueContainer
import org.neonwindwalker.hardtiles.TilesContainerVM
import org.neonwindwalker.hardtiles.asView

class TVGenresColVM(val menuVM: TVMenuView.VM, val appActivity: MainActivity) : TilesContainerVM() {
    init {
        vertical = true
        fixedSize = BaseApp.getDimension(R.dimen.genre_tile_width)
        topPadding = BaseApp.getDimension(R.dimen.channel_col_header_height)
        rebuild()
    }

    fun rebuild() {
        val consoleDefaults: NetConsoleInfo? = BaseApp.sharedSettings.consoleDefaultsObj
        val genreList = mutableListOf<NetGenre>()
        if (consoleDefaults?.company?.application?.any {it.name == "vod"} == true){
            genreList.add(NetGenre("-video-", "VideoClub"))
        }
        genreList.add(NetGenre("-favorites-", "Favorites"))
        genreList.addAll(AuthorizedUser.tvGenres.value)
        genreList.add(NetGenre("-history-", "History"))

        genreList.forEach{ genre ->
            genre.title = appActivity.mapCatToName(genre.title)
        }
        array = genreList.map { g->
            find(g.id)?.apply { genre = g } ?: TVGenreTileVM(this, g)
        }.toTypedArray()
    }

    fun find(id: String?): TVGenreTileVM? {
        id ?: return null
        return array.find { (it as? TVGenreTileVM)?.genre?.id == id } as? TVGenreTileVM
    }

    fun genreAllTileVM(): TVGenreTileVM? {
        return find(NetGenre.ALL_ID)
    }

    override fun overrideDefaultFocusOnMove(focusedRect: Rect, direction: Int, ignoreFocusView: View?): SingleValueContainer<View?>? {
        return SingleValueContainer(tilesBuilderView?.findTileViewForVM(find(menuVM.channels.genre?.id))?.asView)
    }

    fun updateTilesSelectedState() {
        tilesContainerView?.forEach {
            (it as? TVGenreTileView)?.updateSelectedState()
        }
    }

    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        return super.buildView(context, width, height).also {
            it.asView.apply {
                setBackgroundColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
            }
        }
    }
}
