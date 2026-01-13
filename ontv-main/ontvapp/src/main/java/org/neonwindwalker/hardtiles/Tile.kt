package org.neonwindwalker.hardtiles

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import java.lang.ref.WeakReference
import java.security.InvalidParameterException

/*
                        HARDTILES
            (Мгновенно работающая библиотека тайлов без отложенных операций, замена библиотеки leanback)

Основыне идеи:
    Вью-модель определяет размеры вьюхи!
    Дерево вьюх строиться сразу по дереву вью-моделей!
    Можно скроллиться до момента создания вьюх имея только вью модель!

Чем лучшще:
    Пока RecyclerView иже с ним начинают задувываться о размерах вьюх и о создании тайлов,
    Hardtiles уже построил дерево вьюх и готов заскроллится к нужной вьюхи, хотя ее системные размеры еще не посчитанны.
    Hardtiles может при возврате на предидущий экран сразу восстановит скроллы и наполнение экрана без передергивания.
    Hardtiles позволяет работать без ожиданий, добавили вью-модель в дереве и сразу заскролили и зафокусились на созданной вьюхе.
    Плавный скролл есть, но плавность используется для создания приятного вида а не для скрытия косяков архитектуры.

Особенности:
    Дерво вью-моделей и дерево вьюх соответствуют друг другу, какие вьюхи будут созданы а какие нет опредеятеся видимостью вьюх и настройками контейнера
    При обновлении данный тайлов нужно находить нужную вью-модель тайла и обновлять данные в этой вью-модели тайла.
    Создание новой вью-модели тайла приведет к созданию новой вьюхи даже если данные одинаковы.
*/

//база вьюх тайлов и вьюхи контенеров
interface IHardtileView {
    //созданная вьюха всегда должна иметь ненулевую ссылку на модель!
    //null допускается только для вьюх в кеше
    val vm: IHardtileViewModel?

    //когда вьюха удаляется из дерева вьюх
    fun free()

    fun onScrollChanged(parent: IHardtileContainerView){}

    fun scheduleAnimTick():Boolean { return false; }
    fun animTick(time: Long, rebuildList: MutableSet<IHardtileContainerView>):Boolean { return false; } //return enable again
}

//база модели контейнеров и тайлов
interface IHardtileViewModel {
    //парент устанавливается при добавлении в массив
    var parentVM: Any?

    //Рассчет размеров тайла или конейнера производится в модели(*почти)
    //containWidth&containHeight - размер вьюхи контенера минус паддинги
    //можем вычислять умные размеры, хотя обычно задаются константы
    //WRAP_CONTENT поддерживатеся, но в таком случае вьюха будет всегда присутствовать в контейнере даже если не видна
    //  и нужно будет вызывать postRebuild в onSizeChanged вьюхи и немного потюнить onMeasure
    //MATCH_PARENT тоже поддерживается
    //в случае гридов размеры containWidth&containHeight указывают на размер для ячейки а не всего доступного места контенера
    fun viewWidth(containWidth: Int, containHeight: Int): Int
    fun viewHeight(containWidth: Int, containHeight: Int): Int

    //нужно строить невидимые тайлы до первого фокусируемово, если вьюха меняет свойство фокусируемости то следует возвращать false
    val isAllwaysFocusable: Boolean
        get() = true

    //содание и удалени вьюхи
    //width = viewWidth(...), height = viewHeight(...)
    //модель не обязана хранить вьюху, но может и в большинстве случаев это удобно
    fun buildView(context: Context, width: Int, height: Int): IHardtileView
    fun onPreFreeView(v: IHardtileView){}
    fun onPostFreeView(v: IHardtileView){}
}

data class SingleValueContainer<T>(val v: T)

//база для модели контенера
interface IHardtileContainerViewModel : IHardtileViewModel {
    //массив дочерних тайлов или контейнеров
    var array: Array<IHardtileViewModel>

