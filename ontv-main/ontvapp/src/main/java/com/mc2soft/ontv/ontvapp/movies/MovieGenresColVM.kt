package com.mc2soft.ontv.ontvapp.movies

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

class MovieGenresColVM(val menuVM: MoviesMenuView.VM, val appActivity: MainActivity) : TilesContainerVM() {
    val search = MovieSearchGenreTileVM(this, NetGenre.makeSearch(""))

    init {
        vertical = true
        fixedSize = BaseApp.getDimension(R.dimen.movies_genre_tile_width)
        rebuild()
    }

    fun rebuild() {
        val consoleDefaults: NetConsoleInfo? = BaseApp.sharedSettings.consoleDefaultsObj
        val genreList = mutableListOf<NetGenre>()
        if (consoleDefaults?.company?.application?.any {it.name == "tv"} == true){
            genreList.add(NetGenre("-tv-", "TV"))
        }
        genreList.add(NetGenre("-favorites-", "Favorites"))
        genreList.addAll(AuthorizedUser.filmCategories.value)
        genreList.add(NetGenre("-history-", "History"))

        genreList.forEach{ genre ->
            genre.title = appActivity.mapCatToName(genre.title)
        }

        array = listOf(search).plus(
            genreList.map { g->
                find(g.id)?.apply { genre = g } ?: MovieGenreTileVM(this, g)
            }
        ).toTypedArray()
    }

    fun find(id: String?): IMovieGenreTileVM? {
        val id = id ?: return null
        return array.find { (it as? IMovieGenreTileVM)?.genre?.id == id } as? IMovieGenreTileVM
    }

    override fun overrideDefaultFocusOnMove(focusedRect: Rect, direction: Int, ignoreFocusView: View?): SingleValueContainer<View?>? {
        return SingleValueContainer(tilesBuilderView?.findTileViewForVM(find(menuVM.genre?.id))?.asView)
    }

    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        return super.buildView(context, width, height).also {
            it.asView.apply {
                setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.backgroundColor))
            }
        }
    }

    fun updateTilesSelectedState() {
        tilesContainerView?.forEach {
            (it as? MovieGenreTileView)?.updateSelectedState()
        }
    }

    fun focusToSelectedGenre(): Boolean {
        return tilesContainerView?.focusToTileEx(find(menuVM.genre?.id)) == true
    }
}
