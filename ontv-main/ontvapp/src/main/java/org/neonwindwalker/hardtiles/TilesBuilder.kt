package org.neonwindwalker.hardtiles

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.*

abstract class TilesBuilderVM : IHardtileContainerViewModel, IHardtileTraversable {
    //view params
    var vertical: Boolean = false
    var fixedSize: Int = ViewGroup.LayoutParams.MATCH_PARENT
    var division: Int = 1
    var leftPadding: Int = 0
    var topPadding: Int = 0
    var rightPadding: Int = 0
    var bottomPadding: Int = 0
    var tileSpacing: Int = 0

    enum class Gravity {
        LeftOrTop,
        Center,
        RightOrBottom
    }
    var gravity: Gravity = Gravity.LeftOrTop

    override var array: Array<IHardtileViewModel> = emptyArray()
        set(newArr) {
            if (field.contentEquals(newArr)) {
                field = newArr
                return
            }

            field.forEach {
                if (!newArr.contains(it)) {
                    it.parentVM = null
                }
            }

            field = newArr

            array.forEach {
                if (Constants.DEBUG) {
                    if (it.parentVM != null && it.parentVM !== this)
                        Log.e("Hardtiles", "remove vm from previous container before add to new, vm type ${it.javaClass.simpleName}")
                    if (array.indexOf(it) != array.lastIndexOf(it))
                        Log.e("Hardtiles", "duplication in vm array, type ${it.javaClass.simpleName}")
                }
                it.parentVM = this
            }

            tilesBuilderView?.rebuild(IHardtileContainerView.RebuildReason.TilesArrayChanged)
        }
    var savedScrollPosX: Int = 0
    var savedScrollPosY: Int = 0

    abstract val tilesBuilderView: TilesBuilderView?

    //settings
    var buildAllViews = false //создаем все вьюхти, например хорошо для кнопок или для основной вьюхи карточки контента
    var maxCachedViews = 0

    fun triggerRebuildView(reason: IHardtileContainerView.RebuildReason) {
        tilesBuilderView?.rebuild(reason)
    }

    fun triggerPostRebuildView(reason: IHardtileContainerView.RebuildReason) {
        tilesBuilderView?.postRebuild(reason)
    }

    override fun viewWidth(w: Int, h: Int): Int { return if (vertical && fixedSize > 0) fixedSize else w  }
    override fun viewHeight(w: Int, h: Int): Int { return if (!vertical && fixedSize > 0) fixedSize else h  }

    fun cloneArray() : ArrayList<IHardtileViewModel> { return ArrayList<IHardtileViewModel>(array.toList()) }

    fun insertRowSafe(index: Int, row: IHardtileViewModel?) {
        row ?: return
        val existIndex = array.indexOf(row)
        if (index == existIndex)return
        array = cloneArray().apply {
            if (existIndex >= 0) {
                removeAt(existIndex)
            }
            add(index, row)
        }.toTypedArray()
    }

    fun removeRow(row: IHardtileViewModel?) {
        row ?: return
        val existIndex = array.indexOf(row)
        if (existIndex >= 0) {
            array = cloneArray().apply { removeAt(existIndex) }.toTypedArray()
        }
    }

    override fun traverse(cb: (v: Any) -> IHardtileTraversable.ReturnValue): IHardtileTraversable.ReturnValue {
        val myr = cb(this)
        if (myr !is IHardtileTraversable.Continue) return myr
        for (it in array) {
            val r = cb(it)
            if (r is IHardtileTraversable.BreakTraverseBranch)continue
            if (r is IHardtileTraversable.FinishWithResult)return r
            (it as? IHardtileTraversable)?.traverse(cb)?.let {
                if (it is IHardtileTraversable.FinishWithResult)
                    return it
            }
        }
        return IHardtileTraversable.Continue
    }
}

abstract class TilesBuilderView(context: Context, val initialWidth: Int, val initialHeight: Int, override val vm: TilesBuilderVM) : FrameLayout(context), IHardtileContainerView, IHardtileTraversable {
    var crdArray = IntArray(1)
        private set
    var buildLowIndex = -1
        private set
    var buildHiIndex = -1
        private set
    var isDestroyed = false
        private set