    //ссылка на вьюху если она создана
    val tilesContainerView: IHardtileContainerView?

    //Скролл, keepFocusedVisible говорит чтоб мы скроллились максимум настолько чтоб зафокусенная вьюха будет полностью видима
    fun scrollToTileEx(index: Int, scrollMode: ScrollMode = ScrollMode.InBounds, smoothScrollEnabled: Boolean = false, overrideStrategy: ScrollOverrideStrategy = ScrollOverrideStrategy.WhenNewPos, keepFocusedVisible: Boolean = false): Boolean

    fun scrollToTileEx(itemVM: IHardtileViewModel?, scrollMode: ScrollMode = ScrollMode.InBounds, smoothScrollEnabled: Boolean = false, overrideStrategy: ScrollOverrideStrategy = ScrollOverrideStrategy.WhenNewPos, keepFocusedVisible: Boolean = false): Boolean {
        val index = array.indexOf(itemVM)
        if (index < 0 || index >= array.size)return false
        return scrollToTileEx(index, scrollMode, smoothScrollEnabled, overrideStrategy, keepFocusedVisible)
    }

    fun scrollToBegin(keepFocusedVisible: Boolean = true): Boolean {
        return scrollToTileEx(0, ScrollMode.ToLeftOrTop, false, ScrollOverrideStrategy.Allways, keepFocusedVisible)
    }

    fun scrollToEnd(keepFocusedVisible: Boolean = true): Boolean {
        return scrollToTileEx(array.size - 1, ScrollMode.ToRightOrBottom, false, ScrollOverrideStrategy.Allways, keepFocusedVisible)
    }

    //для фокусировки "среднейго" тайла строки невзирая на то место откуда пришел фокус если вернем {null}
    //или для фокусировки первого тайла
    fun overrideDefaultFocusOnMove(focusedRect: Rect, direction: Int, ignoreFocusView: View?): SingleValueContainer<View?>? { return null }

    fun calcScrollInRebuild(crdArray: IntArray, size: Int, reason: IHardtileContainerView.RebuildReason, prevScroll: Int, prevAllTilesTotalSize: Int): Int? { return null }

    fun onTileFocused(index: Int) {}
}

object Constants {
    var DEBUG = false
    var SCROLL_DELTA = 3
}

//как заскролить чтоб вьюха была видна
enum class ScrollMode {
    InBounds,           //заскроллить так чтоб вьюзха была внутри конейнера и его паддингов
    InBoundsLeftOrTop,
    InBoundsRightOrBottom,
    Center,
    ToLeftOrTop,        //с начала
    ToRightOrBottom     //в конец
}

//Элемент который заскроллел(аля зафокусен)
//Живет вечно до следующего скролла
//Нужен чтобы при анимции изменения размера контейнела и\или тайлов изменялся корректно скролл
class ScrollTarget(
    val vm: WeakReference<IHardtileViewModel>,
    val scrollMode: ScrollMode,
    val keepFocusedVisible: Boolean,
    val duration: Int) {
    var lastUpdateTime = 0L
    var elapsedTime = 0L
    val isFinished: Boolean
        get() = elapsedTime >= duration
    fun setFinished() { elapsedTime = duration.toLong() }
}

//как поступать если предидущий скролл на тотже самый элемент еще не был завершен
enum class ScrollOverrideStrategy {
    Allways {
        override fun isOverride(view: IHardtileContainerViewModel, prevTarget: ScrollTarget, newTarget: ScrollTarget, newTargetWantChangeScroll: Boolean): Boolean {
            return true
        }
    },
    WhenNewPos {
        override fun isOverride(view: IHardtileContainerViewModel, prevTarget: ScrollTarget, newTarget: ScrollTarget, newTargetWantChangeScroll: Boolean): Boolean {
            return newTargetWantChangeScroll
        }
    },
    WhenNewPosOrLessDuration {
        override fun isOverride(view: IHardtileContainerViewModel, prevTarget: ScrollTarget, newTarget: ScrollTarget, newTargetWantChangeScroll: Boolean): Boolean {
            return newTargetWantChangeScroll || newTarget.duration < prevTarget.duration
        }
    };

