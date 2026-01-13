package com.mc2soft.ontv.ontvapp.movies

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.forEach
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetEpisode
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.ui.asDpToPx
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.SeriesEpisodeTileBinding
import com.mc2soft.ontv.ontvapp.databinding.SeriesSeasonTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.model.SeriesDetails
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.*


class SeriesDetailsDialog : MovieDetailsDialog {
    constructor() : super()
    constructor(src: NetMovieOrSeries) : super(src)

    class VM(val series: NetMovieOrSeries) : TilesContainerVM() {
        var seriesDetails: SeriesDetails? = null
        val episodes = EpisodesVM(this)
        val seasons = SeasonsVM(this)
        var dialog: SeriesDetailsDialog? = null

        var seasonNumber: Int = 0
            set(v) {
                if (field == v) return
                field = v
                episodes.rebuild(true)
                seasons.updateSelectedState()
            }

        val season: SeriesDetails.Season?
            get() = seriesDetails?.seasons?.getOrNull(seasonNumber)
        init {
            vertical = true
            fixedSize = BaseApp.getDimension(R.dimen.episode_tile_height) * 2
            array = arrayOf(seasons, episodes)
        }

        fun load() {
            if (seriesDetails != null)return
            AuthorizedUser.scope.launch {
                try {
                    seriesDetails = SeriesDetails.load(series)
                    dialog?.scope?.launch {
                        dialog?.update()
                    }
                } catch (ex: Exception) {
                    BaseApp.handleError(ex)
                }
            }
        }

        fun rebuild() {
            seasons.rebuild()
            episodes.rebuild()
        }
    }

    class SeasonsVM(val parent: VM) : TilesContainerVM() {
        init {
            parentVM = parent
            vertical = false
            fixedSize = BaseApp.getDimension(R.dimen.episode_tile_height)
        }

        fun rebuild() {
            array = parent.seriesDetails?.seasons?.map {
                find(it.season.id) ?: SeasonTileVM(this, it)
            }?.toTypedArray() ?: emptyArray()
        }

        fun find(id: Int?): SeasonTileVM? {
            id ?: return null
            return array.find { (it as? SeasonTileVM)?.season?.season?.id == id } as? SeasonTileVM
        }

        override fun overrideDefaultFocusOnMove(focusedRect: Rect, direction: Int, ignoreFocusView: View?): SingleValueContainer<View?>? {
            return SingleValueContainer(tilesBuilderView?.findTileViewForArrayIndex(parent.seasonNumber)?.asView)
        }

        fun updateSelectedState() {
            tilesContainerView?.forEach {
                (it as? SeasonTileView)?.updateSelectedState()
            }
        }
    }

    class EpisodesVM(val parent: VM) : TilesContainerVM() {
        init {
            parentVM = parent
            vertical = false
            fixedSize = BaseApp.getDimension(R.dimen.episode_tile_height)
        }

        fun rebuild(initial: Boolean = false) {
            if (initial)
                array = emptyArray()
            array = parent.seriesDetails?.seasons?.getOrNull(parent.seasonNumber)?.episodes?.let {
                it.map { find(it.id) ?: EpisodeTileVM(this, it) }.toTypedArray()
            } ?: run {
                emptyArray()
            }
        }

        fun find(id: Int?): EpisodeTileVM? {
            id ?: return null
            return array.find { (it as? EpisodeTileVM)?.episode?.id == id } as? EpisodeTileVM
        }
    }

    private var vm: VM? = null
    private var containerView: TilesContainerView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        movie?.let {
            vb?.htroot?.visible(true)
            vm = VM(it)
            containerView = vm?.buildView(requireContext(), 1000, 1000) as TilesContainerView
            vb?.htroot?.addView(containerView!!)
        }

        containerView?.dispatchKeyEventLeaveFocusCallback = { dir, _->
            when(dir) {
                View.FOCUS_LEFT -> false
                View.FOCUS_DOWN -> vb?.description?.isFocusable != true
                else -> true
            }
        }

