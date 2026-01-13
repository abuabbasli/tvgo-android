package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.settings.ISharedSettingsStorage
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.ui.isVisible
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.BuildConfig
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.databinding.PlayerOverlayBinding
import com.mc2soft.ontv.ontvapp.model.ChannelsCache
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.player.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    companion object {
        var FADE_TIME = 8000.toLong()
    }

    val vb = PlayerOverlayBinding.inflate(LayoutInflater.from(context), this, true)

    private lateinit var player: PlayerView

    fun init(act: MainActivity) {
        player = act.player
        vb.info.player = act.player
        vb.controls.player = act.player
        vb.hotbar.init(act)
    }

    init {
        vb.info.visible(false)
        vb.controls.visible(false)
        vb.hotbar.visible(false)
        vb.clock.visible(false)
        vb.bottomFrame.visible(false)
        vb.auth.visible(BuildConfig.DEBUG)
        //vb.vidSize.visible(BuildConfig.DEBUG)
    }

    fun tick() {
        vb.error.text = player.currentError?.message?.takeIf { player.isRertyAfterError != null }
        if (vb.info.isVisible)
            vb.info.tick()
        if (vb.controls.isVisible)
            vb.controls.tick()
        // Display resolution on player
        vb.vidSize.text = player.outputVideoSize?.let { "${it.width}x${it.height}" }
        vb.vidSize.visible(false)
    }

    private var hideJob: Job? = null

    fun show(controls: Boolean = false, hotbar: Boolean = false) {
        val prevFocused = hasFocus() && (vb.controls.isVisible || vb.hotbar.isVisible)

        if (controls) {
            vb.controls.visible(true)
        }
        if (hotbar) {
            vb.hotbar.visible(true)
        }

        vb.info.visible(true)
        vb.clock.visible(true)
        vb.bottomFrame.visible(true)

        vb.hotbar.vm.rebuild()
        tick()

        if (!prevFocused) {
            var focused = false
            if (vb.controls.isVisible) {
                focused = vb.controls.requestInitialFocus()
            }
            if (vb.hotbar.isVisible && !focused) {
                focused = vb.hotbar.requestInitialFocus()
            }
        }

        hideJob?.cancel()
        hideJob = player.scope?.launch {
            delay(FADE_TIME)
            while (player.seekProcessDirection != 0) {
                delay(FADE_TIME / 2)
            }
            hide()
        }

        if (vb.auth.isVisible) {
            vb.auth.text = "mac: ${AuthorizedUser.auth?.mac}"
        }
    }

    fun hide() {
        vb.info.visible(false)
        vb.controls.visible(false)
        vb.hotbar.visible(false)
        vb.clock.visible(false)
        vb.bottomFrame.visible(false)
        hideJob?.cancel()
    }

    val showed: Boolean
        get() = vb.info.isVisible || vb.controls.isVisible || vb.hotbar.isVisible

    val isFocusedViewsShowed: Boolean
        get() = vb.controls.isVisible || vb.hotbar.isVisible

    fun dispatchKeyDown(keyCode: Int, isMovieMode: Boolean): Boolean {
        if (keyCode !in arrayOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MENU
            )
        ) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (vb.hotbar.isVisible && !vb.controls.isVisible) {
                    show(true, !isMovieMode)
                    if (!isMovieMode) {
                        vb.controls.requestInitialFocus()
                    }
                    return true
                } else if (!isFocusedViewsShowed) {
                    player.channelPlaybackSource?.let { cps->
                        ChannelsCache.scrollChannelsLooped(cps.channel, 1, cps.genre)?.let {
                            PlaybackSource.ChannelPlaybackSource(cps.genre, it).showAccessDialogInNeed(context) {
                                it.loadUrlAndPlay(player)
                            }
                            return true
                        }
                    }
                    player.seriesPlaybackSource?.jumpNext()?.let {
                        it.loadUrlAndPlay(player)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (!isFocusedViewsShowed) {
                    player.channelPlaybackSource?.let { cps ->
                        ChannelsCache.scrollChannelsLooped(cps.channel, 1, cps.genre)?.let {
                            PlaybackSource.ChannelPlaybackSource(cps.genre, it)
                                .showAccessDialogInNeed(context) {
                                    it.loadUrlAndPlay(player)
                                }
                            return true
                        }
                    }
                    player.seriesPlaybackSource?.jumpNext()?.let {
                        it.loadUrlAndPlay(player)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (vb.controls.isVisible && !vb.hotbar.isVisible) {
                    show(true, !isMovieMode)
                    if (!isMovieMode) {
                        vb.hotbar.requestInitialFocus()
                    }
                    return true
                } else if (!isFocusedViewsShowed) {
                    player.channelPlaybackSource?.let { cps->
                        ChannelsCache.scrollChannelsLooped(cps.channel, -1, cps.genre)?.let {
                            PlaybackSource.ChannelPlaybackSource(cps.genre, it).showAccessDialogInNeed(context) {
                                it.loadUrlAndPlay(player)
                            }
                            return true
                        }
                    }
                    player.seriesPlaybackSource?.jumpPrev()?.let {
                        it.loadUrlAndPlay(player)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (!isFocusedViewsShowed) {
                    player.channelPlaybackSource?.let { cps->
                        ChannelsCache.scrollChannelsLooped(cps.channel, -1, cps.genre)?.let {
                            PlaybackSource.ChannelPlaybackSource(cps.genre, it).showAccessDialogInNeed(context) {
                                it.loadUrlAndPlay(player)
                            }
                            return true
                        }
                    }
                    player.seriesPlaybackSource?.jumpPrev()?.let {
                        it.loadUrlAndPlay(player)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player.pause = !player.pause
            }
            KeyEvent.KEYCODE_MENU -> {
                if (!vb.controls.isVisible) {
                    show(true, !isMovieMode)
                    if (!isMovieMode) {
                        vb.controls.requestInitialFocus()
                    }
                    return true
                } else {
                    hide()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER -> if (!vb.controls.isVisible && !vb.hotbar.isVisible) {
                show(isMovieMode, !isMovieMode)
                return true
            }
        }
        show()
        return false
    }
}