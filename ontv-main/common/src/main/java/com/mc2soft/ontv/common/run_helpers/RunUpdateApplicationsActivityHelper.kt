package com.mc2soft.ontv.common.run_helpers

import android.content.Context
import android.content.Intent
import com.mc2soft.ontv.common.AppsList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RunUpdateApplicationsActivityHelper {
    companion object {
        val APP_LIST_URL = "APP_LIST_URL"
        val APP_LIST_JSON = "APP_LIST_JSON"
        val START_AFTER = "START_AFTER"
        val BACKGROUND = "BACKGROUND"

        fun getIntent(context: Context): Intent? {
            return context.packageManager.getLaunchIntentForPackage("com.mc2soft.ontv.update_applications")
        }

        fun run(context: Context, appsListUrl: String, startAfterIntent: Intent? = null): Boolean {
            getIntent(context)?.let {
                if (startAfterIntent != null) {
                    it.putExtra(START_AFTER, startAfterIntent)
                }
                it.putExtra(APP_LIST_URL, appsListUrl)
                context.startActivity(it)
                return true
            }
            return false
        }

        fun run(context: Context, appsList: AppsList, startAfterIntent: Intent? = null, background: Boolean = false): Boolean {
            getIntent(context)?.let {
                val intent = it
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (startAfterIntent != null) {
                    intent.putExtra(START_AFTER, startAfterIntent)
                }
                val json = Json.encodeToString(appsList)
                intent.removeExtra(APP_LIST_JSON)
                intent.putExtra(APP_LIST_JSON, json)
                intent.putExtra(BACKGROUND, background)
                context.startActivity(intent)
                return true
            }
            return false
        }
    }
}