package com.mc2soft.ontv.ontvapp.movies

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat.getSystemService
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.MovieSearchTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import org.neonwindwalker.hardtiles.SimpleTileCommonFrameLayout
import org.neonwindwalker.hardtiles.SimpleTileVM


class MovieSearchGenreTileVM(val genresColVM: MovieGenresColVM, initialGenre: NetGenre) :
    SimpleTileVM<MovieSearchGenreTileView>(ViewGroup.LayoutParams.MATCH_PARENT, BaseApp.getDimension(R.dimen.genre_tile_height), genresColVM), IMovieGenreTileVM {

    override var genre: NetGenre = initialGenre
        set(v) {
            assert(genre.isSearch)
            field = v
            view?.update()
        }

    override fun viewBuild(context: Context, width: Int, height: Int): MovieSearchGenreTileView {
        return MovieSearchGenreTileView(context, this)
    }
}

class MovieSearchGenreTileView(con: Context, override val vm: MovieSearchGenreTileVM) : SimpleTileCommonFrameLayout(con) {

    val vb = MovieSearchTileBinding.inflate(LayoutInflater.from(context), this)

    init {
        update()
        vb.edit.addTextChangedListener(object  : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                vm.genre = NetGenre.makeSearch(vb.edit.text.toString())
                if (vm.genresColVM.menuVM.genre?.isSearch == true) {
                    vm.genresColVM.menuVM.genre = vm.genre
                }
            }
        })

        vb.edit.setOnFocusChangeListener { v, hasFocus ->
            val imm = getSystemService(context, InputMethodManager::class.java) ?: return@setOnFocusChangeListener
            if (hasFocus) {
                if (vm.genre.title.isEmpty()) {
                    vm.genresColVM.menuVM.genre = vm.genre
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
    }

    fun update() {
        val q = LocalizeStringMap.translate(vm.genre.title)
        if (vb.edit.text.toString() != q) {
            vb.edit.setText(q)
        }
    }
}