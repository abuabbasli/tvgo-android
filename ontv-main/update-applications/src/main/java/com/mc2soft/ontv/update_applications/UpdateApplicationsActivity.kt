package com.mc2soft.ontv.update_applications;

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.mc2soft.ontv.common.AppsList
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.run_helpers.RunOnTvActivityHelper
import com.mc2soft.ontv.common.run_helpers.RunUpdateApplicationsActivityHelper
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper
import com.mc2soft.ontv.common.ui.BaseActivity
import com.mc2soft.ontv.update_applications.databinding.UpdateApplicationsActivityBinding
import com.microsoft.appcenter.analytics.Analytics
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.sql.Time


class UpdateApplicationsActivity : BaseActivity() {
    private lateinit var vb: UpdateApplicationsActivityBinding

    companion object {
        const val SHOW_DELAY = 2000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = UpdateApplicationsActivityBinding.inflate(layoutInflater)
        setContentView(vb.root)
        setTitle(Stage.Starting, null)

        vb.version.text = BuildConfig.VERSION_NAME
    }

    override fun onStart() {
        super.onStart()
        Analytics.trackEvent("UpdateApplication Started")
        val isBackground: Boolean = intent?.extras?.getBoolean(
            RunUpdateApplicationsActivityHelper.BACKGROUND,
            false
        ) == true
        if (isBackground) {
            moveTaskToBack(isBackground)
        }
        if (intent.hasExtra(RunUpdateApplicationsActivityHelper.APP_LIST_JSON)) {
            try {
                val appsList = Json.decodeFromString<AppsList>(
                    intent.getStringExtra(
                        RunUpdateApplicationsActivityHelper.APP_LIST_JSON
                    )!!
                )
                startUpdates(appsList, null)
            } catch (ex: Exception) {
                BaseApp.handleError(ex)
            }
        } else {
            var appsListUrl: String? =
                if (intent.hasExtra(RunUpdateApplicationsActivityHelper.APP_LIST_URL)) {
                    intent.getStringExtra(RunUpdateApplicationsActivityHelper.APP_LIST_URL)
                        ?.takeIf { it.isNotBlank() }
                } else null

            if (appsListUrl == null)
                appsListUrl = AppsList.getAppsListUrl()

            startUpdates(null, appsListUrl)
        }
    }

    private var updateScope: CoroutineScope? = null

    override fun onStop() {
        super.onStop()
        updateScope?.cancel()
        updateScope = null
    }

    fun startUpdates(inAppsList: AppsList?, inAppsListUrl: String?) {
        updateScope?.cancel()
        updateScope = CoroutineScope(Dispatchers.IO)
        updateScope?.launch {
            setTitle(Stage.CheckUpdates, null)
            try {
                val appsList = inAppsList ?: inAppsListUrl?.let {
                    Timber.i(it)
                    AppsList.load(inAppsListUrl)
                } ?: throw Exception("no data")
                inAppsList?.apps?.forEach {
                    Timber.e("Updates apps${it.packageName} ${it.downloadLink} ${it.versionCode}")
                }
                if (!appsList.isNeedUpdateAnyApp(this@UpdateApplicationsActivity)) {
                    setTitle(Stage.AlreadyUpToDate)
//                    BaseApp.sharedSettings.ontvUpdated = true
//                    Timber.e("Updated ontv ${BaseApp.sharedSettings.isOntvUpdated}")
                    delay(SHOW_DELAY)
                    scope?.launch {
                        finish()
                    }
                    return@launch
                }

                for (app in appsList.apps) {
                    if (app.isNeedUpdate(this@UpdateApplicationsActivity)) {

                        setTitle(Stage.DownloadingApk, app)
                        val file =
                            File(applicationContext.filesDir, "update_" + app.packageName + ".apk")
                        if (file.exists()) {
                            file.delete()
                        }
                        URL(app.downloadLink).openStream().use {
                            Channels.newChannel(it).use { rbc ->
                                FileOutputStream(file).use { fos ->
                                    fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
                                }
                            }
                        }

                        setTitle(Stage.InstallingApk, app)

                        if (app.packageName == BuildConfig.APPLICATION_ID) {
//                            installApkSimpleMode(file)
                            installApkSilentMode(file, app)
                        } else {
                            installApkSilentMode(file, app)
                        }
                        Analytics.trackEvent("App Updated", mapOf("packageName" to app.packageName, "versionCode" to app.versionCode.toString()))
                    }
                }

                setTitle(Stage.UpdateSuccess, null)
                BaseApp.sharedSettings.ontvUpdated = true
                Timber.e("Updated ontv ${BaseApp.sharedSettings.isOntvUpdated}")

//                val newIntent =
//                    intent.getParcelableExtra<Intent>(RunUpdateApplicationsActivityHelper.START_AFTER)
//                newIntent?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
//                startActivity(newIntent)
                delay(SHOW_DELAY)
                scope?.launch {
                    Timber.e("Finish")
                    finish()
                }
            } catch (ex: java.lang.Exception) {
                BaseApp.handleError(ex)
            }
        }
    }

