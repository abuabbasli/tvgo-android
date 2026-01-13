package com.mc2soft.ontv.ontvapp.movies

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.ui.BaseDialog
import com.mc2soft.ontv.common.ui.asDpToPx
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.SeriesDetailsDlgBinding
import com.mc2soft.ontv.ontvapp.model.FavoriteMoviesCache
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.ui.PinCodeCheckDialog
import com.mc2soft.ontv.ontvapp.ui.load


open class MovieDetailsDialog : BaseDialog {
    protected var movie: NetMovieOrSeries? = null

    constructor() {}
    constructor(src: NetMovieOrSeries) {
        movie = src
    }

    protected var vb: SeriesDetailsDlgBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        vb = SeriesDetailsDlgBinding.inflate(inflater, container, false)
        return vb!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (movie?.isSeries == false) {
            vb?.htroot?.visible(false)
            (vb?.description?.layoutParams as? ConstraintLayout.LayoutParams)?.let {
                it.topToBottom = R.id.about
                it.topMargin = 16.asDpToPx()
            }
        }

        vb?.playButton?.setOnClickListener {
            open()
        }

        vb?.favButton?.setOnClickListener {
            movie?.let { series->
                FavoriteMoviesCache.setFav(series, !FavoriteMoviesCache.isInFav(series))
                update()
            }
        }

        vb?.lockButton?.setOnClickListener {
            val act = MainActivity.get(context) ?: return@setOnClickListener
            movie?.let { series->
                PinCodeCheckDialog {
                    UserLocalData.inst.setParentalLock(series, !UserLocalData.inst.isParentalLock(series))
                    update()
                }.show(act.supportFragmentManager, null)
            }
        }

        vb?.description?.setMovementMethod(ScrollingMovementMethod())

        vb?.description?.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val f = vb?.description?.let { it.lineCount > it.maxLines } ?: false
            vb?.description?.isFocusable = f
            vb?.description?.isFocusableInTouchMode = f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vb = null
    }

    override fun onStart() {
        super.onStart()
        if (movie == null) {
            dismiss()
        } else {
            update()
        }
    }

    override fun onResume() {
        super.onResume()
        vb?.playButton?.requestFocus()
        update()
    }

    open fun update() {
        val movie = movie ?: return
        vb?.name?.text = LocalizeStringMap.translate(movie.name)
        vb?.description?.text = LocalizeStringMap.translate(movie.description)
        vb?.image?.load(movie.posterUrl)
        vb?.favButton?.isSelected = FavoriteMoviesCache.isInFav(movie)
        if (movie.isCensored) {
            vb?.lockButton?.isEnabled = false
            vb?.lockButton?.isSelected = true
        } else {
            vb?.lockButton?.isEnabled = true
            vb?.lockButton?.isSelected = UserLocalData.inst.isParentalLock(movie)
        }
        vb?.about?.text = StringBuffer().apply {
            movie.country?.takeIf { it.isNotEmpty() }?.let {
                append("${resources.getString(R.string.movie_about_country)}: ${LocalizeStringMap.translate(it)}\n")
            }
            movie.genres_str?.takeIf { it.isNotEmpty() }?.let {
                append("${resources.getString(R.string.movie_about_genre)}: ${LocalizeStringMap.translate(it)}\n")
            }
            movie.director?.takeIf { it.isNotEmpty() }?.let {
                append("${resources.getString(R.string.movie_about_director)}: ${LocalizeStringMap.translate(it)}\n")
            }
            movie.actors?.takeIf { it.isNotEmpty() }?.let {
                append("${resources.getString(R.string.movie_about_actors)}: ${LocalizeStringMap.translate(it)}\n")
            }
        }

        movie.year?.takeIf { it.isNotEmpty() }?.let {
            vb?.year?.visible(true)
            vb?.yearIcon?.visible(true)
            vb?.year?.text = it
        } ?: run {
            vb?.year?.visible(false)
            vb?.yearIcon?.visible(false)
        }

        movie.rating_imdb?.takeIf { it.isNotEmpty() }?.let {
            vb?.rating?.visible(true)
            vb?.ratingIcon?.visible(true)
            vb?.rating?.text = it
        } ?: run {
            vb?.rating?.visible(false)
            vb?.ratingIcon?.visible(false)
        }

        movie.age?.takeIf { it.isNotEmpty() }?.let {
            vb?.limit?.visible(true)
            vb?.limitIcon?.visible(true)
            vb?.limit?.text = it
        } ?: run {
            vb?.limit?.visible(false)
            vb?.limitIcon?.visible(false)
        }

        movie.time?.takeIf { it.isNotEmpty() }?.let {
            vb?.duration?.visible(true)
            vb?.durationIcon?.visible(true)
            vb?.duration?.text = it + resources.getString(R.string.movie_about_time_units)
        } ?: run {
            vb?.duration?.visible(false)
            vb?.durationIcon?.visible(false)
        }
    }

    open fun open() {
        val movie = movie ?: return
        preOpen { act->
            PlaybackSource.MoviePlaybackSource(movie).showAccessDialogInNeed(act) {
                it.showUseSavedPositionDialogInNeed(act) {
                    it.loadUrlAndPlay(act.player)
                }
            }
        }
    }

    fun preOpen(cb: (act: MainActivity)->Unit) {
        dismiss()
        MainActivity.get(context)?.let { act->
            act.hideMenu(true)
            cb(act)
        }
    }
}