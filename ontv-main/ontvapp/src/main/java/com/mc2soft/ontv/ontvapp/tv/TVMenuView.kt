package com.mc2soft.ontv.ontvapp.tv

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import androidx.core.animation.doOnEnd
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.BuildConfig
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.MenuBaseView
import com.mc2soft.ontv.ontvapp.databinding.TvMenuBinding
import com.mc2soft.ontv.ontvapp.model.ChannelsCache
import com.mc2soft.ontv.ontvapp.model.FavoriteChannelsCache
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.*


class TVMenuView(activity: MainActivity) : MenuBaseView(activity) {
    class VM(val playbackSourceFlow: StateFlow<PlaybackSource?>, val activity: MainActivity) :
        BaseVM() {
        var initialFocusWasSet: Boolean = false

        val programs = ProgramsColVM(this)
        val channels = ChannelsColVM(this)
        val genres = TVGenresColVM(this, activity)

        var menuView: TVMenuView? = null

        init {
            savedScrollPosX = genres.fixedSize
            centerFocusedOnKey = false
            centerFocused = false
            array = arrayOf(genres, channels, programs)
            maxOverscrollAllowed = genres.fixedSize
            channels.genre = genres.genreAllTileVM()?.genre
            scope.launch {
                ChannelsCache.channels.collect {
                    channels.rebuild(false)
                }
            }
            scope.launch {
                ChannelsCache.totalProgramsLoaded.collect {
                    channels.update()
                }
            }
            scope.launch {
                FavoriteChannelsCache.overlaps.collect {
//                  if (channels.genre?.id == "-favorites-") {
//                  if (channels.genre == NetGenre.favorites) {
                    if (channels.genre?.id == NetGenre.favorites.id) {
                        channels.rebuild(false)
                    }
                }
            }
            scope.launch {
                UserLocalData.inst.channelHistory.collect {
                    if (channels.genre?.id === NetGenre.history.id) {
                        channels.rebuild(false)
                    }
                }
            }
            scope.launch {
                playbackSourceFlow.collect {
                    programs.updateTilesSelectedState()
                }
            }
        }
    }

    override val vm: VM = VM(activity.player.playbackSourceFlow, activity)

    val vb = TvMenuBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        vm.menuView = this
        build()
        vb.hole.setOnClickListener {
            activity.hideMenu(true)
        }
        vb.version.text = BuildConfig.VERSION_NAME
    }

    override fun destroy() {
        super.destroy()
        vm.menuView = null
    }

    override fun onDispatchKeyEventLeaveFocusCallback(
        focusDirection: Int,
        view: TilesContainerView
    ): Boolean {
        if (focusDirection == View.FOCUS_RIGHT) {
            val focused = view.findFocusedView()
            focused?.let {
                if (it.id == View.NO_ID) {
                    it.id = View.generateViewId()
                }
            }
            vb.hole.requestFocus()
            vb.hole.nextFocusLeftId = focused?.id ?: View.NO_ID
            vb.banner.nextFocusLeftId = focused?.id ?: View.NO_ID
            return true
        } else if (focusDirection == View.FOCUS_UP) {
            vm.channels.tilesContainerView?.let { tilesView ->
                if (tilesView.findTileViewForArrayIndex(0)?.asView?.hasFocus() == true) {
                    tilesView.focusToTileEx(vm.channels.array.size - 1, ScrollMode.InBounds, false)
                    return true
                }
            }
        } else if (focusDirection == View.FOCUS_DOWN) {
            vm.channels.tilesContainerView?.let { tilesView ->
                if (tilesView.findTileViewForArrayIndex(vm.channels.array.size - 1)?.asView?.hasFocus() == true) {
                    tilesView.focusToTileEx(0, ScrollMode.InBounds, false)
                    return true
                }
            }
        }
        return true
    }

    override fun show() {
        if (showed) return
        super.show()
        val playedChannelId = activity.channelPlaybackSource?.channel?.id
        if (playedChannelId != null && vm.programs.channel?.id != playedChannelId) {
            val chTile = vm.channels.find(playedChannelId) ?: run {
                vm.channels.genre = vm.genres.genreAllTileVM()?.genre
                vm.channels.find(playedChannelId)
            }
            vm.initialFocusWasSet =
                vm.channels.tilesContainerView?.focusToTileEx(chTile, ScrollMode.Center) == true
            if (!vm.initialFocusWasSet) {
                vm.genres.tilesContainerView?.focusToTileEx(vm.genres.find(vm.channels.genre?.id))
            }
        } else {
            if (!SaveFocusData.restoreFocus(containerView, vm.savedSelectedDataItem)) {
                keepFocus()
            }
        }
        vb.hole.isOn = true
        vb.banner.renew()
    }

    override fun hide() {
        if (!showed) return
        super.hide()
        vb.hole.isOn = false
    }

    override fun keepFocus(): Boolean {
        if (vm.channels.array.isNotEmpty()) {
            if (!vm.initialFocusWasSet || containerView?.hasFocus() != true) {
                if (vm.channels.tilesContainerView?.focusToTileEx(0) == true) {
                    vm.initialFocusWasSet = true
                    return true
                }
            }
        } else {
            if (containerView?.hasFocus() != true) {
                if (vm.genres.tilesContainerView?.focusToTileEx(vm.genres.find(vm.channels.genre?.id)) == true)
                    return true
            }
        }
        return super.keepFocus()
    }

    private var fadeAnimator: ObjectAnimator? = null

    fun updateExpandStatus() {
        vm.genres.tilesContainerView?.handler?.post {
            vm.programs.tilesContainerView?.let { programsCol ->
                if (vm.genres.tilesContainerView?.hasFocus() != true) {
                    programsCol.visible(true)
                    if (programsCol.alpha != 1.0f) {
                        fadeAnimator?.cancel()
                        fadeAnimator =
                            ObjectAnimator.ofFloat(programsCol, View.ALPHA, 0.0f, 1.0f).apply {
                                duration = vm.smoothScrollDuration.toLong()
                                startDelay = duration / 2
                                doOnEnd {
                                    programsCol.alpha = 1.0f
                                    fadeAnimator = null
                                }
                                start()
                            }
                    }
                } else {
                    fadeAnimator?.cancel()
                    fadeAnimator = null
                    programsCol.alpha = 0.0f
                    programsCol.visible(false)
                }
            }
        }
    }

    override fun tick() {
        vm.channels.update()
    }

    fun openChannel(ch: NetChannel) {
        val tile = vm.channels.find(ch.id) ?: run {
            vm.channels.genre = vm.genres.genreAllTileVM()?.genre
            vm.channels.find(ch.id)
        }
        vm.channels.tilesContainerView?.focusToTileEx(tile, ScrollMode.Center)
    }
}