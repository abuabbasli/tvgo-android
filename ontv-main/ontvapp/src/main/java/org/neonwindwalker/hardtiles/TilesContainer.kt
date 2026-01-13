package org.neonwindwalker.hardtiles

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import org.neonwindwalker.hardtiles.FocusFinder.findFixedNextFocus
import org.neonwindwalker.hardtiles.FocusFinder.findNextFocusFromRect

open class TilesContainerVM : TilesScrollerVM() {
    companion object {
        var defaultMinMoveAgainOnKeyTime = 80
    }

    override var parentVM: Any? = null

    var onlySmoothScrollingOnKey = false
    var centerFocusedOnKey = true
    var centerFocused = false
    var minMoveAgainOnKeyTime = defaultMinMoveAgainOnKeyTime

    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        tilesContainerView = TilesContainerView(context, width, height, this).apply {
            init()
        }
        return tilesContainerView!!
    }

    override fun onPreFreeView(v: IHardtileView) {
        super.onPreFreeView(v)
        tilesContainerView = null
    }

    override var tilesContainerView: TilesContainerView? = null

    override val tilesBuilderView: TilesBuilderView?
        get() = tilesContainerView

    override val tilesScrollerView: TilesScrollerView?
        get() = tilesContainerView

    fun closestFocus(savedSelectedDataItem: SaveFocusData?): Boolean {
        var deepestVM: IHardtileContainerViewModel = this
        traverse {
            if (it === this) {
                IHardtileTraversable.Continue
            } else if (savedSelectedDataItem?.track?.contains(it) == true) {
                (it as? IHardtileContainerViewModel)?.let {
                    deepestVM.scrollToTileEx(it)
                    deepestVM = it
                }
                IHardtileTraversable.Continue
            } else {
                IHardtileTraversable.BreakTraverseBranch
            }
        }
        return deepestVM.tilesContainerView?.focusAny() ?: false
    }
}

open class TilesContainerView(context: Context, width: Int, height: Int, override val vm: TilesContainerVM) : TilesScrollerView(context, width, height, vm) {
    protected var pendingMoveDirection: Int = 0
    protected var pendingMoveEnableSmoothScroll = false

    val isCanSmoothScrolling: Boolean
        get() = vm.onlySmoothScrollingOnKey || (vm.target?.isFinished != false && System.currentTimeMillis() - lastHardScrollSetTime > Math.max(vm.smoothScrollDuration, vm.minMoveAgainOnKeyTime))

    private fun postHandlePendingMove(dir: Int, smooth: Boolean, delay: Long = 10) {
        pendingMoveDirection = dir
        pendingMoveEnableSmoothScroll = smooth
        moveAgainHandler.removeCallbacksAndMessages(null)
        moveAgainHandler.postDelayed({
            if (pendingMoveDirection == 0)return@postDelayed
            if (vm.target?.isFinished == false){ //лучше ожидать чем ждать колбеки о финализации скролла, так надежнее и стек вызова проще
                postHandlePendingMove(pendingMoveDirection, pendingMoveEnableSmoothScroll)
                return@postDelayed
            }
            val xdir = pendingMoveDirection
            val xsmooth = pendingMoveEnableSmoothScroll
            pendingMoveDirection = 0
            pendingMoveEnableSmoothScroll = false
            move(xdir, xsmooth)
        }, delay)
    }

    var onBeforeFocusLostInRebuild: ((focusedTile: View)->Boolean)? = null
    var onAfterFocusLostInRebuild: ((rect: Rect)->Boolean)? = null

    override fun checkBeforeFocusLostInRebuild(focusedTile: View) {
        if (onBeforeFocusLostInRebuild?.invoke(focusedTile) == true)
            return
        vm.upFind<IHardtileContainerViewModel>()?.tilesContainerView?.checkBeforeFocusLostInRebuild(focusedTile)
    }

    override fun checkAfterFocusLostInRebuild(removedFocusedViewRect: Rect) {
        if (onAfterFocusLostInRebuild?.invoke(removedFocusedViewRect) == true)
            return
        var bestIntersectView: View? = null
        var bestIntersectSquare = -1
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (!v.isAnyFocusableExist)continue
            val r = actualRectOfChildTile(v)
            if (!r.intersect(removedFocusedViewRect))continue
            val s = (Math.min(r.right, removedFocusedViewRect.right) - Math.max(r.left, removedFocusedViewRect.left)) *
                    (Math.min(r.bottom, removedFocusedViewRect.bottom) - Math.max(r.top, removedFocusedViewRect.top))
            if (s > bestIntersectSquare) {
                bestIntersectSquare = s
                bestIntersectView = v
            }
        }
        bestIntersectView?.let { bestIntersectView->
            val r = actualRectOfChildTile(bestIntersectView)
            val shiftedRect = Rect(removedFocusedViewRect)
            shiftedRect.left -= r.left
            shiftedRect.right -= r.left
            shiftedRect.top -= r.top
            shiftedRect.bottom -= r.top
            FocusFinder.findFocusInFromRect(bestIntersectView, shiftedRect, true)?.requestFocus()
            return
        }

