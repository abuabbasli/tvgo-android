package com.mc2soft.ontv.launcher

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContentProviderCompat.requireContext
import com.mc2soft.ontv.common.AppsList
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.DeviceInfo
import com.mc2soft.ontv.common.consoleapi.ConsoleAuthData
import com.mc2soft.ontv.common.consoleapi.NetConsoleCompany
import com.mc2soft.ontv.common.consoleapi.NetConsoleInfo
import com.mc2soft.ontv.common.consoleapi.api.NetConsoleApi
import com.mc2soft.ontv.common.run_helpers.RunOnTvActivityHelper
import com.mc2soft.ontv.common.run_helpers.RunUpdateApplicationsActivityHelper
import com.mc2soft.ontv.common.run_helpers.RunUpdateApplicationsActivityHelper.Companion.BACKGROUND
import com.mc2soft.ontv.common.settings.ISharedSettingsStorage
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.NotificationMessages
import com.mc2soft.ontv.common.stalker_portal.entities.NetMessageData
import com.mc2soft.ontv.common.ui.*
import com.mc2soft.ontv.launcher.databinding.LauncherActivityBinding
import com.microsoft.appcenter.analytics.Analytics
import kotlinx.coroutines.*
import timber.log.Timber


class LauncherActivity : BaseActivity() {
    companion object {
        const val ITEM_NAME_ID_TV = "tv"
        const val ITEM_NAME_ID_VOD = "vod"
        const val ITEM_NAME_ID_YOUTUBE = "youtube"
        const val ITEM_NAME_ID_NETFLIX = "netflix"
        const val ITEM_NAME_ID_SERVER_PREFS = "server_prefs"
        const val INITIAL_INIT_PERIOD = 3000L
        const val YOUTUBE_PACKAGE1 = "com.google.android.youtube.tv"
        const val YOUTUBE_PACKAGE2 = "com.google.android.youtube"
        const val NETFLIX_PACKAGE = "com.netflix.mediaclient"
        const val UPDATE_APPLICATIONS_PACKAGE = "com.mc2soft.ontv.update_applications"
        const val SPEEDTEST_PACKAGE = "org.zwanoo.android.speedtest"
        const val SETUP_WIZARD_PACKAGE = "com.mbx.settingsmbox"
        const val LOCAL_PREF_FILE = "local_prefs"
        const val LOCAL_PREF_SETUP_WIZARD_STARTED_ONCE = "SETUP_WIZARD_STARTED_ONCE"

        val magicSeq = arrayOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER
        )
    }

    private lateinit var vb: LauncherActivityBinding
    private var showDevAppsRows: Boolean = false
        set(v) {
            field = v
            updateView()
        }
    private var magicSeqIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vb = LauncherActivityBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.version.text = BuildConfig.VERSION_NAME

        vb.buttonOnTVPlayTV.setOnClickListener {
            try {
                RunOnTvActivityHelper.run(this, false)
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        vb.buttonOnTVPlayMovies.setOnClickListener {
            try {
                Analytics.trackEvent("OnTvPlayMovies Click")
                Timber.e("OnTvPlayMovies Click")
                RunOnTvActivityHelper.run(this, true)
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        vb.buttonYoutube.setOnClickListener {
            Analytics.trackEvent("Youtube Started")
            try {
                getLaunchIntent(BaseApp.sharedSettings.consoleDefaultsObj?.getApplicationRunPackageName(ITEM_NAME_ID_YOUTUBE))?.let {
                    startActivity(it)
                    return@setOnClickListener
                }
                getLaunchIntent(YOUTUBE_PACKAGE1)?.let {
                    startActivity(it)
                    return@setOnClickListener
                }
                getLaunchIntent(YOUTUBE_PACKAGE2)?.let {
                    startActivity(it)
                    return@setOnClickListener
                }
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        vb.buttonNetflix.setOnClickListener {
            Analytics.trackEvent("Netflix Started")
            try {
                getLaunchIntent(BaseApp.sharedSettings.consoleDefaultsObj?.getApplicationRunPackageName(ITEM_NAME_ID_NETFLIX))?.let {
                    startActivity(it)
                    return@setOnClickListener
                }
                startActivity(getLaunchIntent(NETFLIX_PACKAGE))
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        vb.buttonCheckUpdates.setOnClickListener {
            try {
                val intent = getLaunchIntent(UPDATE_APPLICATIONS_PACKAGE)
                intent?.removeExtra(BACKGROUND)
                intent?.putExtra(BACKGROUND, false)
                intent?.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        vb.buttonSettings.setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        vb.buttonServerPrefs.setOnClickListener {
            try {
                RunOnTvActivityHelper.runSettings(this)
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        vb.buttonDiagnostics.setOnClickListener {
            showDevAppsRows = true
        }

        vb.buttonSpeedTest.setOnClickListener {
            Analytics.trackEvent("Speedtest Started")
            try {
                startActivity(getLaunchIntent(SPEEDTEST_PACKAGE))
            } catch (ex: Exception) {
                BaseApp.handleError(ex, fatal = false)
            }
        }

        scope?.launch {
            NotificationMessages.netMessage.collect {
                handleNotification()
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            handleNotification()
        }

        if (BuildConfig.DEBUG) {
            //BaseApp.sharedSettings.devModeTimestamp = System.currentTimeMillis() //FIXME
        }
    }

    override fun mapNameToTheme(name: String?): Int? {
        return when (name) {
            SharedSettingsContentResolverHelper.THEME_TEST -> R.style.Theme_Launcher_Test
            NetConsoleCompany.COLOR_PRESET_ONTV ->  R.style.Theme_Launcher_OnTV
            NetConsoleCompany.COLOR_PRESET_TvinTV ->  R.style.Theme_Launcher_TvinTV
            NetConsoleCompany.COLOR_PRESET_BirLinkTV ->  R.style.Theme_Launcher_Birlink
            else -> null
        }
    }

    override fun onStart() {
        super.onStart()
        val consoleDefaults: NetConsoleInfo? = BaseApp.sharedSettings.consoleDefaultsObj
        Analytics.trackEvent("Launcher Started", mapOf("Customer" to consoleDefaults?.company?.name))
        if (createdWithThemeName != BaseApp.sharedSettings.themeOrDefault) {
            restart()
            return
        }

        startup()

        startedScope?.launch {
            while (true) {
                delay(INITIAL_INIT_PERIOD)
                checkInitialLoad()
            }
        }

        //for devMode update, and fix devMode after power off
        startedScope?.launch {
            while(true) {
                delay(1000)
                updateView()
            }
        }

        updateView()

        startSetupWizardOnceHack()
    }

    private var startupJob: Job? = null
    private fun startup() {
        startupJob?.cancel()
        startupJob = startedScope?.launch(Dispatchers.IO) {
            try {
                val mac = BaseApp.sharedSettings.macOrDefault
                val authStr = BaseApp.sharedSettings.consoleAuth
                val auth = ConsoleAuthData.deserialize(authStr)?.takeIf { it.mac == mac } ?: run {
                    ConsoleAuthData(mac, ConsoleAuthData.genToken(mac)).also {
                        BaseApp.sharedSettings.consoleAuth = it.serialize()
                    }
                }

                BaseApp.sharedSettings.consoleDefaultsObj = NetConsoleApi.getInfo(auth)
                startedScope?.launch {
                    updateView()
                }

                if (createdWithThemeName != BaseApp.sharedSettings.themeOrDefault) {
                    startedScope?.launch {
                        restart()
                    }
                } else {
                    afterStart()
                }
            } catch (ex: CancellationException) {
            } catch (ex: java.lang.Exception) {
                BaseApp.sharedSettings.consoleAuth = null
                BaseApp.handleError(ex)
                afterStart()
            }
        }
    }

    private fun afterStart() {
        Timber.e("Launcher after start")
        startedScope?.launch(Dispatchers.IO) {
            try {
                val list = AppsList.load()
                if (list.isNeedUpdateAnyApp(this@LauncherActivity)) {
                    startedScope?.launch {
                        if (isResume) {
                            RunUpdateApplicationsActivityHelper.run(context = applicationContext, appsList = list, background = true)
//                            UpdateAppsDialog().also {
//                                it.list = list
//                            }.show(supportFragmentManager, null)
                        }
                    }
                }
            } catch (ex: java.lang.Exception) {
                BaseApp.handleError(ex)
            }
        }

        startedScope?.launch(Dispatchers.IO) {
            try {
                AuthorizedUser.startup( false, false)

                launch {
                    while (true) {
                        NotificationMessages.triggerReadNetMessages()
                        delay(NotificationMessages.READ_NET_MESSAGES_POOL_DELAY)
                    }
                }

                launch {
                    while(true) {
                        delay(AuthorizedUser.REAUTH_HACK_DELAY_TIME)
                        try {
                            AuthorizedUser.startup(false, false)
                        } catch (ex: java.lang.Exception) {
                            BaseApp.handleError(ex)
                        }
                    }
                }
            } catch (ex: java.lang.Exception) {
                BaseApp.handleError(ex)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AuthorizedUser.stop()
    }

    override fun onResume() {
        Timber.e("Launcher resume")
        super.onResume()
        updateView()
        checkInitialLoad()
        startedScope?.launch(Dispatchers.IO) {
            Timber.e("Updated open ontv ${BaseApp.sharedSettings.ontvUpdated}")

            if (BaseApp.sharedSettings.ontvUpdated == true){
                Timber.e("Updated open ontv")
                RunOnTvActivityHelper.run(applicationContext, false)
                BaseApp.sharedSettings.ontvUpdated = false
            }
        }
    }

    private fun checkInitialLoad() {
        if (isResume && startupJob?.isActive != true && BaseApp.sharedSettings.consoleDefaults == null) {
            startup()
        }
    }

    override fun onBackPressed() {
        if (showDevAppsRows) {
            showDevAppsRows = false
            vb.buttonDiagnostics.requestFocus()
            return
        }
        super.onBackPressed()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val code = event.keyCode
            if (magicSeq[magicSeqIndex] == code) {
                magicSeqIndex += 1
            } else {
                magicSeqIndex = 0
            }
            if (magicSeqIndex == magicSeq.size) {
                magicSeqIndex = 0
                BaseApp.sharedSettings.devModeTimestamp =  if (ISharedSettingsStorage.isDevMode) null else System.currentTimeMillis()
                updateView()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun updateView() {
        val devMode = ISharedSettingsStorage.isDevMode
        val consoleDefaults: NetConsoleInfo? = BaseApp.sharedSettings.consoleDefaultsObj
        vb.buttonOnTVPlayTV.visible(consoleDefaults?.isEnableApplication(ITEM_NAME_ID_TV) == true)
        vb.buttonOnTVPlayMovies.visible(consoleDefaults?.isEnableApplication(ITEM_NAME_ID_VOD) == true)
        vb.buttonYoutube.visible(consoleDefaults?.isEnableApplication(ITEM_NAME_ID_YOUTUBE) == true)
        vb.buttonNetflix.visible(consoleDefaults?.isEnableApplication(ITEM_NAME_ID_NETFLIX) == true)
        vb.buttonSpeedTest.visible(getLaunchIntent(SPEEDTEST_PACKAGE) != null)

        vb.buttonServerPrefs.visible(consoleDefaults?.isEnableApplication(ITEM_NAME_ID_SERVER_PREFS) == true || BuildConfig.DEBUG)
        //vb.icon.load(consoleDefaults?.company?.logo, theme.getAttrDrawable(com.mc2soft.ontv.common.R.attr.theme_logo_sm))

        if (showDevAppsRows) {
            vb.mainRow.visible(false)
            vb.devRow.visible(false)
            vb.devAppsRow.visible(true)
        } else {
            vb.mainRow.visible(true)
            vb.devAppsRow.visible(false)
            vb.devRow.visible(devMode)
        }
        if (!vb.root.hasFocus() && isResume) {
            if (vb.mainRow.isVisible && vb.buttonOnTVPlayTV.isVisible) {
                vb.buttonOnTVPlayTV.requestFocus()
            } else {
                vb.root.requestFocus()
            }
        }
        vb.auth.visible(devMode)
        vb.auth.text = "mac:${BaseApp.sharedSettings.macOrDefault} ip:${DeviceInfo.localIpAddress}"
    }

    fun getLaunchIntent(packageName: String?): Intent? {
        val packageName = packageName?.trim()?.takeIf { it.all { !it.isWhitespace() } && it.contains(".") } ?: return null
        try {
            return packageManager.getLaunchIntentForPackage(packageName)
        } catch (ex: Exception) {
            return null
        }
    }

    fun handleNotification() {
        NotificationMessages.netMessage.value?.data?.let {
            when (it.eventType) {
                NetMessageData.EventType.send_msg -> {
                    if (isResume && supportFragmentManager.findFragmentByTag(StalkerPortalNotificationDialog.TAG) == null) {
                        StalkerPortalNotificationDialog().show(supportFragmentManager, StalkerPortalNotificationDialog.TAG)
                    }
                }
                else -> {
                    NotificationMessages.markMessageReadOrExpire()
                }
            }
        }
    }

    class UpdateAppsDialog : UserDialog() {
        var list: AppsList? = null
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setStyleQuestion()
            setTexts(resources.getString(R.string.update_applications_dlg_message), null)
            addButton(resources.getString(R.string.update_applications_dlg_ok)) {
                dismiss()
                RunUpdateApplicationsActivityHelper.run(requireContext(), list!!)
            }
            addButton(resources.getString(com.mc2soft.ontv.common.R.string.cancel)) {
                dismiss()
            }
        }

        override fun onStart() {
            super.onStart()
            if (list == null)dismiss()
        }
    }

    var isSetupWizardStartedOnce: Boolean
        set(v) {
            getSharedPreferences(LOCAL_PREF_FILE, Context.MODE_PRIVATE).edit().putBoolean(LOCAL_PREF_SETUP_WIZARD_STARTED_ONCE, v).commit()
        }
        get() = getSharedPreferences(LOCAL_PREF_FILE, Context.MODE_PRIVATE).getBoolean(LOCAL_PREF_SETUP_WIZARD_STARTED_ONCE, false)

    fun startSetupWizardOnceHack() {
        if (isSetupWizardStartedOnce)return
        isSetupWizardStartedOnce = true
        getLaunchIntent(SETUP_WIZARD_PACKAGE)?.let {
            startActivity(it)
        }
    }
}