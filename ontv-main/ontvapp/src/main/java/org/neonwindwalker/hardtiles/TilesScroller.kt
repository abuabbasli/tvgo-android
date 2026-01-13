package org.neonwindwalker.hardtiles

import android.content.Context
import android.os.Handler
import android.view.ViewParent
import android.view.animation.AccelerateDecelerateInterpolator
import java.lang.ref.WeakReference

abstract class TilesScrollerVM : TilesBuilderVM() {
    companion object {
        var defaultSmoothScrollAnimationTickTime = 1000L / 60
        var defaultSmoothScrollMaxAnimationTickTimeMultiplier = 5
        var defaultSmoothScrollDuration = 360
        var defaultSmoothScrollShiftDuration = 360
    }

    var smoothScrollAnimationTickTime = defaultSmoothScrollAnimationTickTime
    var smoothScrollMaxAnimationTickTimeMultiplier = defaultSmoothScrollMaxAnimationTickTimeMultiplier
    var smoothScrollDuration = defaultSmoothScrollDuration
    var smoothScrollShiftDuration = defaultSmoothScrollShiftDuration
    var maxOverscrollAllowed = 0

    var isUnfinished: Boolean = false
    var unfinishedOverscroll: Int = 100

    var target: ScrollTarget? = null

    abstract val tilesScrollerView: TilesScrollerView?

    override fun scrollToTileEx(index: Int, scrollMode: ScrollMode, smoothScrollEnabled: Boolean, overrideStrategy: ScrollOverrideStrategy, keepFocusedVisible: Boolean): Boolean {
        val ret = scrollToTile(index, scrollMode, smoothScrollEnabled, overrideStrategy, keepFocusedVisible)
        if (ret && target?.isFinished == true)
            tilesBuilderView?.rebuild(IHardtileContainerView.RebuildReason.ScrollOrOtherAnimations)
        return ret
    }

    fun scrollToTile(index: Int, scrollMode: ScrollMode, smoothScrollEnabled: Boolean, overrideStrategy: ScrollOverrideStrategy, keepFocused: Boolean): Boolean {
        if (index < 0 || index >= array.size)return false
        val newTarget = ScrollTarget(WeakReference(array.get(index)), scrollMode, keepFocused, if (smoothScrollEnabled)smoothScrollDuration else 0)

        val prevTarget = target
        if (prevTarget != null && prevTarget.vm.get() == newTarget.vm.get()) {
            val sameScroll = when (newTarget.scrollMode) {
                ScrollMode.InBounds -> true
                ScrollMode.InBoundsLeftOrTop -> {
                    prevTarget.scrollMode == ScrollMode.InBoundsLeftOrTop ||
                            prevTarget.scrollMode == ScrollMode.ToLeftOrTop ||
                            prevTarget.scrollMode == ScrollMode.Center
                }
                ScrollMode.InBoundsRightOrBottom -> {
                    prevTarget.scrollMode == ScrollMode.InBoundsRightOrBottom ||
                            prevTarget.scrollMode == ScrollMode.ToRightOrBottom ||
                            prevTarget.scrollMode == ScrollMode.Center
                }
                else -> prevTarget.scrollMode == newTarget.scrollMode
            }
            if (!overrideStrategy.isOverride(this, prevTarget, newTarget, !sameScroll))
                return false
        }

        target = newTarget
        target?.lastUpdateTime = System.currentTimeMillis()

        return tilesScrollerView?.updateScroll() ?: false
    }
}

abstract class TilesScrollerView(context: Context, width: Int, height: Int, override val vm: TilesScrollerVM) : TilesBuilderView(context, width, height, vm) {
    protected val scrollAnimationInterpolator = AccelerateDecelerateInterpolator()
    var lastHardScrollSetTime: Long = 0
        private set

    override fun init() {
        super.scrollTo(vm.savedScrollPosX, vm.savedScrollPosY)
        targetScrollCalcClamped()?.let {
            scrollToV(it)
        }
        super.init()
    }

    override fun calcTargetScroll(): Pair<Int, Int>? {
        val to = targetScrollCalcClamped() ?: return null
        return if (vm.vertical)
            Pair<Int, Int>(0, to)
        else
            Pair<Int, Int>(to, 0)
    }

    override fun clear() {
        super.clear()
        animTickHandler?.removeCallbacksAndMessages(null)
        animTickHandler = null
        scrollToV(scrollShiftV)
    }

    var scrollShiftVT: Int = 0
        set(v) {
            field = v
            scrollToV(scrollV)
        }

    //should use only negative values
    //should rebuild after apply
    var scrollShiftV: Int = 0
        set(v) {
            val old = field
            field = v
            scrollAnimationUpdate()
            setNoAnimScroll(v - old)
        }

