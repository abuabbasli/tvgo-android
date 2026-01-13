package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.ui.UserDialog
import com.mc2soft.ontv.common.ui.asDpToPx
import com.mc2soft.ontv.common.ui.screenSize
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.PlayerHotbarButtonBinding
import com.mc2soft.ontv.ontvapp.databinding.PlayerHotbarTileBinding
import com.mc2soft.ontv.ontvapp.model.*
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.player.PlayerView
import com.mc2soft.ontv.ontvapp.ui.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.*

class PlayerHotbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    companion object {
        const val MAX_TILES = 5
    }

    lateinit var player: PlayerView
    lateinit var vm: VM

    fun init(act: MainActivity) {
        player = act.player
        vm = VM(act.scope!!, player.playbackSourceFlow, act.isMovieModeFlow)
    }

    class VM(val scope: CoroutineScope, val playbackSourceFlow: StateFlow<PlaybackSource?>, val isMovieModeFlow: StateFlow<Boolean>) : TilesContainerVM() {
        val gide = ButtonGideVM(this)
        val fav = ButtonFavVM(this)
        val clear = ButtonClearVM(this)

        init {
            vertical = false
            tileSpacing = 10.asDpToPx()
            topPadding = 10.asDpToPx()
            bottomPadding = topPadding
            leftPadding = 56.asDpToPx()
            rightPadding = leftPadding
            fixedSize = BaseApp.getDimension(R.dimen.hotbar_tile_height) + topPadding + bottomPadding
            rebuild()

            scope.launch {
                isMovieModeFlow.collect {
                    rebuild()
                    fav.view?.update()
                }
            }

            scope.launch {
                UserLocalData.inst.channelHistory.collect {
                    rebuild()
                }
            }

            scope.launch {
                FavoriteChannelsCache.overlaps.collect {
                    fav.view?.update()
                }
            }

            scope.launch {
                FavoriteMoviesCache.overlaps.collect {
                    fav.view?.update()
                }
            }

            scope.launch {
                playbackSourceFlow.collect {
                    rebuild()
                    fav.view?.update()
                }
            }
        }

        fun rebuild() {
            if (isMovieModeFlow.value) { /*
                val curPlayedMovieId = (playbackSourceFlow.value as? PlaybackSource.MoviePlaybackSource)?.movie?.id
                val tiles = UserLocalData.inst.getMoviesHistory().filter { it.id != curPlayedMovieId }.take(MAX_TILES).mapNotNull { movie->
                    array.find { (it as? MovieTileVM)?.movie?.id == movie.id } ?: MovieTileVM(movie)
                }
                array = arrayOf<IHardtileViewModel>(gide, fav).plus(tiles).plus(clear)
                */
                array = emptyArray()
            } else {
                val curPlayedChannelId = (playbackSourceFlow.value as? PlaybackSource.ChannelPlaybackSource)?.channel?.id
                val tiles = UserLocalData.inst.channelHistory.value.filter { it != curPlayedChannelId }.take(MAX_TILES).mapNotNull { chId->
                    (array.find { (it as? ChannelTileVM)?.channel?.id == chId } ?: ChannelsCache.channels.value.data.firstOrNull { it.id == chId }?.let { ChannelTileVM(it) }) as? IHardtileViewModel
                }
                array = arrayOf<IHardtileViewModel>(gide).let {
                    if (playbackSourceFlow.value != null)it.plus(fav) else it
                }.plus(tiles).let {
                    if (tiles.isNotEmpty())it.plus(clear) else it
                }
            }
        }
    }

    private var containerView: TilesContainerView? = null

    init {
        setBackgroundColor(context.resources.getColor(R.color.player_control_shadow))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        containerView = vm.buildView(context, MainActivity.get(context)?.screenSize?.width ?: 0, vm.fixedSize) as TilesContainerView
        addView(containerView, LayoutParams(LayoutParams.MATCH_PARENT, vm.fixedSize))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        containerView?.freeAndVMNotify()
        containerView = null
    }

    fun requestInitialFocus(): Boolean {
        if (containerView?.focusToTileEx(vm.gide) == true)return true
        return requestFocus()
    }

    class ButtonGideVM(val parent: VM) : SimpleTileVM<ButtonGideView>(
        BaseApp.getDimension(R.dimen.hotbar_tile_width),
        BaseApp.getDimension(R.dimen.hotbar_tile_height), parent
    ) {
        override fun viewBuild(context: Context, width: Int, height: Int): ButtonGideView {
            return ButtonGideView(context, this)
        }
    }

    class ButtonGideView(context: Context, override val vm: ButtonGideVM) : SimpleTileCommonFrameLayout(context) {
        val vb = PlayerHotbarButtonBinding.inflate(LayoutInflater.from(context), this, true)

        init {
            setOnClickListener {
                MainActivity.get(context)?.openMenu()
            }
            vb.icon.setImageResource(R.drawable.hotbar_gide)
        }
    }

    class ButtonFavVM(val parent: VM) : SimpleTileVM<ButtonFavView>(
        BaseApp.getDimension(R.dimen.hotbar_tile_width),
        BaseApp.getDimension(R.dimen.hotbar_tile_height), parent
    ) {
        override fun viewBuild(context: Context, width: Int, height: Int): ButtonFavView {
            return ButtonFavView(context, this)
        }
    }

    class ButtonFavView(context: Context, override val vm: ButtonFavVM) : SimpleTileCommonFrameLayout(context) {
        val vb = PlayerHotbarButtonBinding.inflate(LayoutInflater.from(context), this, true)

        init {
            setOnClickListener {
                if (vm.parent.isMovieModeFlow.value) {
                    (vm.parent.playbackSourceFlow.value as? PlaybackSource.MoviePlaybackSource)?.movie?.let {
                        FavoriteMoviesCache.setFav(it, !FavoriteMoviesCache.isInFav(it))
                    }
                } else {
                    (vm.parent.playbackSourceFlow.value as? PlaybackSource.ChannelPlaybackSource)?.channel?.let {
                        FavoriteChannelsCache.setFav(it, !FavoriteChannelsCache.isInFav(it))
                    }
                }
            }
            vb.icon.setImageResource(R.drawable.hotbar_fav)
            update()
        }

        fun update() {
            val ps = vm.parent.playbackSourceFlow.value
            val fav = if (vm.parent.isMovieModeFlow.value)
                FavoriteMoviesCache.isInFav((ps as? PlaybackSource.MoviePlaybackSource)?.movie)
            else
                FavoriteChannelsCache.isInFav((ps as? PlaybackSource.ChannelPlaybackSource)?.channel)
            vb.icon.isSelected = fav
            isSelected = fav
        }
    }

    class ButtonClearVM(val parent: VM) : SimpleTileVM<ButtonClearView>(
        BaseApp.getDimension(R.dimen.hotbar_tile_width),
        BaseApp.getDimension(R.dimen.hotbar_tile_height)
    ) {
        override fun viewBuild(context: Context, width: Int, height: Int): ButtonClearView {
            return ButtonClearView(context, this)
        }
    }

    class ButtonClearView(context: Context, override val vm: ButtonClearVM) : SimpleTileCommonFrameLayout(context) {
        val vb = PlayerHotbarButtonBinding.inflate(LayoutInflater.from(context), this, true)

        init {
            setOnClickListener {
                MainActivity.get(context)?.let {
                    ClearHistoryDialog().show(it.supportFragmentManager, null)
                }
            }
            vb.icon.setImageResource(R.drawable.hotbar_del)
        }
    }

    class ClearHistoryDialog : UserDialog() {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setStyleQuestion()
            setTexts(resources.getString(R.string.clear_history_dlg_title), resources.getString(R.string.clear_history_dlg_mesage))
            addButton(resources.getString(R.string.clear_history_dlg_button)) {
                dismiss()
                if (MainActivity.get(context)?.isMovieMode == true) {
                    UserLocalData.inst.clearMoviesHistory()
                } else {
                    UserLocalData.inst.clearChannelsHistory()
                }
            }
            addButton(resources.getString(com.mc2soft.ontv.common.R.string.cancel)) {
                dismiss()
            }
        }
    }

    class ChannelTileVM(initialChannel: NetChannel) :
        CachedTileVM<ChannelTileVM, ChannelTileView>(ChannelTileView::class.java, BaseApp.getDimension(R.dimen.hotbar_tile_width), BaseApp.getDimension(R.dimen.hotbar_tile_height)) {

        var channel: NetChannel = initialChannel
            set(v) {
                if (field === v) return
                field = v
                view?.update()
            }
    }

    class ChannelTileView(context: Context) : HardtileCachedCommonFrameLayout<ChannelTileVM>(context) {
        val vb = PlayerHotbarTileBinding.inflate(LayoutInflater.from(context), this, true)

        init {
            setOnClickListener {
                vm?.channel?.let { ch->
                    MainActivity.get(context)?.player?.let { pl->
                        PlaybackSource.ChannelPlaybackSource(null, ch).loadUrlAndPlay(pl)
                    }
                }
            }
        }

        override fun update() {
            vb.text.text = LocalizeStringMap.translate(vm?.channel?.name)
            vb.image.load(vm?.channel?.playerLogoUrl)
        }
    }

    class MovieTileVM(initialMovie: NetMovieOrSeries) :
        CachedTileVM<MovieTileVM, MovieTileView>(MovieTileView::class.java, BaseApp.getDimension(R.dimen.hotbar_tile_width), BaseApp.getDimension(R.dimen.hotbar_tile_height)) {

        var movie: NetMovieOrSeries = initialMovie
            set(v) {
                if (field === v) return
                field = v
                view?.update()
            }
    }

    class MovieTileView(context: Context) : HardtileCachedCommonFrameLayout<MovieTileVM>(context) {
        val vb = PlayerHotbarTileBinding.inflate(LayoutInflater.from(context), this, true)

        init {
            isFocusable = true
            setOnClickListener {
                vm?.movie?.let { m->
                    MainActivity.get(context)?.player?.let { pl->
                        PlaybackSource.MoviePlaybackSource(m).loadUrlAndPlay(pl)
                    }
                }
            }
        }

        override fun update() {
            vb.text.text = LocalizeStringMap.translate(vm?.movie?.name)
            vb.image.load(vm?.movie?.playerLogoUrl)
        }
    }
}