package com.mc2soft.ontv.ontvapp.unused

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.ui.TempFocusView
import org.neonwindwalker.hardtiles.SaveFocusData
import org.neonwindwalker.hardtiles.TilesContainerVM
import org.neonwindwalker.hardtiles.TilesContainerView
import org.neonwindwalker.hardtiles.freeAndVMNotify

open class BaseContainerFragment<TVM : BaseContainerFragment.BaseContainerVM>(override val vm: BaseContainerVM) : BaseFragment<BaseContainerFragment.BaseContainerVM>(vm) {
    open class BaseContainerVM : BaseVM() {
        val rootVM = TilesContainerVM().apply {
            parentVM = this@BaseContainerVM
            vertical = false
        }
        var savedSelectedDataItem: SaveFocusData? = null

        fun mayKeepFocusAfterLoadOrResume(): Boolean {
            return (fragment?.get() as? BaseContainerFragment<*>)?.mayKeepFocusAfterLoadOrResume() ?: false
        }
    }

    var containerView: TilesContainerView? = null
        private set

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val size = screenSize
        containerView = vm.rootVM.buildView(requireContext(), size.width, size.height) as TilesContainerView

        (view.findViewById(R.id.htroot) as FrameLayout).addView(containerView, 0, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        containerView?.dispatchKeyEventLeaveFocusCallback = { focusDirection, containerView ->
            onContainerViewDispatchKeyEventLeaveFocusCallback(focusDirection, containerView)
        }

        containerView?.onBeforeFocusLostInRebuild = { v ->
            view.findViewById<TempFocusView>(R.id.tempKeepFocusView)?.tempFocus() ?: false
        }
    }

    override fun onDestroyView() {
        containerView?.freeAndVMNotify()
        containerView = null
        super.onDestroyView()
    }

    open fun onContainerViewDispatchKeyEventLeaveFocusCallback(focusDirection: Int, view: TilesContainerView): Boolean {
        return true
    }

    override fun onSaveFocus() {
        vm.savedSelectedDataItem = SaveFocusData.saveFocus(containerView)
    }

    override fun onRestoreFocus() {
        if (!SaveFocusData.restoreFocus(containerView, vm.savedSelectedDataItem)) {
            keepFocusAfterLoadOrResume()
        }
    }

    fun mayKeepFocusAfterLoadOrResume(): Boolean {
        if (!isFocusAvailable)return false
        return keepFocusAfterLoadOrResume()
    }

    open fun keepFocusAfterLoadOrResume(): Boolean {
        if (view?.hasFocus() != false)return false
        return vm.rootVM.closestFocus(vm.savedSelectedDataItem)
    }
}