    fun scrollToV(scr: Int) {
        if (vm.vertical)
            super.scrollTo(scrollShiftVT, scr)
        else
            super.scrollTo(scr, scrollShiftVT)
    }

    fun tileScrollLimits(index: Int?): IntRange? {
        val index = index ?: return null
        if (index < 0 || index / vm.division + 1 >= crdArray.size) return null
        val hiScroll = tileLowCrd(index)
        val lowScroll = tileHiCrd(index) + if (vm.vertical) paddingBottom + paddingTop - actualHeight else paddingRight + paddingLeft - actualWidth
        return lowScroll..hiScroll
    }

    fun targetTileScrollLimits(): IntRange? {
        val target = vm.target ?: return null
        val itemVm = target.vm.get() ?: return null
        val index = vm.array.indexOf(itemVm)
        return tileScrollLimits(index)
    }

    fun targetScrollCalcClamped(): Int? {
        val target = vm.target ?: return null
        val itemVm = target.vm.get() ?: return null
        val range = tileScrollLimits(vm.array.indexOf(itemVm)) ?: return null
        if (range.isEmpty()) return null

        val scrollBeforeShift = scrollV - scrollShiftV
        var to = if (range.contains(scrollBeforeShift) && target.scrollMode in arrayListOf(ScrollMode.InBounds, ScrollMode.InBoundsLeftOrTop, ScrollMode.InBoundsRightOrBottom)) {
            scrollBeforeShift
        } else {
            when(target.scrollMode) {
                ScrollMode.Center -> (range.first + range.last) / 2
                ScrollMode.InBounds -> if (Math.abs(scrollBeforeShift - range.last) < Math.abs(scrollBeforeShift - range.first)) range.last else range.first
                ScrollMode.InBoundsLeftOrTop, ScrollMode.ToLeftOrTop -> range.last
                ScrollMode.InBoundsRightOrBottom, ScrollMode.ToRightOrBottom -> range.first
            }
        }

        if (to < 0)
            to = 0

        to = Math.min(to, maxAvailableScrollWithoutShift)


        to += scrollShiftV

        if (target.keepFocusedVisible) {
            tileScrollLimits(findFocusedIndex())?.let { range->
                if (!range.contains(to)) {
                    to = if ((range.first + range.last)/2 > to) range.first else range.last
                }
            }
        }
        return to
    }

    fun updateScroll(): Boolean {
        val t = vm.target ?: return false
        val to = targetScrollCalcClamped() ?: return false

        if (to == scrollV
            /*|| это уже проверяется в targetScrollCalcClamped
                (t.scrollMode in setOf(ScrollMode.InBounds, ScrollMode.InBoundsLeftOrTop, ScrollMode.InBoundsRightOrBottom) &&
                 targetTileScrollLimits()?.contains(scrollV - scrollShiftV) == true)*/) {
            vm.target?.setFinished()
            return false
        }

        if (t.duration > 0) {
            scheduleAnimTick()
        } else {
            scrollToV(to)
            lastHardScrollSetTime = System.currentTimeMillis()
        }

        return true
    }

    override val minAvailableScroll : Int
        get() = if (scrollShiftV < 0) scrollShiftV else 0

    val maxAvailableScrollWithoutShift : Int
        get() = super.maxAvailableScroll + (if (vm.isUnfinished && crdArray.size >= 2)vm.unfinishedOverscroll else 0)  + vm.maxOverscrollAllowed

    override val maxAvailableScroll : Int
        get() = maxAvailableScrollWithoutShift + if (scrollShiftV < 0) 0 else scrollShiftV

    private var animTickHandler: Handler? = null

    fun animTickImpl() {
        val rebuildList = HashSet<IHardtileContainerView>()
        if (animTick(System.currentTimeMillis(), rebuildList)) {
            animTickHandler?.postDelayed({animTickImpl()}, vm.smoothScrollAnimationTickTime)
        } else {
            animTickHandler = null
        }
        rebuildList.sortedBy {
            var parentsCount = 0
            var p: ViewParent? = it.asView.parent
            while (p != null) {
                p = p.parent
                parentsCount += 1
            }
            parentsCount
        }.forEach {
            it.rebuild(IHardtileContainerView.RebuildReason.ScrollOrOtherAnimations)
        }
    }

    override fun scheduleAnimTick(): Boolean {
        if (animTickHandler != null)return true
        if (findParent<IHardtileContainerView>()?.scheduleAnimTick() == true)return true
        animTickHandler = Handler().apply {
            postDelayed( {
                animTickImpl()
            }, vm.smoothScrollAnimationTickTime)
        }
        return true
    }

