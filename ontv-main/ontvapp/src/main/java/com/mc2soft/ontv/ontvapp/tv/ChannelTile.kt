package com.mc2soft.ontv.ontvapp.tv

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.common.ui.getAttrDrawableId
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.ChannelTileBinding
import com.mc2soft.ontv.ontvapp.model.ChannelsCache
import com.mc2soft.ontv.ontvapp.model.FavoriteChannelsCache
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.ui.load
import org.neonwindwalker.hardtiles.CachedTileVM
import org.neonwindwalker.hardtiles.IHardtileCachedView
import org.neonwindwalker.hardtiles.ScrollMode
import org.neonwindwalker.hardtiles.ScrollOverrideStrategy

class ChannelTileVM(val channelsColVM: ChannelsColVM, initialChannel: NetChannel) :
        CachedTileVM<ChannelTileVM, ChannelTileView>(ChannelTileView::class.java,
            ViewGroup.LayoutParams.MATCH_PARENT, BaseApp.getDimension(R.dimen.channel_tile_height), channelsColVM) {

    var channel: NetChannel = initialChannel
        set(v) {
            if (field === v)return
            field = v
            view?.update()
        }
}

class ChannelTileView(con: Context) : LinearLayout(con), IHardtileCachedView<ChannelTileVM> {
    override var vm: ChannelTileVM? = null
        set(v) {
            if (field === v)return
            field = v
            update()
            v?.channel?.id?.let {
                ChannelsCache.triggerUpdatePrograms(it) //для обновления подписи текущей программы на канале
            }
        }

    val vb = ChannelTileBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = HORIZONTAL
        setBackgroundResource(context.theme.getAttrDrawableId(R.attr.theme_channel_tile_bg) ?: 0)

        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                vm?.let { vm->
                    vm.channelsColVM.selectOnFocus(vm)
                    vm.channelsColVM.menuVM.scrollToTileEx(vm.channelsColVM, ScrollMode.ToLeftOrTop, smoothScrollEnabled = true, ScrollOverrideStrategy.Allways)
                }
            }
            vb.channelTitle.isSelected = hasFocus
            vb.channelSubtitle.isSelected = hasFocus
        }

        setOnClickListener {
            val vm = vm ?: return@setOnClickListener
            val act = MainActivity.get(context) ?: return@setOnClickListener
            if (vm.channel.id == act.channelPlaybackSource?.channel?.id && act.channelPlaybackSource?.isLive == true) {
                act.hideMenu(true)
            } else {
                PlaybackSource.ChannelPlaybackSource(vm.channelsColVM.genre, vm.channel).showAccessDialogInNeed(context) {
                    it.loadUrlAndPlay(act.player)
                }
            }
        }
    }

    fun update() {
        val channel = vm?.channel ?: return
        vb.channelTitle.text = "${channel.number ?: ""} ${LocalizeStringMap.translate(channel.name)}"
        ChannelsCache.getLiveProgram(channel.id)?.let {
            vb.channelSubtitle.text = LocalizeStringMap.translate(it.name)
            vb.channelSubtitle.visibility = View.VISIBLE
            setProgress((System.currentTimeMillis() -  it.startTimeMS).toFloat() / ((it.stop_timestamp - it.start_timestamp)*1000))
        } ?: run {
            vb.channelSubtitle.visibility = View.INVISIBLE
            setProgress(0.0f)
        }

        vb.iconPlayedNow.visible(MainActivity.get(context)?.channelPlaybackSource?.let { it.channel.id == channel.id } ?: false)
        vb.iconHaveArchive.visible(channel.isHaveArchive)
        vb.iconFav.visible(FavoriteChannelsCache.isInFav(channel))
        vb.iconLock.visible(UserLocalData.inst.isParentalLockOrCensored(channel))

        vb.image.load(channel.tileImageUrl)
        updateSelectedState()
    }

    fun setProgress(v: Float) {
        (vb.progressView.layoutParams as LinearLayout.LayoutParams).weight = v
        vb.progressView.requestLayout()
    }

    fun updateSelectedState() {
        val selId = vm?.channelsColVM?.menuVM?.programs?.channel?.id
        isSelected = vm?.channel?.id == selId && selId != null
    }
}