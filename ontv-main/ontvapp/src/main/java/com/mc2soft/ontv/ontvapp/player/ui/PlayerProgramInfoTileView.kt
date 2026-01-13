package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannelProgram
import com.mc2soft.ontv.ontvapp.databinding.PlayerProgramInfoTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.ui.TextFormatter

class PlayerProgramInfoTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    val vb = PlayerProgramInfoTileBinding.inflate(LayoutInflater.from(context), this, true)

    fun update(program: NetChannelProgram) {
        vb.time.text = TextFormatter.inst.printProgramTime(program.startTimeMS) + " - " +
                TextFormatter.inst.printProgramTime(program.stopTimeMS)
        vb.name.text = LocalizeStringMap.translate(program.name)
    }
}