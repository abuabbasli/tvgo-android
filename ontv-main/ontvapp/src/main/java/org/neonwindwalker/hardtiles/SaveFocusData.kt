package org.neonwindwalker.hardtiles

import android.view.View

data class SaveFocusData(val vm: IHardtileViewModel, val track: List<IHardtileViewModel>) {
    companion object {
        fun saveFocus(rootView: IHardtileTraversable?): SaveFocusData? {
            var deepestView: IHardtileView? = null
            val track = ArrayList<IHardtileViewModel>()
            rootView?.traverse {
                val v = it as View
                if (!v.hasFocus()) {
                    IHardtileTraversable.BreakTraverseBranch
                } else {
                    (v as? IHardtileView)?.let {
                        deepestView = it
                        it.vm?.let { vm->
                            if (!track.contains(vm)) {
                                track.add(vm)
                            }
                        }
                    }
                    IHardtileTraversable.Continue
                }
            }
            deepestView?.vm?.let {
                return SaveFocusData(it, track)
            }
            return null
        }

        fun restoreFocus(rootView: IHardtileContainerView?, dat: SaveFocusData?): Boolean {
            dat ?: return false
            val vm = (rootView?.vm as? IHardtileTraversable)?.findTraverse<IHardtileViewModel> { it === dat.vm } ?: return false
            val xvm = (vm.parentVM as? IHardtileViewModel)?.thisOrUpFind<IHardtileContainerViewModel>() ?: return false
            val yvm = (xvm.parentVM as? IHardtileViewModel)?.thisOrUpFind<IHardtileContainerViewModel>()
            val ix = xvm.array.indexOf(vm)
            val iy = yvm?.array?.indexOf(xvm)
            return if (iy != null && iy >= 0) {
                rootView.focusToTileEx(iy, overrideStrategy = ScrollOverrideStrategy.WhenNewPos) {
                    it.focusToTileEx(ix, overrideStrategy = ScrollOverrideStrategy.WhenNewPos)
                }
            } else {
                rootView.focusToTileEx(ix, overrideStrategy = ScrollOverrideStrategy.WhenNewPos)
            }
            return true
        }
    }
}

