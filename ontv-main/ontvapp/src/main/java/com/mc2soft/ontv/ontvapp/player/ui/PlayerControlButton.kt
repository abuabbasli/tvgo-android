package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import com.mc2soft.ontv.common.ui.asDpToPx
import com.mc2soft.ontv.ontvapp.R

class PlayerControlButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    val icon = ImageView(context)
    val circle = ImageView(context)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(5.asDpToPx(), 5.asDpToPx(), 5.asDpToPx(), 5.asDpToPx())
        clipChildren = false
        clipToPadding = false
        circle.setBackgroundResource(R.drawable.player_control_button_bg)
        circle.isDuplicateParentStateEnabled = true
        icon.isDuplicateParentStateEnabled = true
        addView(circle, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        addView(icon, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.PlayerControlButton)
            if (a.hasValue(R.styleable.PlayerControlButton_player_control_icon)) {
                icon.setImageResource(a.getResourceId(R.styleable.PlayerControlButton_player_control_icon, 0))
            }
        }
    }

    fun setImageResource(id: Int) {
        icon.setImageResource(id)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (isFocused) {
            circle.startAnimation(AnimationUtils.loadAnimation(context, R.anim.player_control_circle))
        }
    }
}