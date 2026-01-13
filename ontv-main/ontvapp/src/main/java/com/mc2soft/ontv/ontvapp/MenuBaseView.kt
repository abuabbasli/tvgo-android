package com.mc2soft.ontv.ontvapp

import android.view.ViewGroup
import android.widget.FrameLayout
import com.mc2soft.ontv.common.ui.isVisible
import com.mc2soft.ontv.common.ui.screenSize
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.ui.TempFocusView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.neonwindwalker.hardtiles.SaveFocusData
import org.neonwindwalker.hardtiles.TilesContainerVM
import org.neonwindwalker.hardtiles.TilesContainerView
import org.neonwindwalker.hardtiles.freeAndVMNotify

abstract class MenuBaseView(val activity: MainActivity) : FrameLayout(activity) {
    open class BaseVM : TilesContainerVM() {
        val scope = CoroutineScope(Dispatchers.Main)
        var savedSelectedDataItem: SaveFocusData? = null

        open fun destroy() {
            scope.cancel()
        }
    }

    abstract val vm: BaseVM
    protected var containerView: TilesContainerView? = null

    init {
        visibility = INVISIBLE
    }

    open fun build() {
        val size = activity.screenSize
        containerView = vm.buildView(context, size.width, size.height) as TilesContainerView
        containerView?.visible(false)
        findViewById<FrameLayout>(R.id.htroot).addView(containerView, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        containerView?.dispatchKeyEventLeaveFocusCallback = { focusDirection, containerView ->
            onDispatchKeyEventLeaveFocusCallback(focusDirection, containerView)
        }

        containerView?.onBeforeFocusLostInRebuild = {
            tempFocus()
        }
    }

    open fun destroy() {
        super.onDetachedFromWindow()
        containerView?.freeAndVMNotify()
        containerView = null
        vm.destroy()
    }

    open fun onDispatchKeyEventLeaveFocusCallback(focusDirection: Int, view: TilesContainerView): Boolean {
        return true
    }

    fun tempFocus(): Boolean {
        return findViewById<TempFocusView>(R.id.tempKeepFocusView)?.tempFocus() ?: false
    }

    fun mayKeepFocus(): Boolean {
        if (!isVisible || !isAttachedToWindow || !activity.isResume)return false
        return keepFocus()
    }

    open fun keepFocus(): Boolean {
        if (containerView?.hasFocus() != true) {
            return vm.closestFocus(vm.savedSelectedDataItem)
        }
        return false
    }

    open fun show() {
        if (showed)return
        visible(true)
        tempFocus()
        containerView?.visible(true)

        if (!SaveFocusData.restoreFocus(containerView, vm.savedSelectedDataItem)) {
            keepFocus()
        }
    }

    open fun hide() {
        if (!showed)return
        vm.savedSelectedDataItem = SaveFocusData.saveFocus(containerView)
        visible(false)
        containerView?.visible(false)
    }

    open val showed: Boolean
        get() = isVisible

    open fun tick() {}
}