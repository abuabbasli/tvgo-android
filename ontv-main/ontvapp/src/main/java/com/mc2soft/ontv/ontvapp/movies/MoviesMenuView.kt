package com.mc2soft.ontv.ontvapp.movies

import android.view.LayoutInflater
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.MenuBaseView
import com.mc2soft.ontv.ontvapp.databinding.MovieMenuBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.SaveFocusData

class MoviesMenuView(activity: MainActivity) : MenuBaseView(activity) {
    class VM(activity: MainActivity) : BaseVM() {
        val genres = MovieGenresColVM(this, activity)
        val grid = MoviesGridVM(this)

        private var postSetGenreJob: Job? = null

        var genre: NetGenre? = null
            set(v) {
                if (field === v)return
                field = v

                if (genre?.isSearch != true) { //clear search
                    genres.search.genre = NetGenre.makeSearch("")
                }

                postSetGenreJob?.cancel()
                postSetGenreJob = scope.launch {
                    delay(350)
                    grid.genre = genre
                }
            }

        init {
            vertical = false
            array = arrayOf(genres, grid)
            genre = AuthorizedUser.filmCategories.value.firstOrNull()
        }
    }

    override val vm = VM(activity)

    val vb = MovieMenuBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        build()
    }

    override fun show() {
        if (showed)return
        super.show()
        if (!SaveFocusData.restoreFocus(containerView, vm.savedSelectedDataItem)) {
            keepFocus()
        }
    }

    override fun keepFocus(): Boolean {
        if (containerView?.hasFocus() != true) {
            if (vm.genres.focusToSelectedGenre())
                return true
        }
        return super.keepFocus()
    }
}