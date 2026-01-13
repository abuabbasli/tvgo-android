package org.neonwindwalker.hardtiles

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

open class TilesContainerStdFocusVM : TilesScrollerVM() {
    override var parentVM: Any? = null

    var centerFocused = true
    var smoothScrolling = true

    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        tilesContainerView = TilesContainerStdFocusView(context, width, height, this).apply {
            init()
        }
        return tilesContainerView!!
    }

    override fun onPreFreeView(v: IHardtileView) {
        super.onPreFreeView(v)
        tilesContainerView = null
    }

    override var tilesContainerView: TilesContainerStdFocusView? = null

    override val tilesBuilderView: TilesBuilderView?
        get() = tilesContainerView

    override val tilesScrollerView: TilesScrollerView?
        get() = tilesContainerView

    var onTileFocusedCallback: ((index: Int)->Unit)? = null
}

open class TilesContainerStdFocusView(context: Context, width: Int, height: Int, override val vm: TilesContainerStdFocusVM) : TilesScrollerView(context, width, height, vm) {
    override fun checkAfterFocusLostInRebuild(removedFocusedViewRect: Rect) {
        if (onAfterFocusLostInRebuild?.invoke(removedFocusedViewRect) == true)
            return
        vm.upFind<IHardtileContainerViewModel>()?.tilesContainerView?.let { parentContainer->
            val r = Rect(removedFocusedViewRect)
            (parentContainer.asView as? ViewGroup)?.offsetDescendantRectToMyCoords(this, r)
            parentContainer.checkAfterFocusLostInRebuild(r)
        }
    }

    override fun checkBeforeFocusLostInRebuild(focusedTile: View) {
        if (onBeforeFocusLostInRebuild?.invoke(focusedTile) == true)
            return
        vm.upFind<IHardtileContainerViewModel>()?.tilesContainerView?.checkBeforeFocusLostInRebuild(focusedTile)
    }

    var onAfterFocusLostInRebuild: ((rect: Rect)->Boolean)? = null
    var onBeforeFocusLostInRebuild: ((focusedTile: View)->Boolean)? = null
    var requestChildFocusCallback: ((child: View, focused: View, container: TilesContainerStdFocusView)->Unit)? = null

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (inRebuild)return
        child?.let {
            val index = findArrayIndexOfView(it)!!
            vm.scrollToTileEx(index, if (vm.centerFocused) ScrollMode.Center else ScrollMode.InBounds, vm.smoothScrolling)
            vm.onTileFocusedCallback?.invoke(index)
            requestChildFocusCallback?.invoke(it, focused!!, this)
        }
    }

    override fun focusToTileEx(index: Int, scrollMode: ScrollMode, smoothScrollEnabled: Boolean, overrideStrategy : ScrollOverrideStrategy, innerContainerCallback: ((container: IHardtileContainerView)->Boolean)?): Boolean {
        vm.scrollToTileEx(index, scrollMode, smoothScrollEnabled, overrideStrategy,false)
        val tile = findTileViewForArrayIndex(index) ?: return false
        if (tile is IHardtileContainerView) {
            innerContainerCallback?.let {
                return it.invoke(tile)
            }
        }
        return tile.asView.requestFocus()
    }
}