    val actualWidth: Int
        get() {
            layoutParams?.width?.let { if (it >= 0)return it }
            if (width > 0) return width
            return initialWidth
        }
    val actualHeight: Int
        get() {
            layoutParams?.height?.let { if (it >= 0)return it }
            if (height > 0) return height
            return initialHeight
        }

    val actualSize: Int
        get() = if (vm.vertical) actualHeight else actualWidth

    init {
        clipToPadding = false
    }

    //нельзя заребилдиться пока обьект не создан полностью
    open fun init() {
        rebuild(IHardtileContainerView.RebuildReason.Initial)
    }

    override fun free() {
        isDestroyed = true
        clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        postRebuild(IHardtileContainerView.RebuildReason.Resize)
    }

    var inClear = false
        private set
    protected open fun clear() {
        buildLowIndex = -1
        buildHiIndex = -1
        inClear = true
        while(childCount > 0) {
            removeAndDestroy(getChildAt(0))
        }
        inClear = false
    }

    private fun removeAndDestroy(v: View) {
        removeView(v)
        (v as IHardtileView).freeAndVMNotify()
    }

    override fun traverse(cb: (v: Any) -> IHardtileTraversable.ReturnValue): IHardtileTraversable.ReturnValue {
        val myr = cb(this)
        if (myr !is IHardtileTraversable.Continue) return myr
        for (i in 0 until childCount) {
            val v = getIChildAt(i)
            val r = cb(v)
            if (r is IHardtileTraversable.BreakTraverseBranch)continue
            if (r is IHardtileTraversable.FinishWithResult)return r
            (v as? IHardtileTraversable)?.traverse(cb)?.let {
                if (it is IHardtileTraversable.FinishWithResult)
                    return it
            }
        }
        return IHardtileTraversable.Continue
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, y)
        vm.savedScrollPosX = x
        vm.savedScrollPosY = y
        for (i in 0 until childCount) {
            getIChildAt(i).onScrollChanged(this)
        }
    }

    val scrollV: Int
        get() = if (vm.vertical) scrollY else scrollX

    fun scrollTo(v: Int) {
        if (vm.vertical)
            scrollTo(0, v)
        else
            scrollTo(v, 0)
    }

    abstract fun calcTargetScroll(): Pair<Int,Int>?

    fun tileLowCrd(index: Int): Int {
        return crdArray[index / vm.division]
    }

    fun tileHiCrd(index: Int): Int {
        return crdArray[index / vm.division + 1] - if (index / vm.division + 1 != crdArray.lastIndex) vm.tileSpacing else 0
    }

    val allTilesTotalSize: Int
        get() = crdArray.last()

    open val minAvailableScroll : Int
        get() = 0

    open val maxAvailableScroll : Int
        get() = Math.max(0, allTilesTotalSize - if (vm.vertical) actualHeight - paddingBottom - paddingTop else actualWidth - paddingRight - paddingLeft)

    private fun setTileLayoutParams(lp: LayoutParams, i: Int): Boolean  {
        val widthForTiles = widthForTiles
        val heightForTiles = heightForTiles
        var w = vm.array[i].viewWidth(widthForTiles, heightForTiles)
        var h = vm.array[i].viewHeight(widthForTiles, heightForTiles)
        if (w == ViewGroup.LayoutParams.MATCH_PARENT && (vm.division != 1 || !vm.vertical)) {
            w = widthForTiles
        }
        if (h == ViewGroup.LayoutParams.MATCH_PARENT && (vm.division != 1 || vm.vertical)) {
            h = heightForTiles
        }
        val x: Int
        val y: Int
        if (vm.vertical) {
            y = crdArray[i / vm.division]
            val start = (i % vm.division) * widthForTiles
            x = when(vm.gravity) {
                TilesBuilderVM.Gravity.LeftOrTop -> start
                TilesBuilderVM.Gravity.Center -> start + (widthForTiles - w) / 2
                TilesBuilderVM.Gravity.RightOrBottom -> start + widthForTiles - w
            }
        } else {
            x = crdArray[i / vm.division]
            val start = (i % vm.division) * heightForTiles
            y = when(vm.gravity) {
                TilesBuilderVM.Gravity.LeftOrTop -> start
                TilesBuilderVM.Gravity.Center -> start + (heightForTiles - h) / 2
                TilesBuilderVM.Gravity.RightOrBottom -> start + heightForTiles - h
            }
        }
        if (lp.leftMargin != x || lp.topMargin != y || lp.width != w || lp.height != h) {
            lp.leftMargin = x
            lp.topMargin = y
            lp.width = w
            lp.height = h
            return true
        }
        return false
    }

    fun getIChildAt(i: Int): IHardtileView { return getChildAt(i) as IHardtileView }

    override fun findArrayIndexOfView(v: View?): Int? {
        val tile = childContainsView(v) as? IHardtileView ?: return null
        val ind = vm.array.indexOf(tile.vm)
        return if (ind >= 0)ind else null
    }

    override fun findTileViewForArrayIndex(index: Int): IHardtileView? {
        return findTileViewForVM(vm.array.getOrNull(index))
    }

    override fun findTileViewForVM(element: IHardtileViewModel?): IHardtileView? {
        val element = element ?: return null
        for (i in 0 until childCount) {
            val v = getIChildAt(i)
            if (v.vm === element)
                return v
        }
        return null
    }

    fun findFocusedIndex(): Int? {
        return findArrayIndexOfView(findFocus())
    }

    private val widthForTiles: Int
        get() = (actualWidth - paddingLeft - paddingRight) / if (vm.vertical) vm.division else 1

    private val heightForTiles: Int
        get() = (actualHeight - paddingTop - paddingBottom) / if (!vm.vertical) vm.division else 1

    private fun buildTile(index: Int): IHardtileView {
        val lp = LayoutParams(0, 0)
        setTileLayoutParams(lp, index)
        val v = vm.array[index].buildView(context, lp.width, lp.height)
        addView(v.asView, lp)
        return v
    }

    private var postRebuildHandler: Handler? = null

    override fun postRebuild(reason: IHardtileContainerView.RebuildReason) {
        if (postRebuildHandler != null)return
        postRebuildHandler = Handler()
        postRebuildHandler?.post {
            postRebuildHandler = null
            rebuild(reason)
        }
    }

    var inRebuild = false
        private set

    abstract fun recalcScroll()

    fun clearEx() {
        val focusRect = findFocusedView()?.let { rectOf(it) } ?: null
        clear()
        focusRect?.let {
            checkAfterFocusLostInRebuild(it)
        }
    }
    override fun rebuild(reason: IHardtileContainerView.RebuildReason) {
        rebuild(reason, false)
    }

    private fun rebuild(inReason: IHardtileContainerView.RebuildReason, singleRecursiveCall: Boolean) {
        if (isDestroyed) return
        if (inRebuild && !singleRecursiveCall) {
            postRebuild(inReason)
            return
        }

        val reason = if (childCount == 0)IHardtileContainerView.RebuildReason.Initial else inReason

        postRebuildHandler?.removeCallbacksAndMessages(null)
        postRebuildHandler = null
        inRebuild = true

        if (paddingLeft != vm.leftPadding || paddingTop != vm.topPadding || paddingRight !=  vm.rightPadding || paddingBottom != vm.bottomPadding) {
            setPadding(vm.leftPadding, vm.topPadding, vm.rightPadding, vm.bottomPadding)
        }

        val widthForTiles = widthForTiles
        val heightForTiles = heightForTiles

        if (vm.array.isEmpty() || actualWidth <= 0 || actualHeight <= 0 || widthForTiles <= 0 || heightForTiles <= 0){
            inRebuild = false
            clearEx()
            return
        }

        val prevAllTilesTotalSize = allTilesTotalSize
        var crd = 0
        val divisionsCount = (vm.array.size + vm.division - 1) / vm.division
        if (crdArray.size != divisionsCount + 1) {
            crdArray = IntArray(divisionsCount + 1)
        }
        for (div in 0 until divisionsCount) {
            crdArray[div] = crd
            var S = 0
            val divisionSize = Math.min(vm.array.size - vm.division * div, vm.division)
            for (k in 0 until divisionSize) {
                val index = k + div * vm.division
                val cellVm = vm.array[index]
                var s = if (vm.vertical) cellVm.viewHeight(widthForTiles, heightForTiles) else cellVm.viewWidth(widthForTiles, heightForTiles)
                if (s == ViewGroup.LayoutParams.MATCH_PARENT) {
                    s = if (vm.vertical) heightForTiles else widthForTiles
                } else if (s == ViewGroup.LayoutParams.WRAP_CONTENT) {
                    val existView = findTileViewForArrayIndex(index)
                    val v = (existView ?: buildTile(index)).asView
                    if (vm.vertical) {
                        s = v.height
                        if (s <= 0 || existView == null) {
                            if (existView == null) {
                                v.requestLayout()
                            }
                            v.measure(MeasureSpec.makeMeasureSpec(widthForTiles, MeasureSpec.EXACTLY),
                                    MeasureSpec.makeMeasureSpec(heightForTiles, MeasureSpec.UNSPECIFIED))
                            s = v.measuredHeight
                        }
                    } else {
                        s = v.width
                        if (s <= 0 || existView == null) {
                            if (existView == null) {
                                v.requestLayout()
                            }
                            v.measure(MeasureSpec.makeMeasureSpec(widthForTiles, MeasureSpec.UNSPECIFIED),
                                    MeasureSpec.makeMeasureSpec(heightForTiles, MeasureSpec.EXACTLY))
                            s = v.measuredWidth
                        }
                    }
                }
                S = Math.max(S, s)
            }
            crd += S
            if (div + 1 < divisionsCount) { //non last
                crd += vm.tileSpacing
            }
        }
        crdArray[divisionsCount] = crd

        calcScrollInRebuild(crdArray, actualSize, reason, scrollV, prevAllTilesTotalSize)?.let { scroll->
            scrollTo(scroll)
        } ?: run {
            recalcScroll()
        }

        var minOfScrollsX = scrollX
        var minOfScrollsY = scrollY
        var maxOfScrollsX = scrollX
        var maxOfScrollsY = scrollY
        calcTargetScroll()?.let { xy->
            if (minOfScrollsX > xy.first)
                minOfScrollsX = xy.first
            if (maxOfScrollsX < xy.first)
                maxOfScrollsX = xy.first
            if (minOfScrollsY > xy.second)
                minOfScrollsY = xy.second
            if (maxOfScrollsY < xy.second)
                maxOfScrollsY = xy.second
        }

        val lowCoord = if (vm.vertical) minOfScrollsY - paddingTop else minOfScrollsX - paddingLeft
        val hiCoord = if (vm.vertical) maxOfScrollsY - paddingTop + actualHeight else maxOfScrollsX - paddingLeft + actualWidth
        buildLowIndex = -1
        buildHiIndex = -1
        for (div in 0 until divisionsCount) {
            val divisionSize = Math.min(vm.array.size - vm.division * div, vm.division)
            val lastIndex = divisionSize - 1 + div * vm.division
            if (buildLowIndex == -1 && crdArray[div + 1] >= lowCoord) {
                buildLowIndex = lastIndex
            }
            if (crdArray[div] <= hiCoord) {
                buildHiIndex = lastIndex
            }
        }

        if (buildLowIndex < 0 || buildLowIndex > buildHiIndex) {
            clearEx()
            scrollTo(minAvailableScroll)
            inRebuild = false
            return
        }

        if (scrollV < minAvailableScroll) {
            scrollTo(minAvailableScroll)
            if (!singleRecursiveCall) {
                rebuild(reason,true)
            }
            inRebuild = false
            return
        }

        if (scrollV > maxAvailableScroll) {
            scrollTo(maxAvailableScroll)
            if (!singleRecursiveCall) {
                rebuild(reason, true)
            }
            inRebuild = false
            return
        }

        if (vm.buildAllViews) {
            buildLowIndex = 0
            buildHiIndex = vm.array.size - 1
        } else {
            while (buildLowIndex > 0) {
                buildLowIndex -= 1
                if (vm.array[buildLowIndex].isAllwaysFocusable) {
                    buildLowIndex = (buildLowIndex / vm.division) * vm.division
                    break
                }
            }

            while (buildHiIndex + 1 < vm.array.size) {
                buildHiIndex += 1
                if (vm.array[buildHiIndex].isAllwaysFocusable) {
                    buildHiIndex = Math.min(((buildHiIndex + vm.division - 1) / vm.division) * vm.division, vm.array.size - 1)
                    break
                }
            }
        }

        var removeFocusedView: View? = null
        var removeFocusedViewRect: Rect? = null
        val dellist = ArrayList<View>()
        val canDelList = if (childCount > vm.maxCachedViews && !vm.buildAllViews) TreeMap<Int, View>() else null
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val arrIndex = findArrayIndexOfView(v)
            if (arrIndex == null) {
                if (v.hasFocus()){
                    v.findFocusedView()?.let { vf->
                        removeFocusedView = v
                        removeFocusedViewRect = rectOf(vf)
                    } ?: run {
                        dellist.add(v)
                    }
                } else {
                    dellist.add(v)
                }
            } else if (canDelList != null && !v.hasFocus()) {
                if (arrIndex !in buildLowIndex..buildHiIndex &&
                        (if (vm.vertical) v.layoutParams.height else v.layoutParams.width) != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    val d = if (arrIndex < buildLowIndex) arrIndex - buildLowIndex else arrIndex - buildHiIndex
                    canDelList.put(d, v)
                }
            }
        }
        dellist.forEach {
            removeAndDestroy(it)
        }

        canDelList?.let { list->
            while (list.isNotEmpty() && list.size > vm.maxCachedViews) {
                val entry = if (Math.abs(list.firstKey()) > Math.abs(list.lastKey()))list.firstEntry()!! else list.lastEntry()!!
                removeAndDestroy(entry.value)
                list.remove(entry.key)
            }
        }

        for (i in buildLowIndex..buildHiIndex) {
            if (findTileViewForArrayIndex(i) == null) {
                buildTile(i)
            }
        }

        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val index = findArrayIndexOfView(v)
            if (index == null) {
                Log.e("Hardtiles", "invalid view ${v.javaClass.name} without vm, please check vm setter")
                continue
            }
            if (setTileLayoutParams(v.layoutParams as LayoutParams, index)) {
                v.requestLayout()
            }
        }

        removeFocusedView?.let {
            checkBeforeFocusLostInRebuild(it)
            removeAndDestroy(it)
        }

        inRebuild = false

        removeFocusedViewRect?.let {
            checkAfterFocusLostInRebuild(it)
        }
    }

    fun actualRectOfChildTile(v: View): Rect {
        val lp = v.layoutParams as LayoutParams
        val r = Rect(lp.leftMargin, lp.topMargin, lp.leftMargin, lp.topMargin)
        r.right += when (lp.width) {
            ViewGroup.LayoutParams.MATCH_PARENT -> widthForTiles
            ViewGroup.LayoutParams.WRAP_CONTENT -> v.measuredWidth
            else -> lp.width
        }
        r.bottom += when (lp.height) {
            ViewGroup.LayoutParams.MATCH_PARENT -> heightForTiles
            ViewGroup.LayoutParams.WRAP_CONTENT -> v.measuredHeight
            else -> lp.height
        }
        return r
    }

    fun prebuildAny(): Boolean {
        if (width <= 0 || height <= 0 || buildLowIndex < 0)return false
        if (widthForTiles <= 0 || heightForTiles <= 0)return false

        var index: Int? = null
        for (i in 1..Math.min(vm.maxCachedViews, vm.array.size)) {
            val ind1 = buildLowIndex - i
            val ind2 = buildHiIndex + i
            val ind1Valid = ind1 in vm.array.indices
            val ind2Valid = ind2 in vm.array.indices
            if (ind1Valid && findTileViewForArrayIndex(ind1) == null) {
                index = ind1
                break
            }
            if (ind2Valid && findTileViewForArrayIndex(ind2) == null) {
                index = ind2
                break
            }
            if (!ind1Valid && !ind2Valid)return false
        }
        if (index == null)return false

        buildTile(index)
        return true
    }
}
