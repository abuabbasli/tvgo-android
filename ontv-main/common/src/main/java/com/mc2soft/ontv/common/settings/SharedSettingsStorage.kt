package com.mc2soft.ontv.common.settings

import android.content.Context
import com.mc2soft.ontv.common.BaseApp

open class SharedSettingsStorage : ISharedSettingsStorage {
    companion object {
        const val PREF_FILE = "settings"
    }

    val context: Context
        get() = BaseApp.instance.applicationContext

    override var auth: String?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_AUTH, str ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_AUTH, "")?.takeIf { it.isNotEmpty() }

    override var mac: String?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_MAC, str ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_MAC, "")?.takeIf { it.isNotEmpty() }

    override var server: String?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_SERVER, str ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_SERVER, "")?.takeIf { it.isNotEmpty() }

    override var theme: String?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_THEME, str ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_THEME, "")?.takeIf { it.isNotEmpty() }

    override var consoleAuth: String?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_CONSOLE_AUTH, str ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_CONSOLE_AUTH, "")?.takeIf { it.isNotEmpty() }

    override var consoleDefaults: String?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_CONSOLE_DEFAULTS, str ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_CONSOLE_DEFAULTS, "")?.takeIf { it.isNotEmpty() }

    override var updateAppsUrl: String?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_UPDATE_APPS_URL, str ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_UPDATE_APPS_URL, "")?.takeIf { it.isNotEmpty() }

    override var devModeTimestamp: Long?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_DEV_MODE_TIMESTAMP, str?.toString() ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_DEV_MODE_TIMESTAMP, "")?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    override var setUseTextureViewInPlayerTimestamp: Long?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP, str?.toString() ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP, "")?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    override var ontvUpdated: Boolean?
        set(str) {
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(SharedSettingsContentResolverHelper.FIELD_ONTV_UPDATED, str?.toString() ?: "").commit()
        }
        get() = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(SharedSettingsContentResolverHelper.FIELD_ONTV_UPDATED, "")?.takeIf { it.isNotEmpty() }?.toBoolean()
}