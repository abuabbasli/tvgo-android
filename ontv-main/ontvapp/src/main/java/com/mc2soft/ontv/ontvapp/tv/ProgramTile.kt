package com.mc2soft.ontv.ontvapp.tv

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannelProgram
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.ProgramTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.ui.TextFormatter
import org.neonwindwalker.hardtiles.CachedTileVM
import org.neonwindwalker.hardtiles.HardtileCachedCommonLinearLayout

class ProgramTileVM(val programsColVM: ProgramsColVM, initialChannel: NetChannelProgram) :
        CachedTileVM<ProgramTileVM, ProgramTileView>(ProgramTileView::class.java,
            ViewGroup.LayoutParams.MATCH_PARENT, BaseApp.getDimension(R.dimen.program_tile_height), programsColVM) {

    var program: NetChannelProgram = initialChannel
        set(v) {
            if (field === v)return
            field = v
            view?.update()
        }
}

class ProgramTileView(con: Context) : HardtileCachedCommonLinearLayout<ProgramTileVM>(con) {
    val vb = ProgramTileBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.program_tile_bg)

        setOnFocusChangeListener { view, hasFocus ->
            vb.name.isSelected = hasFocus
        }

        setOnClickListener {
            val vm = vm ?: return@setOnClickListener
            if (!vm.program.isWasStarted)return@setOnClickListener
            val channel = vm.programsColVM.channel ?: return@setOnClickListener
            val genre = vm.programsColVM.menuVM.channels.genre ?: return@setOnClickListener
            val act = MainActivity.get(context) ?: return@setOnClickListener
            if (vm.program.id == act.channelPlaybackSource?.programOrLiveProgram?.id) {
                act.hideMenu(true)
            } else {
                PlaybackSource.ChannelPlaybackSource(genre, channel, vm.program).showAccessDialogInNeed(act) {
                    it.loadUrlAndPlay(act.player)
                }
            }
        }
    }

    override fun update() {
        vm?.program?.let { program->
            vb.time.text = TextFormatter.inst.printProgramTime(program.startTimeMS)
            vb.name.text = LocalizeStringMap.translate(program.name)
            vb.name.alpha = if (program.isWasStarted) 1.0f else 0.5f
            vb.time.alpha = vb.name.alpha
        }
        updateSelectedState()
    }

    fun updateSelectedState() {
        val playedProgramId = MainActivity.get(context)?.channelPlaybackSource?.let {
            it.liveProgram?.id ?: it.program?.id
        }
        isSelected = vm?.program?.id == playedProgramId && playedProgramId != null
    }
}