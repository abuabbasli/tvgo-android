package com.mc2soft.ontv.common

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.lang.NullPointerException
import java.net.URL

@Serializable
class AppsList {
    @Serializable
    class AppPackageInfo {
        var packageName: String? = null

        var downloadLink: String? = null

        var versionCode: Long = 0

        fun isNeedUpdate(context: Context): Boolean {
            val pInfo = try {
                context.getPackageManager().getPackageInfo(packageName ?: "", 0)
            } catch (ex: PackageManager.NameNotFoundException) {
                null
            }
            if (downloadLink != null && (pInfo == null || versionCode > pInfo.versionCode)) {
                return true
            }
            return false
        }
    }

    val apps = ArrayList<AppPackageInfo>()

    fun isNeedUpdateAnyApp(context: Context): Boolean {
        return  apps.any { it.isNeedUpdate(context) }
    }

    companion object {
        fun getAppsListUrl(): String? {
            val urlBaseStr = BaseApp.sharedSettings.updateAppsUrlOrDefault?.takeIf { it.isNotBlank() } ?: return null
            val uri = android.net.Uri.parse(urlBaseStr).buildUpon()

            try {
                uri.appendQueryParameter("mac", BaseApp.sharedSettings.macOrDefault)
            } catch (ex: Exception) { }

            try {
                uri.appendQueryParameter("firmware", DeviceInfo.firmwareVersion)
            } catch (ex: Exception) { }

            try {
                val deviceId = android.provider.Settings.Secure.getString(BaseApp.instance.applicationContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                uri.appendQueryParameter("android_id", deviceId)
            } catch (ex: Exception) { }

            return uri.build().toString()
        }

        fun load(listJsonURL: String?): AppsList {
            val urlStr = listJsonURL?.takeIf { it.isNotBlank() } ?: throw NullPointerException("apps list null or empty")
            val str = URL(urlStr).readText()
            return Json { ignoreUnknownKeys = true }.decodeFromString<AppsList>(str)
        }

        fun load(): AppsList {
            return load(getAppsListUrl())
        }
    }
}