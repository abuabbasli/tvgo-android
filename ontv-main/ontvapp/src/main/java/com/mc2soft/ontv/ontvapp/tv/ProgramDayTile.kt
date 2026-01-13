package com.mc2soft.ontv.ontvapp.tv

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.ProgramDayTileBinding
import com.mc2soft.ontv.ontvapp.ui.TextFormatter
import org.joda.time.DateTime
import org.neonwindwalker.hardtiles.CachedTileVM
import org.neonwindwalker.hardtiles.HardtileCachedCommonFrameLayout

class ProgramDayTileVM(val programsColVM: ProgramsColVM, initialDay: DateTime) :
        CachedTileVM<ProgramDayTileVM, ProgramDayTileView>(ProgramDayTileView::class.java,
            ViewGroup.LayoutParams.MATCH_PARENT, BaseApp.getDimension(R.dimen.program_day_tile_height), programsColVM) {

    var day: DateTime = initialDay
        set(v) {
            if (field == v)return
            field = v
            view?.update()
        }

    override val isAllwaysFocusable: Boolean
        get() = false

}

class ProgramDayTileView(con: Context) : HardtileCachedCommonFrameLayout<ProgramDayTileVM>(con) {
    val vb = ProgramDayTileBinding.inflate(LayoutInflater.from(context), this)

    override fun update() {
        vb.time.text = vm?.day?.let { day->
            TextFormatter.inst.printProgramDayTime(day.millis)
        }
    }
}