        if (FocusFinder.findFocusInFromRect(this, removedFocusedViewRect, false)?.requestFocus() == true)return

        vm.upFind<IHardtileContainerViewModel>()?.tilesContainerView?.let { parentContainer->
            val r = Rect(removedFocusedViewRect)
            (parentContainer.asView as? ViewGroup)?.offsetDescendantRectToMyCoords(this, r)
            parentContainer.checkAfterFocusLostInRebuild(r)
        }
    }

    enum class MoveStatus {
        FocusMoved,
        OnlyScrolled,
        WantLeaveMajorDirection,
        WantLeaveMinorDirection,
        PendingLater,
        Invalid,
        NoChanges
    }

    private val isViewInvalidForFocusSearch: Boolean
        get() = width == 0 || height == 0 || visibility != View.VISIBLE

    private val moveAgainHandler = Handler()

    fun move(focusDirection: Int, smoothScrollEnable: Boolean): MoveStatus {
        if (isViewInvalidForFocusSearch)return MoveStatus.Invalid
        val idir = when (focusDirection) {
            View.FOCUS_LEFT, View.FOCUS_UP -> -1
            else -> 1
        }

        val scrollMode = if (vm.centerFocusedOnKey) ScrollMode.Center else {
            if (idir < 0) ScrollMode.InBoundsLeftOrTop
            else ScrollMode.InBoundsRightOrBottom
        }

        val focusedView = findFocus() ?: return MoveStatus.NoChanges
        val focusedTile = childContainsView(focusedView) ?: return MoveStatus.NoChanges
        val focusedRect = rectOf(focusedView)
        var nextFocused = findFixedNextFocus(focusedView, focusDirection, focusedTile) ?: findNextFocusFromRect(focusedTile, focusedRect, focusDirection, focusedView)
        if (nextFocused == null) {
            val majorDirection = if (vm.vertical) focusDirection == View.FOCUS_UP || focusDirection == View.FOCUS_DOWN else focusDirection ==  View.FOCUS_LEFT || focusDirection == View.FOCUS_RIGHT
            if (majorDirection) {
                if (pendingMoveDirection != 0) {
                    pendingMoveDirection = focusDirection
                    pendingMoveEnableSmoothScroll = pendingMoveEnableSmoothScroll && smoothScrollEnable
                    return MoveStatus.PendingLater
                }

                if (vm.onlySmoothScrollingOnKey && vm.target?.isFinished == false) {
                    postHandlePendingMove(focusDirection, true)
                    return MoveStatus.PendingLater
                }

                val elapsedAfterScroll = System.currentTimeMillis() - lastHardScrollSetTime
                val delay = vm.minMoveAgainOnKeyTime - elapsedAfterScroll
                if (delay > 0) {
                    postHandlePendingMove(focusDirection, false, delay + 5)
                    return MoveStatus.PendingLater
                }
            } else if (vm.division <= 1) {
                return MoveStatus.WantLeaveMinorDirection
            }

            val focusedTileIndex: Int = findArrayIndexOfView(focusedTile)!!

            var dIndex = if (majorDirection) idir * vm.division    else idir
            var lowIndex = if (majorDirection) 0                   else (focusedTileIndex / vm.division) * vm.division
            var hiIndex = if (majorDirection) vm.array.size - 1    else Math.min(lowIndex + vm.division - 1, vm.array.size - 1)

            var searchIndex = focusedTileIndex + dIndex

            if (majorDirection && idir > 0 && vm.division > 1 && searchIndex !in lowIndex..hiIndex && focusedTileIndex / vm.division != (vm.array.size - 1) / vm.division) {//last unfinished dir
                lowIndex = ((vm.array.size - 1) / vm.division) * vm.division
                hiIndex = Math.min(lowIndex + vm.division - 1, vm.array.size - 1)
                searchIndex = hiIndex
                dIndex = -1
            }

            while (searchIndex in lowIndex..hiIndex) {
                val i = searchIndex
                searchIndex += dIndex

                val v = findTileViewForArrayIndex(i)?.asView
                if (v == null || v.visibility != View.VISIBLE) continue
                if (v.width == 0 || v.height == 0) {
                    //в процессе посторения, подождем пока построится
                    if (v.layoutParams.width != 0 && v.layoutParams.height != 0) {
                        postHandlePendingMove(focusDirection, smoothScrollEnable, 30)
                        return MoveStatus.PendingLater
                    } else
                        continue
                }
                if (!v.isAnyFocusableExist)continue

                nextFocused = (v as? IHardtileContainerView)?.overrideDefaultFocusOnMove(focusedRect, focusDirection, focusedView)?.let {
                    it.v
                } ?: run {
                    findNextFocusFromRect(v, focusedRect, focusDirection, focusedView)
                }

                if (nextFocused == null) {//слишком далекий фокус, пробуем переброс фокуса со всего тайла(обычно всей колонки\столбца)
                    val focusedTileRect = rectOf(focusedTile)
                    if (majorDirection && vm.division > 1) {
                        if (vm.vertical) {
                            focusedTileRect.left = paddingLeft
                            focusedTileRect.right = actualWidth - paddingRight
                        } else {
                            focusedTileRect.top = paddingTop
                            focusedTileRect.bottom = actualHeight - paddingBottom
                        }
                    }
                    nextFocused = (v as? IHardtileContainerView)?.overrideDefaultFocusOnMove(focusedRect, focusDirection, focusedView)?.let {
                        it.v
                    } ?: run {
                        findNextFocusFromRect(v, focusedRect, focusDirection, focusedView)
                    }
                }
                if (nextFocused != null)break
            }
            if (nextFocused == null && majorDirection) { //try scroll
                val overscrollMode = if (idir < 0) ScrollMode.InBoundsLeftOrTop else ScrollMode.InBoundsRightOrBottom
                var searchIndex = focusedTileIndex + dIndex
                while (searchIndex in lowIndex..hiIndex) {
                    val i = searchIndex
                    searchIndex += dIndex

                    if (getDirectionToScrollInBoundsView(i) == idir) {
                        if (vm.scrollToTileEx(i, overscrollMode, isCanSmoothScrolling && smoothScrollEnable))
                            return MoveStatus.OnlyScrolled
                    }
                }

                /*
                //когда заскроллились до нефокусируемого элемента да так что фокусируемый стал невидим и хотим вернутся назад и заскорллить фокусируемый
                if (getDirectionToScrollInBoundsView(focusedTileIndex) == idir) {
                    if (scrollToTileEx(focusedTileIndex, ScrollMode.InBounds, isCanSmoothScrolling && smoothScrollEnable))
                        return MoveStatus.OnlyScrolled
                }
                 */
            }
            if (nextFocused == null)
                return if (majorDirection) MoveStatus.WantLeaveMajorDirection else MoveStatus.WantLeaveMinorDirection
        }

        val nextFocusedIndex = findArrayIndexOfView(nextFocused)!!
        vm.scrollToTileEx(nextFocusedIndex, scrollMode, isCanSmoothScrolling && smoothScrollEnable)

        if (nextFocused.hasFocus()) { //wrong android state!!! hackfix
            nextFocused.findFocusedView()?.clearFocus()
            nextFocused.clearFocus()
        }
        return if (nextFocused.requestFocus()) MoveStatus.FocusMoved else MoveStatus.Invalid
    }

    var dispatchKeyEventLeaveFocusCallback: ((focusDirection: Int, view: TilesContainerView)->Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (super.dispatchKeyEvent(event)) return true
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val focusDirection = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            else -> return false
        }

        val status = move(focusDirection, true)

        if (status == MoveStatus.NoChanges)return false

        if (status == MoveStatus.WantLeaveMajorDirection || status == MoveStatus.WantLeaveMinorDirection) {
            return dispatchKeyEventLeaveFocusCallback?.invoke(focusDirection, this) ?: false
        }

        return true
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (visibility != View.VISIBLE)return false
        if (width == 0 || height == 0) {
            return super.requestFocus(direction, previouslyFocusedRect)
        }
        val r = previouslyFocusedRect ?: Rect().apply {
            getDrawingRect(this)
            when (direction) {
                View.FOCUS_LEFT -> right = left
                View.FOCUS_RIGHT -> left = right
                View.FOCUS_UP -> top = bottom
                View.FOCUS_DOWN -> bottom = top
            }
        }
        findNextFocusFromRect(this, r, direction, null)?.let {
            return it.requestFocus()
        }
        return false
    }

    var requestChildFocusCallback: ((child: View, focused: View, container: TilesContainerView)->Unit)? = null

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (inRebuild || inClear)return
        child?.let {
            val index = findArrayIndexOfView(it)!!
            vm.scrollToTile(index, if (vm.centerFocused) ScrollMode.Center else ScrollMode.InBounds, true, ScrollOverrideStrategy.WhenNewPos, false)
            onTileFocused(index)
            requestChildFocusCallback?.invoke(it, focused!!, this)
        }
    }

    override fun focusToTileEx(index: Int, scrollMode: ScrollMode, smoothScrollEnabled: Boolean, overrideStrategy: ScrollOverrideStrategy, innerContainerCallback: ((container: IHardtileContainerView)->Boolean)?): Boolean {
        vm.scrollToTileEx(index, scrollMode, smoothScrollEnabled, overrideStrategy, false)
        val tile = findTileViewForArrayIndex(index) ?: return false
        if (tile is IHardtileContainerView) {
            innerContainerCallback?.let {
                return it.invoke(tile)
            }
        }
        return tile.asView.requestFocus()
    }
}
