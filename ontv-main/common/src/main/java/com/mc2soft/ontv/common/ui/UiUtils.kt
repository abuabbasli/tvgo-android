package com.mc2soft.ontv.common.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.Size
import android.util.TypedValue
import android.view.View
import com.mc2soft.ontv.common.BaseApp

object UiUtil {
    inline fun<reified T: Activity> getActivity(context: Context?): T? {
        var context = context
        while (context is ContextWrapper) {
            if (context is T) {
                return context
            }
            context = context.baseContext
        }
        return null
    }
}

val Activity.screenSize: Size
    get() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }



val View.isVisible get() = visibility == View.VISIBLE

fun View.visible(b: Boolean = true) {
    visibility = if (b) View.VISIBLE else View.GONE
}

fun Context.getDimension(id: Int): Int {
    return BaseApp.instance.applicationContext.resources.getDimensionPixelSize(id)
}

fun Int.asDpToPx() = this.toFloat().asDpToPx()

fun Float.asDpToPx() =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    ).toInt()


fun Resources.Theme.getAttrDrawable(attrId: Int): Drawable? {
    try {
        val typedValue = TypedValue()
        resolveAttribute(attrId, typedValue, true)
        return getDrawable(typedValue.resourceId)
    } catch (ex: java.lang.Exception) {
        BaseApp.handleError(ex)
        return null
    }
}

fun Resources.Theme.getAttrDrawableId(attrId: Int): Int? {
    try {
        val typedValue = TypedValue()
        resolveAttribute(attrId, typedValue, true)
        return typedValue.resourceId
    } catch (ex: java.lang.Exception) {
        BaseApp.handleError(ex)
        return null
    }
}

fun Resources.Theme.getAttrColor(attrId: Int): Int? {
    try {
        val typedValue = TypedValue()
        resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    } catch (ex: java.lang.Exception) {
        BaseApp.handleError(ex)
        return null
    }
}