    suspend fun installApkSimpleMode(file: File) {
        scope?.launch {
            try {
                grantUriPermission(
                    packageName,
                    Uri.fromFile(file),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                val apkUri = FileProvider.getUriForFile(
                    this@UpdateApplicationsActivity,
                    BuildConfig.APPLICATION_ID + ".fileprovider", file
                )

                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setData(apkUri)
                    setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (ex: Exception) {
                BaseApp.handleError(ex)
            }
        }
        delay(SHOW_DELAY)
    }

    suspend fun installApkSilentMode(file: File, app: AppsList.AppPackageInfo) {
        try {
            file.inputStream().use {
                CosuUtils.installPackage(this, it, app.packageName!!)
            }
            //wait 10 sec
            for (i in 0..20) {
                delay(500)
                if (!app.isNeedUpdate(this@UpdateApplicationsActivity)) return
            }
        } catch (ex: Exception) {
            BaseApp.handleError(ex)
        }
        // Display dialog
//        BaseApp.handleError(java.lang.Exception("no update installed for ${app.packageName}"))
    }

    override fun finish() {
//                                RunOnTvActivityHelper.run(applicationContext, false)
        super.finish()
//                        RunOnTvActivityHelper.run(applicationContext, false)
        if (intent.hasExtra(RunUpdateApplicationsActivityHelper.START_AFTER)) {
            val newIntent =
                intent.getParcelableExtra<Intent>(RunUpdateApplicationsActivityHelper.START_AFTER)
            startActivity(newIntent)
        }
    }

    enum class Stage {
        Starting,
        CheckUpdates,
        DownloadingApk,
        InstallingApk,
        UpdateSuccess,
        AlreadyUpToDate
    }

    fun setTitle(stage: Stage, app: AppsList.AppPackageInfo? = null) {
        Timber.i(stage.name)
        scope?.launch {
            val appInfo = app?.let { "\n${it.packageName} ver: ${it.versionCode}\n" }
            vb.title.text = when (stage) {
                Stage.Starting -> ""
                Stage.CheckUpdates -> resources.getString(R.string.stage_check_updates)
                Stage.DownloadingApk -> resources.getString(R.string.stage_downloading) // + appInfo
                Stage.InstallingApk -> resources.getString(R.string.stage_installing) // + appInfo
                Stage.UpdateSuccess -> resources.getString(R.string.stage_update_success)
                Stage.AlreadyUpToDate -> resources.getString(R.string.stage_no_need_updates)
            }
            vb.subtitle.visibility =
                if (stage == Stage.UpdateSuccess || stage == Stage.AlreadyUpToDate) View.INVISIBLE else View.VISIBLE
        }
    }
}
