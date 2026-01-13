package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.mc2soft.ontv.ontvapp.databinding.PlayerSeekLineBinding

class PlayerSeekLineView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    val vb = PlayerSeekLineBinding.inflate(LayoutInflater.from(context), this, true)

    var percent: Float = 0.0f
        set(v) {
            field = v
            update()
        }
    fun update() {
        vb.viewProgress.layoutParams.width = (vb.viewTotalProgress.width * percent).toInt()
        (vb.viewThumb.layoutParams as LayoutParams).leftMargin = vb.viewProgress.layoutParams.width - vb.viewThumb.layoutParams.width / 2

        vb.viewProgress.requestLayout()
        vb.viewThumb.requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        update()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        update()
    }
}