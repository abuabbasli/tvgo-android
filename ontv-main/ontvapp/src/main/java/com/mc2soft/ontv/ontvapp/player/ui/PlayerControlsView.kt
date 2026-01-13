package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.PlayerControlsBinding
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.player.PlayerView
import com.mc2soft.ontv.ontvapp.ui.PinCodeChangeDialog
import com.mc2soft.ontv.ontvapp.ui.PinCodeCheckDialog
import com.mc2soft.ontv.ontvapp.ui.TextFormatter

class PlayerControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    val vb = PlayerControlsBinding.inflate(LayoutInflater.from(context), this, true)

    lateinit var player: PlayerView

    init {
        vb.playPause.setOnClickListener {
            if (player.isPlaybackEnded) {
                player.seek = 0
                player.pause = false
            } else {
                player.pause = !player.pause
            }
        }
        vb.backward.setOnClickListener {
            player.seekProcessDirection = if (player.seekProcessDirection < 0) 0 else -1
        }
        vb.forward.setOnClickListener {
            player.seekProcessDirection = if (player.seekProcessDirection > 0) 0 else 1
        }
        vb.prev.setOnClickListener {
            (player.playbackSource as? PlaybackSource.IJumpable)?.let {
                it.jumpPrev()?.loadUrlAndPlay(player)
            } ?: run {
                player.seekProcessDirection = 0
                player.seek = 0
            }
        }
        vb.next.setOnClickListener {
            (player.playbackSource as? PlaybackSource.IJumpable)?.jumpNext()?.loadUrlAndPlay(player)
        }

        vb.audioSelect.setOnClickListener {
            MainActivity.get(context)?.let { act->
                SelectAudioTrackDialog().show(act.supportFragmentManager, null)
            }
        }

        vb.parental.setOnClickListener {
            MainActivity.get(context)?.let { act->
                PinCodeChangeDialog().show(act.supportFragmentManager, null)
            }
        }

        vb.lock.setOnClickListener {
            MainActivity.get(context)?.let { act->
                val contentItem = player.playbackSource?.netItem ?: return@let
                val locked = UserLocalData.inst.isParentalLock(contentItem)
                PinCodeCheckDialog {
                    UserLocalData.inst.setParentalLock(contentItem, !locked)
                }.show(act.supportFragmentManager, null)
            }
        }

        vb.live.setOnClickListener {
            player.channelPlaybackSource?.let { ps->
                PlaybackSource.ChannelPlaybackSource(ps.genre, ps.channel).loadUrlAndPlay(player)
            }
        }
    }

    //for hide text after 2 sec 00:00
    private var firstPrintZeroLiveOffsetTime: Long? = null

    fun tick() {
        val ps = player.playbackSource
        vb.playPause.setImageResource(
            if (player.isPlaybackEnded) R.drawable.player_play else { if (player.pause) R.drawable.player_play else R.drawable.player_pause })
        vb.backward.isSelected = player.seekProcessDirection < 0
        vb.forward.isSelected = player.seekProcessDirection > 0
        vb.playPause.isEnabled = ps?.isCanPause ?: false
        vb.prev.isEnabled = (ps as? PlaybackSource.IJumpable)?.let { it.isCanJumpPrev } ?: (ps is PlaybackSource.MoviePlaybackSource)
        vb.next.isEnabled = (ps as? PlaybackSource.IJumpable)?.isCanJumpNext ?: false
        vb.backward.isEnabled = ps?.isCanBackward ?: false
        vb.forward.isEnabled = ps?.isCanForward ?: false

        val position = player.userReadableSeek
        vb.time.text = when(position) {
            is PlaybackSource.SeekAndDuration -> {
                firstPrintZeroLiveOffsetTime = null
                TextFormatter.inst.printTimeAndDuration(position.seek, position.duration)
            }
            is PlaybackSource.AbsPosition -> {
                val offset = System.currentTimeMillis() - position.utcMS
                if (player.seekProcessDirection != 0) {
                    firstPrintZeroLiveOffsetTime = null
                    TextFormatter.inst.printLiveOffsetInSeek(offset)
                } else {
                    if (offset >= 60000) {
                        firstPrintZeroLiveOffsetTime = null
                        TextFormatter.inst.printLiveOffsetInSeek(offset)
                    } else { //zero offset
                        ""
                        /*
                        if (firstPrintZeroLiveOffsetTime == null) {
                            firstPrintZeroLiveOffsetTime = System.currentTimeMillis()
                        }
                        if (firstPrintZeroLiveOffsetTime?.let { System.currentTimeMillis() - it > 2000 } == true)
                            ""
                        else {
                            TextFormatter.inst.printLiveOffset(offset)
                        }
                         */
                    }
                }
            }
        }

        vb.audioSelect.visible(player.audioTracks.value.size > 1)
        vb.lock.isSelected = UserLocalData.inst.isParentalLock(player.playbackSource?.netItem)
        vb.lock.visible(player.playbackSource?.netItem != null)

        (position as? PlaybackSource.SeekAndDuration)?.let { pos->
            vb.seek.visible(true)
            vb.seek.percent = pos.progress
        } ?: run {
            vb.seek.visible(false)
        }

        vb.live.visible((ps as? PlaybackSource.ChannelPlaybackSource)?.isLive == false)
    }

    fun requestInitialFocus(): Boolean {
        if (vb.playPause.isEnabled) {
            return vb.playPause.requestFocus()
        }
        return requestFocus()
    }
}