package com.mc2soft.ontv.common.ui

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.mc2soft.ontv.common.R

class MyBackgroundView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : ShapeableImageView(context, attrs, defStyle) {

    var color: Int = 0
        private set
    var imageId: Int = 0
        private set
    var cornerRadius: Float = 0.0f
        private set

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MyBackgroundView)
            if (a.hasValue(R.styleable.MyBackgroundView_mybg_color)) {
                color = a.getColor(R.styleable.MyBackgroundView_mybg_color, 0)
            }
            if (a.hasValue(R.styleable.MyBackgroundView_mybg_image)) {
                imageId = a.getResourceId(R.styleable.MyBackgroundView_mybg_image, 0)
            }
            if (a.hasValue(R.styleable.MyBackgroundView_mybg_corner_radius)) {
                cornerRadius = a.getDimension(R.styleable.MyBackgroundView_mybg_corner_radius, 0.0f)
            }
        }

        scaleType = ScaleType.FIT_XY
        setBackgroundColor(color)
        setImageResource(imageId)

        if (cornerRadius > 0.0f) {
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                .build()
        }
    }
}