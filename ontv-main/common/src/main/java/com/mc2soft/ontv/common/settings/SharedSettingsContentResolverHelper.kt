package com.mc2soft.ontv.common.settings

import android.content.ContentValues
import android.net.Uri
import com.mc2soft.ontv.common.BaseApp
import java.lang.Exception

object SharedSettingsContentResolverHelper : ISharedSettingsStorage {
    const val PROVIDER_NAME = "com.mc2soft.ontv.launcher.provider"
    const val SETTINGS_PATH = "settings"
    const val URL = "content://$PROVIDER_NAME/$SETTINGS_PATH"

    const val FIELD_AUTH = "auth"
    const val FIELD_MAC = "mac"
    const val FIELD_SERVER = "server"
    const val FIELD_THEME = "theme"
    const val FIELD_CONSOLE_AUTH = "consoleAuth"
    const val FIELD_CONSOLE_DEFAULTS = "consoleDefaults"
    const val FIELD_UPDATE_APPS_URL = "updateAppsUrl"
    const val FIELD_DEV_MODE_TIMESTAMP = "devModeTimestamp"
    const val FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP = "setUseTextureViewInPlayerTimestamp"

    const val FIELD_ONTV_UPDATED = "ontvUpdated"

    const val THEME_TEST = "test"

    override var auth: String?
        get() = getValue(FIELD_AUTH)
        set(v) {
            setValue(FIELD_AUTH, v)
        }

    override var mac: String?
        get() = getValue(FIELD_MAC)
        set(v) {
            setValue(FIELD_MAC, v)
        }

    override var server: String?
        get() = getValue(FIELD_SERVER)
        set(v) {
            setValue(FIELD_SERVER, v)
        }

    override var theme: String?
        get() = getValue(FIELD_THEME)
        set(v) {
            setValue(FIELD_THEME, v)
        }

    override var consoleAuth: String?
        get() = getValue(FIELD_CONSOLE_AUTH)
        set(v) {
            setValue(FIELD_CONSOLE_AUTH, v)
        }

    override var consoleDefaults: String?
        get() = getValue(FIELD_CONSOLE_DEFAULTS)
        set(v) {
            setValue(FIELD_CONSOLE_DEFAULTS, v)
        }

    override var updateAppsUrl: String?
        get() = getValue(FIELD_UPDATE_APPS_URL)
        set(v) {
            setValue(FIELD_UPDATE_APPS_URL, v)
        }

    override var devModeTimestamp: Long?
        get() = getValue(FIELD_DEV_MODE_TIMESTAMP)?.toLongOrNull()
        set(v) {
            setValue(FIELD_DEV_MODE_TIMESTAMP, v?.toString())
        }

    override var setUseTextureViewInPlayerTimestamp: Long?
        get() = getValue(FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP)?.toLongOrNull()
        set(v) {
            setValue(FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP, v?.toString())
        }

    override var ontvUpdated: Boolean?
        get() = getValue(FIELD_ONTV_UPDATED)?.toBoolean()
        set(v) {
            setValue(FIELD_ONTV_UPDATED, v?.toString())
        }

    fun getValue(name: String): String? {
        try {
            BaseApp.instance.contentResolver.query(Uri.parse(URL), arrayOf(name), null, null)?.let { cursor->
                while (cursor.moveToNext()) {
                    for (i in 0 until cursor.columnCount) {
                        if (cursor.getColumnName(i) == name)
                            return cursor.getString(i)?.takeIf { it.isNotEmpty() }
                    }
                }
                cursor.close()
            }
        } catch (ex: Exception) {
            BaseApp.handleError(ex)
        }
        return null
    }

    fun setValue(name: String, value: String?) {
        try {
            val values = ContentValues()
            values.put(name, value ?: "")
            BaseApp.instance.contentResolver.update(Uri.parse(URL), values, null, null)
        } catch (ex: Exception) {
            BaseApp.handleError(ex)
        }
    }
}