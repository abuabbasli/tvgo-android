package org.neonwindwalker.hardtiles

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object FocusFinder {
    fun findNextFocusFromRect(root: View, focusedRect: Rect, direction: Int, ignoreFocusView: View?, oneDimensionMode: Boolean = false): View? {
        val fr = Rect(focusedRect)
        fr.offset(root.scrollX - root.left, root.scrollY - root.top)

        var bestView: View? = null
        var bestViewD2: Long = Long.MAX_VALUE

        traverseAllFocusablesEx(root, root) { view, rect ->
            if (view === ignoreFocusView)
                return@traverseAllFocusablesEx
            val dx = dist(fr.left, fr.right, rect.left, rect.right)
            val dy = dist(fr.top, fr.bottom, rect.top, rect.bottom)

            val moveMain = when (direction) {
                View.FOCUS_LEFT -> -dx
                View.FOCUS_RIGHT -> dx
                View.FOCUS_UP -> -dy
                View.FOCUS_DOWN -> dy
                else -> -1
            }.toLong()

            if (moveMain < 0) return@traverseAllFocusablesEx

            val moveTan = when (direction) {
                View.FOCUS_UP, View.FOCUS_DOWN -> abs(dx)
                View.FOCUS_LEFT, View.FOCUS_RIGHT -> abs(dy)
                else -> 0
            }.toLong()

            if (oneDimensionMode) {
                if (moveTan > 0)return@traverseAllFocusablesEx
            }

            val clcx = closestToBCenterPoint(fr.left, fr.right, rect.left, rect.right, dx)
            val clcy = closestToBCenterPoint(fr.top, fr.bottom, rect.top, rect.bottom, dy)
            val dcx = rect.centerX() - clcx
            val dcy = rect.centerY() - clcy

            val toBCenterMain = when (direction) {
                View.FOCUS_LEFT -> -dcx
                View.FOCUS_RIGHT -> dcx
                View.FOCUS_UP -> -dcy
                View.FOCUS_DOWN -> dcy
                else -> -1
            }.toLong()

            if (toBCenterMain <= 0) return@traverseAllFocusablesEx

            val toBCenterTan = when (direction) {
                View.FOCUS_UP, View.FOCUS_DOWN -> abs(dcx)
                View.FOCUS_LEFT, View.FOCUS_RIGHT -> abs(dcy)
                else -> 0
            }.toLong()


            val fromADx = clcx - dx - fr.centerX()
            val fromADy = clcy - dy - fr.centerY()

            val totalD2 = (moveMain * moveMain + moveTan * moveTan * 4) * 10000 +
                    (toBCenterMain * toBCenterMain + toBCenterTan * toBCenterTan * 4) * 100 +
                    fromADx * fromADx + fromADy * fromADy * 4

            if (totalD2 < bestViewD2) {
                bestViewD2 = totalD2
                bestView = view
            }
        }
        return bestView
    }

    private fun dist(a: Int, A: Int, b: Int, B: Int): Int {
        if (b >= A) return b - A
        if (B <= a) return B - a
        return 0
    }

    private fun closestToBCenterPoint(a: Int, A: Int, b: Int, B: Int, d: Int): Int {
        val c = max(a + d, b)
        val C = min(A + d, B)
        val o = (b + B) / 2
        return if (o >= c) {
            if (o <= C)
                o
            else
                C
        } else {
            c
        }
    }

    private fun traverseAllFocusables(root: ViewGroup, view: View, callback: (v: View, r: Rect)->Unit) {
        if (view.isFocusable && view.visibility == View.VISIBLE && view.width != 0 && view.height != 0) {
            callback(view, root.rectOf(view))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.visibility == View.VISIBLE && child.width != 0 && child.height != 0) {
                    traverseAllFocusables(root, child, callback)
                }
            }
        }
    }

    private fun traverseAllFocusablesEx(root: View, view: View, callback: (v: View, r: Rect)->Unit) {
        if (root is ViewGroup) {
            traverseAllFocusables(root, view, callback)
        } else if (view.isFocusable && view.visibility == View.VISIBLE && view.width != 0 && view.height != 0) {
            val r = Rect()
            view.getDrawingRect(r)
            callback(view, r)
        }
    }

    fun findFocusInFromRect(root: View, shadowRect: Rect, shadowRectInRootCoords: Boolean): View? {
        val fr = Rect(shadowRect)
        if (!shadowRectInRootCoords) {
            fr.offset(root.scrollX - root.left, root.scrollY - root.top)
        }

        var bestView: View? = null
        var bestViewCrossSquare = -1

        traverseAllFocusablesEx(root, root) { view, rect ->
            if (!fr.intersect(rect))return@traverseAllFocusablesEx
            val s = (Math.min(fr.right, rect.right) - Math.max(fr.left, rect.left)) *
                    (Math.min(fr.bottom, rect.bottom) - Math.max(fr.top, rect.top))
            if (bestViewCrossSquare < s) {
                bestViewCrossSquare = s
                bestView = view
            }
        }
        return bestView
    }

    fun findFixedNextFocus(view: View, direction: Int, inView: View?): View? {
        val id = when (direction) {
            View.FOCUS_LEFT -> view.nextFocusLeftId
            View.FOCUS_RIGHT -> view.nextFocusRightId
            View.FOCUS_UP -> view.nextFocusUpId
            View.FOCUS_DOWN -> view.nextFocusDownId
            else -> -1
        }
        if (id <= 0)return null
        var par = view.parent
        while (par != null) {
            (par as? ViewGroup)?.findViewById<View>(id)?.let {
                if (isCanDirectFocus(it))
                    return it
            }
            if (par === inView)return null
            par = par.parent
        }
        return null
    }

    //можем ли фокусить именно эту вьюху?
    fun isCanDirectFocus(v: View) : Boolean {
        if (!v.isFocusable || v.visibility != View.VISIBLE || v.width == 0 || v.height == 0)
            return false
        var par: ViewParent? = v.parent
        while (par != null) {
            (par as? View)?.let {
                if (it.visibility != View.VISIBLE || it.width == 0 || it.height == 0)
                    return false
            }
            par = par.parent
        }
        return true
    }

    fun findNextFocus(root: ViewGroup, focusedChild: View, direction: Int, ignoreFocusView: View?, oneDimensionMode: Boolean = false): View? {
        return findFixedNextFocus(focusedChild, direction, ignoreFocusView) ?: findNextFocusFromRect(root, root.rectOf(focusedChild), direction, ignoreFocusView, oneDimensionMode)
    }
}