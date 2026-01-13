package com.mc2soft.ontv.ontvapp.tv

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.mc2soft.ontv.common.ui.visible

class BannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    init {
        setOnClickListener {

        }
    }

    fun renew() {
        visible(false)
    }
}