    abstract fun isOverride(view: IHardtileContainerViewModel, prevTarget: ScrollTarget, newTarget: ScrollTarget, newTargetWantChangeScroll: Boolean): Boolean
}

//база для вьюхи контенера
interface IHardtileContainerView : IHardtileView {
    enum class RebuildReason {
        TilesArrayChanged,
        ScrollOrOtherAnimations,
        ExpandCollapseAnimations,
        Resize,
        LoadFinishRefresh,
        Initial
    }

    //фокусим тайл или контейнер, если элменет это контейнер то нужно вызвать first.focusToTileEx для фокусировки тайла
    fun focusToTileEx(index: Int, scrollMode: ScrollMode = ScrollMode.InBounds, smoothScrollEnabled: Boolean = false, overrideStrategy: ScrollOverrideStrategy = ScrollOverrideStrategy.WhenNewPos, innerContainerCallback: ((container: IHardtileContainerView)->Boolean)? = null): Boolean

    //Не используйте requestFocus(), после постоения дерева вьюх нельзя сразу зафокусится методами андроида.
    fun focusAny(): Boolean

    //нужно вызывать если WRAP_CONTENT вьюха поменяла размер или тайл поменял свой размер или изменились параметры контейнера
    //в обычных случаях вызывать не нужно, все автоматически вызывается при смене массива vm.array и при изменении размера самого контейнера
    fun rebuild(reason: RebuildReason)
    fun postRebuild(reason: RebuildReason)

    //индекс соответствуюий тайлу в массиве, можно пихать любую дочерную вьюху в тайле
    fun findArrayIndexOfView(v: View?): Int?

    //если null значит вьюха не создана
    fun findTileViewForArrayIndex(index: Int): IHardtileView?
    fun findTileViewForVM(element: IHardtileViewModel?): IHardtileView?

    fun focusToTileEx(itemVM: IHardtileViewModel?, scrollMode: ScrollMode = ScrollMode.InBounds, smoothScrollEnabled: Boolean = false, overrideStrategy: ScrollOverrideStrategy = ScrollOverrideStrategy.WhenNewPos, innerContainerCallback: ((container: IHardtileContainerView)->Boolean)? = null): Boolean {
        val arr = (vm as? IHardtileContainerViewModel)?.array ?: return false
        val index = arr.indexOf(itemVM)
        if (index < 0 || index >= arr.size)return false
        return focusToTileEx(index, scrollMode, smoothScrollEnabled, overrideStrategy, innerContainerCallback)
    }

    //для фокусировки "среднейго" тайла строки невзирая на то место откуда пришел фокус если вернем {null}
    //или для фокусировки первого тайла
    fun overrideDefaultFocusOnMove(focusedRect: Rect, direction: Int, ignoreFocusView: View?): SingleValueContainer<View?>? {
        return (vm as? IHardtileContainerViewModel)?.overrideDefaultFocusOnMove(focusedRect, direction, ignoreFocusView)
    }

    fun calcScrollInRebuild(crdArray: IntArray, size: Int, reason: IHardtileContainerView.RebuildReason, prevScroll: Int, prevAllTilesTotalSize: Int): Int? {
        return (vm as? IHardtileContainerViewModel)?.calcScrollInRebuild(crdArray, size, reason, prevScroll, prevAllTilesTotalSize)
    }

    fun onTileFocused(index: Int) {
        (vm as? IHardtileContainerViewModel)?.onTileFocused(index)
    }

    fun checkBeforeFocusLostInRebuild(focusedTile: View)
    fun checkAfterFocusLostInRebuild(removedFocusedViewRect: Rect)
}

