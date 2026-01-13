package com.mc2soft.ontv.common

import androidx.multidex.MultiDexApplication
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.mc2soft.ontv.common.settings.ISharedSettingsStorage
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper
import com.mc2soft.ontv.common.ui.BaseActivity
import com.mc2soft.ontv.common.ui.ErrorDialog
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter


abstract class BaseApp(val appcenterkey: String? = null) : MultiDexApplication() {
    companion object {
        lateinit var instance: BaseApp
            private set

        fun getDimension(id: Int): Int {
            return instance.applicationContext.resources.getDimension(id).toInt()
        }

        fun handleError(ex: java.lang.Exception, fatal: Boolean? = null, show: Boolean? = null) {
            Firebase.crashlytics.recordException(ex)
            instance.handleError(ex, fatal, show)
        }

        val sharedSettings: ISharedSettingsStorage by lazy {
            instance.createSharedSettings()
        }
    }

    override fun onCreate() {
        instance = this
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        appcenterkey?.let {
            AppCenter.start(this, it, Crashes::class.java, Analytics::class.java)
            DeviceInfo.macAddress?.let {
                AppCenter.setUserId(it)
            }
        }
    }

    abstract fun handleError(ex: java.lang.Exception, fatal: Boolean?, show: Boolean?)

    fun logError(ex: java.lang.Exception) {
        val sw = StringWriter()
        val pw = PrintWriter(sw, true)
        ex.printStackTrace(pw)
        val msg = sw.buffer.toString()

        Timber.e(msg)

        appcenterkey?.let {
            Crashes.trackError(ex)
        }
        Firebase.crashlytics.recordException(ex)

    }

    fun showErrorDialog(title: String, msg: String, fatal: Boolean) {
        GlobalScope.launch(Dispatchers.Main) {
            BaseActivity.activeActivity?.let { act ->
                ErrorDialog().also {
                    it.title = title
                    it.message = msg
                    it.fatal = fatal
                }.show(act.supportFragmentManager, null)
            }
        }
    }
    protected open fun createSharedSettings(): ISharedSettingsStorage {
        return SharedSettingsContentResolverHelper
    }
}