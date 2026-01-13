package com.mc2soft.ontv.common.run_helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent


class RunOnTvActivityHelper {
    companion object {
        val MOVIE_MODE = "MOVIE_MODE"
        const val PACKAGE = "com.mc2soft.ontv.ontvapp"

        fun getIntent(context: Context): Intent? {
            return context.packageManager.getLaunchIntentForPackage(PACKAGE)
        }

        fun run(context: Context, movieMode: Boolean): Boolean {
            getIntent(context)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                it.putExtra(MOVIE_MODE, movieMode)
                context.startActivity(it)
                return true
            }
            return false
        }

        fun runSettings(context: Context) {
            val intent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                component = ComponentName("com.mc2soft.ontv.ontvapp", "com.mc2soft.ontv.ontvapp.SettingsActivity")
            }
            context.startActivity(intent)
        }
    }
}