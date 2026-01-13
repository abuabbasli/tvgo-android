package com.mc2soft.ontv.ontvapp.tv

import android.graphics.Rect
import android.view.View
import androidx.core.view.forEach
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.model.ChannelsCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.neonwindwalker.hardtiles.*

class ProgramsColVM(val menuVM: TVMenuView.VM) : TilesContainerVM() {
    init {
        vertical = true
        centerFocused = false
        centerFocusedOnKey = false
        topPadding = BaseApp.getDimension(R.dimen.channel_col_header_height)
        bottomPadding = topPadding / 2
        fixedSize = leftPadding + BaseApp.getDimension(R.dimen.program_tile_width)
    }

    private var watchProgramsLoadJob: Job? = null

    var channel: NetChannel? = null
        set(v) {
            if (field?.id == v?.id)return
            field = v
            watchProgramsLoadJob?.cancel()
            watchProgramsLoadJob = menuVM.scope.launch {
                channel?.let { ch ->
                    ChannelsCache.triggerUpdatePrograms(ch.id).collect {
                        rebuild(false)
                    }
                }
            }
            rebuild(true)
            menuVM.channels.updateTilesSelectedState()
        }

    fun rebuild(initial: Boolean) {
        if (initial) {
            array = emptyArray()
        }

        val netlist = ChannelsCache.getPrograms(channel?.id)?.value ?: run {
            array = emptyArray()
            return
        }

        var day: DateTime? = null
        val totList = ArrayList<IHardtileViewModel>()
        netlist.forEach { pr->
            val prTile = find(pr.id)?.apply { program = pr } ?: ProgramTileVM(this@ProgramsColVM, pr)
            val d = DateTime(prTile.program.startTimeMS).let { DateTime(it.year, it.monthOfYear, it.dayOfMonth, 0, 0, 0, 0) }
            if (day != d) {
                day = d
                totList += find(d)?.apply { day = d } ?: ProgramDayTileVM(this@ProgramsColVM, d)
            }
            totList += prTile
        }

        array = totList.toTypedArray()
    }

    fun find(id: Int?): ProgramTileVM? {
        id ?: return null
        return array.find { (it as? ProgramTileVM)?.program?.id == id } as? ProgramTileVM
    }

    fun find(t: DateTime?): ProgramDayTileVM? {
        t ?: return null
        return array.find { (it as? ProgramDayTileVM)?.day == t } as? ProgramDayTileVM
    }

    var lastFocusedProgramId: Int? = null

    override fun overrideDefaultFocusOnMove(focusedRect: Rect, direction: Int, ignoreFocusView: View?): SingleValueContainer<View?>? {
        return SingleValueContainer(tilesBuilderView?.findTileViewForVM(find(lastFocusedProgramId))?.asView)
    }

    override fun calcScrollInRebuild(crdArray: IntArray, size: Int, reason: IHardtileContainerView.RebuildReason, prevScroll: Int, prevAllTilesTotalSize: Int): Int? {
        return when (reason) {
            IHardtileContainerView.RebuildReason.Initial -> {
                val now = DateTime(System.currentTimeMillis())
                val thisDay = DateTime(now.year, now.monthOfYear, now.dayOfMonth, 0, 0, 0, 0)
                array.indexOfLast {
                    (it as? ProgramDayTileVM)?.day == thisDay
                }.takeIf { it >= 0 }?.let {
                    crdArray.getOrNull(it)
                }
            }
            IHardtileContainerView.RebuildReason.TilesArrayChanged ->
                prevScroll + (crdArray.last() - prevAllTilesTotalSize)
            else -> null
        }
    }

    fun updateTilesSelectedState() {
        tilesContainerView?.forEach {
            (it as? ProgramTileView)?.updateSelectedState()
        }
    }

    override fun onTileFocused(index: Int) {
        if (index == 1) {
            ChannelsCache.triggerLoadMorePrevPrograms(channel?.id)
        }
        lastFocusedProgramId = (array.getOrNull(index) as? ProgramTileVM)?.program?.id
    }
}
