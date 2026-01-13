package com.mc2soft.ontv.ontvapp.ui

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.mc2soft.ontv.ontvapp.databinding.MyClockBinding

class MyClockView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    private val vb = MyClockBinding.inflate(LayoutInflater.from(context),this, true)

    val tickHandler = Handler()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tick()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tickHandler.removeCallbacksAndMessages(null)
    }

    fun tick() {
        tickHandler.removeCallbacksAndMessages(null)
        tickHandler.postDelayed({ tick() }, 1000)

        vb.text.text = TextFormatter.inst.printClockTime()
    }
}