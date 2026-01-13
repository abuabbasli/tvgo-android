package org.neonwindwalker.hardtiles

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.lang.ref.WeakReference
import java.security.InvalidParameterException

class ViewCache<TView: View>(val context: Context, presize: Int, val creator: (con: Context)->TView) {
    protected val set = HashSet<TView>()

    var optimalFinder: ((set: HashSet<TView>, data: Any?)->TView?)? = null

    fun getOrCreate(data: Any? = null): TView {
        if (set.isNotEmpty()) {
            val v = optimalFinder?.invoke(set, data) ?: set.first()
            set.remove(v)
            return v
        }
        val v = creator(context)
        Log.i("ViewCache", "create ${v.javaClass.simpleName} ${set.size}")
        return v
    }

    //какая-то фигня случается с 4.4 когда мы удаляем вьюхи пихаем их в кеш а затем сразу же заталкиваем в тотже парент, содержание вьюх не обновляется
    //делаем отложенный режим, так работает
    private val delayPutHandler = Handler()

    fun putFree(v: TView?) {
        v?: return
        delayPutHandler.post {
            set.add(v)
        }
    }

    init {
        for (i in 0 until presize)
            set.add(creator(context))
    }

    fun clear() {
        delayPutHandler.removeCallbacksAndMessages(null)
        set.clear()
    }

    companion object {
        inline fun <reified T: View> create(con: Context, presize: Int): ViewCache<T> {
            Log.i("ViewCache", "init ${T::class.java.simpleName}[${presize}]")
            return ViewCache<T>(con, presize) {
                T::class.java.getDeclaredConstructor(Context::class.java).newInstance(con)
            }
        }
    }
}

interface IHardtileCachedView<TVM: IHardtileViewModel> : IHardtileView {
    override var vm: TVM?

    override fun free() {
        vm = null
    }
}

abstract class HardtileCachedCommonFrameLayout<TVM: IHardtileViewModel>(con: Context) : FrameLayout(con), IHardtileCachedView<TVM> {
    override var vm: TVM? = null
        set(v) {
            if (field == v)return
            field = v
            update()
        }
    abstract fun update()
}

abstract class HardtileCachedCommonLinearLayout<TVM: IHardtileViewModel>(con: Context) : LinearLayout(con), IHardtileCachedView<TVM> {
    override var vm: TVM? = null
        set(v) {
            if (field == v)return
            field = v
            update()
        }
    abstract fun update()
}

abstract class CachedTileVM<TVM, TView>(val viewClass: Class<TView>, val fixedViewWidth: Int, val fixedViewHeight: Int, parVM: Any? = null) : IHardtileViewModel  where TView : View, TVM : IHardtileViewModel, TView : IHardtileCachedView<TVM> {
    override var parentVM: Any? = parVM
    open var view: TView? = null

    override fun viewWidth(width: Int, height: Int): Int { return fixedViewWidth }
    override fun viewHeight(width: Int, height: Int): Int { return fixedViewHeight }

    private var viewCache: ViewCache<TView>? = null

    override fun buildView(context: Context, width: Int, height: Int): IHardtileView {
        viewCache = getViewCache(context)
        view = viewCache!!.getOrCreate(this).also { it.vm = this as TVM }
        return view!!
    }

    override fun onPreFreeView(v: IHardtileView) {
        if (v !== view)
            throw InvalidParameterException()
        view = null
    }

    override fun onPostFreeView(v: IHardtileView) {
        viewCache?.putFree(v as TView)
        viewCache = null
    }

    open fun getViewCache(context: Context): ViewCache<TView> {
        return ViewCachesMap.findCachesForContext(context)?.getViewCache<TView>(viewClass) ?: throw java.lang.Exception("no view cache")
    }
}

open class ViewCachesMap(context: Context) {
    val contextWeak = WeakReference(context)
    val map = HashMap<Class<*>, ViewCache<*>>()

    init {
        viewCachesForContext.add(this)
    }

    fun destroy() {
        viewCachesForContext.remove(this)
        map.clear()
    }

    fun<TView: View> getViewCache(cls: Class<*>): ViewCache<TView>? {
        val cache = map.get(cls)
        return cache as? ViewCache<TView>
    }

    inline fun<reified TView: View> add(c: ViewCache<TView>) {
        map.put(TView::class.java, c)
    }

    companion object {
        val viewCachesForContext = ArrayList<ViewCachesMap>()

        fun findCachesForContext(context: Context): ViewCachesMap? {
            viewCachesForContext.removeIf {
                it.contextWeak.get() == null
            }
            return viewCachesForContext.find { it.contextWeak.get() === context }
        }
    }
}