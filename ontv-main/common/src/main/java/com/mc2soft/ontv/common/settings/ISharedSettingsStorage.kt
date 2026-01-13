package com.mc2soft.ontv.common.settings

import android.os.SystemClock
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.BuildConfig
import com.mc2soft.ontv.common.DeviceInfo
import com.mc2soft.ontv.common.consoleapi.NetConsoleInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ISharedSettingsStorage {
    companion object {
        const val SERVER_DEFAULT_1 = "http://portal.tvitech.com/"
        const val SERVER_DEFAULT_2 = "http://devportal.tvitech.az/"
        const val FAKE_MAC = "00:1A:79:91:27:74"
        const val UPDATE_APPS_URL_DROPBOX = "https://www.dropbox.com/s/pjj0jk8agq7mrgp/apps_list.json?raw=1"
        const val DEV_MODE_TIME = 5 * 60000L
        const val USE_TEXTURE_TIME = 3 * 60 * 60000L

        val isDevMode: Boolean
            get() = BaseApp.sharedSettings.devModeTimestamp?.let {
                System.currentTimeMillis() - it < Math.min(DEV_MODE_TIME, SystemClock.elapsedRealtime())
            } ?: false

        val isUseTextureViewInPlayer: Boolean
            get() = BaseApp.sharedSettings.setUseTextureViewInPlayerTimestamp?.let {
                System.currentTimeMillis() - it < Math.min(USE_TEXTURE_TIME, SystemClock.elapsedRealtime())
            } ?: false
    }

    var auth: String?
    var mac: String?
    var server: String?
    var theme: String?
    var consoleAuth: String?
    var consoleDefaults: String?
    var updateAppsUrl: String?
    var devModeTimestamp: Long?
    var setUseTextureViewInPlayerTimestamp: Long?

    var ontvUpdated: Boolean?

    var consoleDefaultsObj: NetConsoleInfo?
        get() {
            try {
                return consoleDefaults?.let { Json { ignoreUnknownKeys = true }.decodeFromString<NetConsoleInfo>(it) }
            } catch (ex: java.lang.Exception) {
                BaseApp.handleError(ex)
                return null
            }
        }
        set(v) {
            consoleDefaults = v?.let { Json.encodeToString(v) }
        }

    val macOrDefault: String
        get() = mac ?: defaultMac

    val serverOrDefault: String?
        get() = server ?: defaultServer

    val themeOrDefault: String?
        get() = theme ?: defaultTheme

    val updateAppsUrlOrDefault: String?
        get() = updateAppsUrl ?: defaultUpdateAppsUrl

    val defaultServer: String?
        get() = consoleDefaultsObj?.company?.portal

    val defaultTheme: String?
        get() = consoleDefaultsObj?.company?.color_preset

    val defaultMac: String
        get() = DeviceInfo.macAddress ?: throw java.lang.Exception("cant detect mac")

    val defaultUpdateAppsUrl: String?
        get() = consoleDefaultsObj?.company?.update_server

    val isOntvUpdated: Boolean
        get() = ontvUpdated ?: false
}