//чтоб пробежать все дерево от парента к чилдам
interface IHardtileTraversable {
    open class ReturnValue()
    class FinishWithResult(val result: Any?) : ReturnValue()
    object Continue : ReturnValue()
    object BreakTraverseBranch : ReturnValue()

    fun traverse(cb: (v: Any) -> ReturnValue): ReturnValue // null - continue traverse, BreakTraverseBranch - break current branch, any other - final break
}

inline fun<reified T> IHardtileTraversable.forEachTraverse(crossinline cb: (v: T) -> Unit) {
    traverse { v->
        (v as? T)?.let {
            cb(it)
        }
        IHardtileTraversable.Continue
    }
}

inline fun<reified T> IHardtileTraversable.findTraverse(crossinline cb: (v: T) -> Boolean): T? {
    return (traverse { v->
        (v as? T)?.let {
            if (cb(it)) return@traverse IHardtileTraversable.FinishWithResult(it)
        }
        IHardtileTraversable.Continue
    } as? IHardtileTraversable.FinishWithResult)?.result as? T
}

val IHardtileView.asView: View
    get() = this as View

//корретктное убивание вьюхи
fun IHardtileView.freeAndVMNotify() {
    val vm = vm
    vm?.onPreFreeView(this)
    free()
    vm?.onPostFreeView(this)
}

//для посика интефейса в дереве от чилда к паренту, например тайл хочет понять на каком экране он очутился
inline fun<reified T> IHardtileViewModel.upFind(): T? {
    var p: Any? = parentVM
    while (p != null) {
        if (p is T)return p
        p = (p as? IHardtileViewModel)?.parentVM
    }
    return null
}

inline fun<reified T> IHardtileViewModel.thisOrUpFind(): T? {
    return this as? T ?: upFind<T>()
}



fun ViewGroup.rectOf(view: View): Rect {
    val r = Rect()
    view.getDrawingRect(r)
    offsetDescendantRectToMyCoords(view, r)
    return r
}

fun View.findFocusedView(): View? {
    if (isFocused)return this
    return (this as? ViewGroup)?.focusedChild?.findFocusedView()
}

//чтоб найти именно вьюху тайла
//ищем вьюху оторая является чилдом и в тоже время содержит view или сама есть эта view
fun ViewParent.childContainsView(view: View?): View? {
    var v: View? = view
    var par: ViewParent? = view?.parent
    while (par !== this) {
        if (par == null)
            return null
        v = par as? View
        par = par.parent
    }
    return v
}

inline fun<reified T> ViewParent.findParent(): T? {
    var par: ViewParent? = parent
    while (par != null) {
        if (par is T)return par
        par = par.parent
    }
    return null
}

//можем ли фокусить вьюху?
val View.isAnyFocusableExist : Boolean
    get() {
        if (visibility != View.VISIBLE)
            return false
        if (isFocusable)
            return true
        val group = this as? ViewGroup ?: return false
        for (i in 0 until group.childCount) {
            if (group.getChildAt(i).isAnyFocusableExist) {
                return true
            }
        }
        return false
    }

abstract class SimpleTileVM<TView>(val fixedViewWidth: Int, val fixedViewHeight: Int, parVM: Any? = null) : IHardtileViewModel  where TView : View, TView : IHardtileView {
    override var parentVM: Any? = parVM
    open var view: TView? = null

    override fun viewWidth(width: Int, height: Int): Int { return fixedViewWidth }
    override fun viewHeight(width: Int, height: Int): Int { return fixedViewHeight }

    override fun onPreFreeView(v: IHardtileView) {
        if (v !== view)
            throw InvalidParameterException()
        view = null
    }

    abstract fun viewBuild(context: Context, width: Int, height: Int): TView

    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        view = viewBuild(context, width, height)
        return view!!
    }
}

abstract class SimpleTileCommonFrameLayout(con: Context) : FrameLayout(con), IHardtileView {
    override fun free() {
    }
}

