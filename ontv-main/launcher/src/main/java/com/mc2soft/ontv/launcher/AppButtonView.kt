package com.mc2soft.ontv.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.mc2soft.ontv.launcher.databinding.AppButtonBinding

class AppButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    val vb = AppButtonBinding.inflate(LayoutInflater.from(context),this, true)

    init {
        isFocusable = true
        isFocusableInTouchMode = true

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.AppButtonView)
            if (a.hasValue(R.styleable.AppButtonView_app_button_title_text)) {
                try {
                    vb.text.text = a.getString(R.styleable.AppButtonView_app_button_title_text)
                } catch (ex: Exception) {
                }
            }
            if (a.hasValue(R.styleable.AppButtonView_app_button_icon)) {
                try {
                    val res = a.getResourceId(R.styleable.AppButtonView_app_button_icon, 0)
                    vb.icon.setImageResource(res)
                } catch (ex: Exception) {
                }
            }
        }
    }
}