package com.mc2soft.ontv.ontvapp.tv

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.forEach
import com.google.android.material.color.MaterialColors
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.ChannelColHeaderBinding
import com.mc2soft.ontv.ontvapp.model.ChannelsCache
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.*

class ChannelsColVM(val menuVM: TVMenuView.VM) : TilesContainerVM() {
    companion object {
        const val DELAY_BEFORE_OPEN_PROGRAMS = 500L
    }
    init {
        vertical = true
        fixedSize = BaseApp.getDimension(R.dimen.channel_tile_width)
        bottomPadding = topPadding
    }

    var genre: NetGenre? = null
        set(v) {
            if (field?.id == v?.id)return
            field = v
            rebuild(true)
            menuVM.genres.updateTilesSelectedState()
            menuVM.programs.channel = null
            view?.updateHeader()
        }

    fun rebuild(initial: Boolean) {
        val allChannels = ChannelsCache.channels.value.data
        val tiles = if (genre?.id == NetGenre.history.id) {
            UserLocalData.inst.channelHistory.value.mapNotNull { hId->
                allChannels.find { it.id == hId }
            }
        } else {
            allChannels.filter {
                UserLocalData.inst.isInGenre(it, genre)
            }
        }.map {
            find(it.id)?.apply { channel = it } ?: ChannelTileVM(this@ChannelsColVM, it)
        }

        if (initial) {
            val wasFocus = tilesContainerView?.hasFocus() == true
            array = emptyArray()
            array = tiles.toTypedArray()
            if (wasFocus) {
                tilesContainerView?.focusToTileEx(0, ScrollMode.ToLeftOrTop, false)
            } else {
                scrollToBegin(false)
            }
        } else {
            array = tiles.toTypedArray()
        }
        menuVM.menuView?.mayKeepFocus()
    }

    fun update() {
        tilesContainerView?.forEach {
            (it as? ChannelTileView)?.update()
        }
    }

    fun find(id: Int?): ChannelTileVM? {
        val id = id ?: return null
        return array.find { (it as? ChannelTileVM)?.channel?.id == id } as? ChannelTileVM
    }

    override fun overrideDefaultFocusOnMove(focusedRect: Rect, direction: Int, ignoreFocusView: View?): SingleValueContainer<View?>? {
        return SingleValueContainer(tilesBuilderView?.findTileViewForVM(find(menuVM.programs.channel?.id))?.asView)
    }
    fun updateTilesSelectedState() {
        tilesContainerView?.forEach {
            (it as? ChannelTileView)?.updateSelectedState()
        }
    }

    private var openOnFocusJob: Job? = null

    fun selectOnFocus(ch: ChannelTileVM) {
        openOnFocusJob?.cancel()
        openOnFocusJob = null
        if (menuVM.programs.channel?.id == ch.channel.id)
            return
        menuVM.programs.channel = null
        if (ChannelsCache.getPrograms(ch.channel.id)?.value?.isNotEmpty() == true) {
            menuVM.programs.channel = ch.channel
        } else {
            openOnFocusJob = menuVM.scope.launch {
                delay(DELAY_BEFORE_OPEN_PROGRAMS)
                if (ch.view?.hasFocus() == true) {
                    menuVM.programs.channel = ch.channel
                }
            }
        }
    }

    private var view: ChannelsColView? = null
    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        view = ChannelsColView(super.buildView(context, width, fixedSize) as TilesContainerView, this)
        return view!!
    }

    override fun onPreFreeView(v: IHardtileView) {
        super.onPreFreeView(v)
        view = null
    }
}

open class ChannelsColView(val container: TilesContainerView, override val vm: ChannelsColVM) : LinearLayout(container.context), IHardtileContainerView, IHardtileTraversable {
    override fun checkAfterFocusLostInRebuild(removedFocusedViewRect: Rect) {
        container.checkAfterFocusLostInRebuild(removedFocusedViewRect)
    }

    override fun checkBeforeFocusLostInRebuild(focusedTile: View) {
        container.checkBeforeFocusLostInRebuild(focusedTile)
    }

    override fun focusToTileEx(index: Int, scrollMode: ScrollMode, smoothScrollEnabled: Boolean, overrideStrategy: ScrollOverrideStrategy, innerContainerCallback: ((container: IHardtileContainerView) -> Boolean)?): Boolean {
        return container.focusToTileEx(index, scrollMode, smoothScrollEnabled, overrideStrategy, innerContainerCallback)
    }

    override fun focusAny(): Boolean {
        return container.focusAny()
    }

    override fun rebuild(reason: IHardtileContainerView.RebuildReason) {
        container.rebuild(reason)
    }

    override fun postRebuild(reason: IHardtileContainerView.RebuildReason) {
        container.postRebuild(reason)
    }

    override fun findArrayIndexOfView(v: View?): Int? {
        return container.findArrayIndexOfView(v)
    }

    override fun findTileViewForArrayIndex(index: Int): IHardtileView? {
        return container.findTileViewForArrayIndex(index)
    }

    override fun findTileViewForVM(element: IHardtileViewModel?): IHardtileView? {
        return container.findTileViewForVM(element)
    }


    val vb = ChannelColHeaderBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        orientation = VERTICAL
        addView(container, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        updateHeader()
        setBackgroundColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
    }

    override fun free() {
        container.free()
    }

    override fun traverse(cb: (v: Any) -> IHardtileTraversable.ReturnValue): IHardtileTraversable.ReturnValue {
        return container.traverse(cb)
    }

    fun updateHeader() {
        vb.text.text = LocalizeStringMap.translate(vm.genre?.title)
    }
}