    override fun animTick(time: Long, rebuildList: MutableSet<IHardtileContainerView>): Boolean {
        val isAnimTarget = vm.target?.isFinished == false
        if (isAnimTarget) {
            rebuildList.add(this)
        }
        var needAgain = isAnimTarget
        for (i in 0 until childCount) {
            val b = (getChildAt(i) as? IHardtileView)?.animTick(time, rebuildList) ?: false
            needAgain = needAgain || b
        }
        return needAgain
    }

    fun scrollAnimationUpdate() {
        val target = vm.target ?: return
        if (target.isFinished)return
        val to = targetScrollCalcClamped() ?: run {
            target.setFinished()
            return
        }

        val prevElapsedTime = target.elapsedTime

        val now = System.currentTimeMillis()
        val dt = now - target.lastUpdateTime
        if (dt == 0L)return
        val tunedDT = Math.min(dt, vm.smoothScrollAnimationTickTime * vm.smoothScrollMaxAnimationTickTimeMultiplier)
        target.elapsedTime += tunedDT
        target.lastUpdateTime = now

        if (target.elapsedTime + vm.smoothScrollAnimationTickTime / 2 >= target.duration || Math.abs(to - scrollV) <= Constants.SCROLL_DELTA) {
            target.setFinished()
            scrollToV(to)
        } else {
            val a0 = scrollAnimationInterpolator.getInterpolation(prevElapsedTime.toFloat() / target.duration)
            val a = scrollAnimationInterpolator.getInterpolation(target.elapsedTime.toFloat() / target.duration)
            val scr = (to * a + (1.0f - a) * (scrollV - to * a0) / (1.0f - a0)).toInt()
            if (Math.abs(to - scr) <= Constants.SCROLL_DELTA) {
                target.setFinished()
                scrollToV(to)
            } else {
                scrollToV(scr)
            }
        }
    }

    override fun recalcScroll() {
        scrollAnimationUpdate()
        setNoAnimScroll(0)
    }

    private fun setNoAnimScroll(dScrollShiftV: Int) {
        if (vm.target?.isFinished == false) return

        var to: Int? = null
        if (vm.target?.isFinished == true) {
            to = targetScrollCalcClamped()
        }

        to = to ?: scrollV + dScrollShiftV
        if (to != scrollV) {
            scrollToV(to)
        }
    }

    fun getDirectionToScrollInBoundsView(index: Int): Int {
        val viewLow = scrollV
        val viewHi = scrollV + actualSize - if (vm.vertical) paddingTop + paddingBottom else paddingLeft + paddingRight
        val low = tileLowCrd(index)
        val hi = tileHiCrd(index)
        if (low in viewLow..viewHi && hi in viewLow..viewHi)
            return 0

       return if (low + hi - (viewLow + viewHi) > 0) 1 else -1
    }

    fun findAnyVisibleTileIndex(align: Int = 0, onlyFocusableTiles: Boolean = true): Int? {
        if (vm.array.isEmpty())return null
        val viewLow = scrollV
        val viewHi = scrollV + actualSize - if (vm.vertical) paddingTop + paddingBottom else paddingLeft + paddingRight
        var bestIndex: Int? = null
        var bestValue: Int = Int.MAX_VALUE

        //full visible
        for (bPartialVisibleAllow in 0..1) {
            for (n in 0..(crdArray.size - 2)) {
                if (bPartialVisibleAllow == 0) {
                    if (crdArray[n] < viewLow || crdArray[n + 1] >= viewHi)continue
                } else {
                    if (crdArray[n] + crdArray[n + 1] - viewLow - viewHi >= viewHi - viewLow)continue
                }

                val dScrollLow = Math.abs(crdArray[n] - viewLow)
                val dScrollHi = Math.abs(crdArray[n + 1] - viewHi)
                val dScrollCenter = Math.abs(crdArray[n] + crdArray[n + 1] - viewLow - viewHi) / 2
                for (div in 0 until vm.division) {
                    val index = n * vm.division + div
                    if (!onlyFocusableTiles || findTileViewForArrayIndex(index)?.asView?.isAnyFocusableExist == true) {
                        val bv = when {
                            align > 0 -> dScrollHi
                            align < 0 -> dScrollLow
                            else -> dScrollCenter
                        }

                        if (bestValue > bv) {
                            bestIndex = index
                            bestValue = bv
                        }
                    }
                }
            }
            bestIndex?.let { return it }
        }
        return null
    }

    fun findAnyVisibleTileView(align: Int = 0, onlyFocusableTiles: Boolean = true): IHardtileView? {
        findAnyVisibleTileIndex(align, onlyFocusableTiles)?.let {
            return findTileViewForArrayIndex(it)
        }
        return null
    }


    override fun focusAny(): Boolean {
        return findAnyVisibleTileIndex(0, true)?.let {
            focusToTileEx(it) {
                it.focusAny()
            }
        } ?: false
    }
}
