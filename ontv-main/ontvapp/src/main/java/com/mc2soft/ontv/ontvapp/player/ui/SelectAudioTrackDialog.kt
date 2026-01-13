package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.ui.BaseDialog
import com.mc2soft.ontv.common.ui.screenSize
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.PlayerSelectAudioTrackDialogBinding
import com.mc2soft.ontv.ontvapp.databinding.PlayerSelectAudioTrackTileBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.player.AdaptExoPlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.*

class SelectAudioTrackDialog() : BaseDialog() {

    class VM(val tracks: StateFlow<List<AdaptExoPlayerView.TrackInfo>>,
             val selected: StateFlow<AdaptExoPlayerView.TrackInfo?>,
             val dialog: SelectAudioTrackDialog) : TilesContainerVM() {
        val scope = CoroutineScope(Dispatchers.Main)
        init {
            vertical = true
            fixedSize = BaseApp.getDimension(R.dimen.player_select_audio_track_dlg_width)
            scope.launch {
                tracks.collect {
                    rebuild()
                }
            }
            scope.launch {
                selected.collect {
                    array.forEach {
                        (it as? TileVM)?.view?.update()
                    }
                }
            }
            rebuild()
        }

        fun destroy() {
            scope.cancel()
        }

        fun rebuild() {
            array = tracks.value.map { tr->
                find(tr)?.also { it.track = tr } ?: TileVM(this, tr)
            }.toTypedArray()
        }

        fun find(tr: AdaptExoPlayerView.TrackInfo?): TileVM? {
            return array.find { (it as? TileVM)?.let { it.track == tr } == true } as? TileVM
        }
    }

    private var vb: PlayerSelectAudioTrackDialogBinding? = null
    private var vm: VM? = null
    private var containerView: TilesContainerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val player = MainActivity.get(context)!!.player
        vm = VM(player.audioTracks, player.userSelectedAudioTrack, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        vm?.destroy()
        vm = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        vb = PlayerSelectAudioTrackDialogBinding.inflate(inflater, container, false)
        return vb!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        containerView = vm!!.buildView(view.context, vm!!.fixedSize, MainActivity.get(context)?.screenSize?.height ?: 0) as TilesContainerView
        vb!!.hthost.addView(containerView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vb = null
        containerView?.freeAndVMNotify()
        containerView = null
    }

    override fun onResume() {
        super.onResume()
        if (containerView?.focusToTileEx(vm?.find(vm?.selected?.value)) != true)
            containerView?.focusAny()
    }

    class TileVM(val parent: VM, initialTrack: AdaptExoPlayerView.TrackInfo) : SimpleTileVM<TileView>(
        ViewGroup.LayoutParams.MATCH_PARENT,
        BaseApp.getDimension(R.dimen.player_select_audio_track_tile_height), parent
    ) {
        var track: AdaptExoPlayerView.TrackInfo = initialTrack
            set(v) {
                field = v
                view?.update()
            }

        override fun viewBuild(context: Context, width: Int, height: Int): TileView {
            return TileView(context, this)
        }
    }

    class TileView(context: Context, override val vm: TileVM) : SimpleTileCommonFrameLayout(context) {
        val vb = PlayerSelectAudioTrackTileBinding.inflate(LayoutInflater.from(context), this, true)

        init {
            setOnClickListener {
                vm.parent.dialog.dismiss()
                MainActivity.get(context)?.player?.setSelectedAudioTrack(vm.track, true)
            }
            update()
        }

        fun update() {
            vb.text.text = LocalizeStringMap.translate(vm.track.name)
            isSelected = vm.parent.selected.value == vm.track
        }
    }
}