package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.player.PlayerView
import org.neonwindwalker.hardtiles.rectOf

class PlayerHoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    var isOn: Boolean = false
        set(v) {
            field = v
            update()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        update()
    }

    fun update() {
        val act = MainActivity.get(context) ?: return
        val player = act.player
        if (!isOn) {
            player.outputRect = null
            player.resizeMode = PlayerView.DEFAULT_VIDEO_RESIZE_MODE
        } else if (width != 0 && height != 0) {
            player.outputRect = act.vb.root.rectOf(this)
            player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
    }
}