package com.mc2soft.ontv.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper

class SharedSettingsProvider : ContentProvider() {
    companion object {
        const val SETTINGS_CODE = 1
    }

    val uriMatcher: UriMatcher by lazy {
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(SharedSettingsContentResolverHelper.PROVIDER_NAME, SharedSettingsContentResolverHelper.SETTINGS_PATH, SETTINGS_CODE)
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            SETTINGS_CODE -> "vnd.android.cursor.item/text"
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        when (uriMatcher.match(uri)) {
            SETTINGS_CODE -> {
                val keys = ArrayList<String>()
                val vals = ArrayList<String?>()
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_AUTH) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_AUTH
                    vals += SharedSettingsStorage.auth ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_MAC) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_MAC
                    vals += SharedSettingsStorage.macOrDefault
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_SERVER) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_SERVER
                    vals += SharedSettingsStorage.serverOrDefault ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_THEME) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_THEME
                    vals += SharedSettingsStorage.theme ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_CONSOLE_AUTH) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_CONSOLE_AUTH
                    vals += SharedSettingsStorage.consoleAuth ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_CONSOLE_DEFAULTS) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_CONSOLE_DEFAULTS
                    vals += SharedSettingsStorage.consoleDefaults ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_UPDATE_APPS_URL) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_UPDATE_APPS_URL
                    vals += SharedSettingsStorage.updateAppsUrl ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_DEV_MODE_TIMESTAMP) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_DEV_MODE_TIMESTAMP
                    vals += SharedSettingsStorage.devModeTimestamp?.toString() ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP
                    vals += SharedSettingsStorage.setUseTextureViewInPlayerTimestamp?.toString() ?: ""
                }
                if (projection?.isEmpty() != false || projection?.contains(
                        SharedSettingsContentResolverHelper.FIELD_ONTV_UPDATED) == true) {
                    keys += SharedSettingsContentResolverHelper.FIELD_ONTV_UPDATED
                    vals += SharedSettingsStorage.isOntvUpdated.toString() ?: ""
                }
                return MatrixCursor(keys.toTypedArray(), 1).apply {
                    addRow(vals)
                }
            }
            else -> throw java.lang.IllegalArgumentException("Unknown URI $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw java.lang.IllegalArgumentException("not support, use update or query")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw java.lang.IllegalArgumentException("not support, use update or query")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        when (uriMatcher.match(uri)) {
            SETTINGS_CODE -> {
                var count = 0
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_AUTH) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_AUTH)?.let {
                        SharedSettingsStorage.auth = it
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_MAC) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_MAC)?.let {
                        SharedSettingsStorage.mac = it
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_SERVER) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_SERVER)?.let {
                        SharedSettingsStorage.server = it
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_THEME) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_THEME)?.let {
                        SharedSettingsStorage.theme = it
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_CONSOLE_AUTH) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_CONSOLE_AUTH)?.let {
                        SharedSettingsStorage.consoleAuth = it
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_CONSOLE_DEFAULTS) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_CONSOLE_DEFAULTS)?.let {
                        SharedSettingsStorage.consoleDefaults = it
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_UPDATE_APPS_URL) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_UPDATE_APPS_URL)?.let {
                        SharedSettingsStorage.updateAppsUrl = it
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_DEV_MODE_TIMESTAMP) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_DEV_MODE_TIMESTAMP)?.let {
                        SharedSettingsStorage.devModeTimestamp = it.takeIf { it.isNotEmpty() }?.toLongOrNull()
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_SET_USE_TEXTURE_VIEW_IN_PLAYER_TIMESTAMP)?.let {
                        SharedSettingsStorage.setUseTextureViewInPlayerTimestamp = it.takeIf { it.isNotEmpty() }?.toLongOrNull()
                        count += 1
                    }
                }
                if (values?.containsKey(SharedSettingsContentResolverHelper.FIELD_ONTV_UPDATED) == true) {
                    values.getAsString(SharedSettingsContentResolverHelper.FIELD_ONTV_UPDATED)?.let {
                        SharedSettingsStorage.ontvUpdated = it.takeIf { it.isNotEmpty() }?.toBoolean()
                        count += 1
                    }
                }
                return count
            }
            else -> throw java.lang.IllegalArgumentException("Unknown URI $uri")
        }
    }
}