        vm?.dialog = this
        vm?.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        containerView?.freeAndVMNotify()
        containerView = null
        vm?.dialog = null
    }

    override fun update() {
        super.update()
        vm?.rebuild()
    }

    class SeasonTileVM(val seasonsRow: SeasonsVM, val season: SeriesDetails.Season) :
        CachedTileVM<SeasonTileVM, SeasonTileView>(SeasonTileView::class.java, BaseApp.getDimension(R.dimen.season_tile_width), BaseApp.getDimension(R.dimen.episode_tile_height), seasonsRow) {
    }

    class SeasonTileView(context: Context) : HardtileCachedCommonFrameLayout<SeasonTileVM>(context) {
        val vb = SeriesSeasonTileBinding.inflate(LayoutInflater.from(context), this)

        init {
            setPadding(6.asDpToPx(), 6.asDpToPx(), 6.asDpToPx(), 6.asDpToPx())
            isFocusable = true
            isFocusableInTouchMode = true
            setOnFocusChangeListener { view, b ->
                vm?.seasonsRow?.parent?.dialog?.let { dialog->
                    dialog.vm?.seasonNumber = dialog.vm?.seriesDetails?.seasons?.indexOf(vm?.season) ?: 0
                }
            }
        }
        override fun update() {
            vb.name.text = "${resources.getString(R.string.series_about_season)} ${vm?.season?.season?.season_number}"
            updateSelectedState()
        }

        fun updateSelectedState() {
            isSelected = vm?.seasonsRow?.parent?.season == vm?.season && vm?.season != null
        }
    }

    class EpisodeTileVM(val episodesRow: EpisodesVM, val episode: NetEpisode) :
        CachedTileVM<EpisodeTileVM, EpisodeTileView>(EpisodeTileView::class.java, ViewGroup.LayoutParams.WRAP_CONTENT, BaseApp.getDimension(R.dimen.episode_tile_height), episodesRow) {
    }

    class EpisodeTileView(context: Context) : HardtileCachedCommonFrameLayout<EpisodeTileVM>(context) {
        val vb = SeriesEpisodeTileBinding.inflate(LayoutInflater.from(context), this)

        init {
            setPadding(6.asDpToPx(), 6.asDpToPx(), 6.asDpToPx(), 6.asDpToPx())
            setOnClickListener {
                vm?.episodesRow?.parent?.let { rootVM->
                    rootVM.dialog?.open(rootVM.season!!, vm!!.episode)
                }
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        }

        override fun update() {
            vb.name.text = LocalizeStringMap.translate(vm?.episode?.name)
            vm?.episodesRow?.parent?.let { rootVM->
                UserLocalData.inst.getMovieOrEpisodeLastPlayPosition(rootVM.series.id, rootVM.season?.season?.id, vm?.episode?.id)?.let { seedAndDuration->
                    (vb.progress.layoutParams as LinearLayout.LayoutParams).weight = seedAndDuration.progress
                    vb.progress.requestLayout()
                    vb.progressFrame.visibility = View.VISIBLE
                } ?: run {
                    vb.progressFrame.visibility = View.INVISIBLE
                }
            }
        }
    }

    override fun open() {
        val details = vm?.seriesDetails ?: return
        if (details.seasons.isEmpty())return
        var prevNSeason: Int? = null
        var prevNEpisode: Int? = null
        preOpen { act->
            for (nS in details.seasons.indices.reversed()) {
                val s = details.seasons[nS]
                for (nE in s.episodes.indices.reversed()) {
                    UserLocalData.inst.getMovieOrEpisodeLastPlayPosition(details.series.id, s.season.id, s.episodes[nE].id)?.let { seedAndDuration->
                        if (seedAndDuration.progress < 0.99f) {
                            PlaybackSource.SeriesPlaybackSource(details, nS, nE).showAccessDialogInNeed(act) {
                                it.showUseSavedPositionDialogInNeed(act) {
                                    it.loadUrlAndPlay(act.player)
                                }
                            }
                            return@preOpen
                        } else if (prevNSeason != null && prevNEpisode != null) {
                            PlaybackSource.SeriesPlaybackSource(details, prevNSeason!!, prevNEpisode!!).showAccessDialogInNeed(act) {
                                it.showUseSavedPositionDialogInNeed(act) {
                                    it.loadUrlAndPlay(act.player)
                                }
                            }
                            return@preOpen
                        }
                    }
                    prevNSeason = nS
                    prevNEpisode = nE
                }
            }

            if (details.seasons.getOrNull(0)?.episodes?.isNotEmpty() == true) {
                PlaybackSource.SeriesPlaybackSource(details, 0, 0).showAccessDialogInNeed(act) {
                    it.showUseSavedPositionDialogInNeed(act) {
                        it.loadUrlAndPlay(act.player)
                    }
                }
            }
        }
    }

    fun open(season: SeriesDetails.Season, episode: NetEpisode) {
        val details = vm?.seriesDetails ?: return
        preOpen { act ->
            PlaybackSource.SeriesPlaybackSource(details, details.seasons.indexOf(season), season.episodes.indexOf(episode)).showAccessDialogInNeed(act) {
                it.showUseSavedPositionDialogInNeed(act) {
                    it.loadUrlAndPlay(act.player)
                }
            }
        }
    }
}