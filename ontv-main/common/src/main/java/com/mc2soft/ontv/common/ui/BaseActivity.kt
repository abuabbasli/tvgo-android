package com.mc2soft.ontv.common.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.R
import com.mc2soft.ontv.common.consoleapi.NetConsoleCompany
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import timber.log.Timber

open class BaseActivity : AppCompatActivity() {
    companion object {
        var activeActivity: BaseActivity? = null
            private set
    }

    var scope: CoroutineScope? = null
        private set

    var startedScope: CoroutineScope? = null
        private set

    var createdWithThemeName: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("${javaClass.simpleName}.onCreate")
        super.onCreate(savedInstanceState)
        scope = CoroutineScope(Dispatchers.Main)
        createdWithThemeName = BaseApp.sharedSettings.themeOrDefault
        mapNameToTheme(createdWithThemeName)?.let {
            setTheme(it)
        }
    }

    open fun mapNameToTheme(name: String?): Int? {
        Log.d("9900", "salam 1: " + name)
        return when (name) {
            SharedSettingsContentResolverHelper.THEME_TEST -> R.style.Theme_OnTvCommon_Test
            NetConsoleCompany.COLOR_PRESET_ONTV -> R.style.Theme_OnTvCommon_OnTV
            NetConsoleCompany.COLOR_PRESET_TvinTV -> R.style.Theme_OnTvCommon_TvinTV
            NetConsoleCompany.COLOR_PRESET_BirLinkTV -> R.style.Theme_OnTvCommon_BirLink
            else -> null
        }
    }

    open fun mapCatToName(netName: String): String {
        return when (netName) {
            "All" -> getString(R.string.category_all)
            "Video" -> getString(R.string.category_video)
            "Series" -> getString(R.string.category_series)
            "Cizgi filmləri" -> getString(R.string.category_cartoons)
            "Musiqi" -> getString(R.string.category_music)
            "Türk" -> getString(R.string.category_turk)
            "Azərbaycan" -> getString(R.string.category_azerbaycan)
            "Animasiyalı filmlər" -> getString(R.string.category_animation)
            "Favorites" -> getString(R.string.category_favorites)
            "History" -> getString(R.string.category_history)
            "Maarifləndirici" -> getString(R.string.category_education)
            "Əyləncə" -> getString(R.string.category_entertainment)
            "Uşaq" -> getString(R.string.category_child)
            "Film" -> getString(R.string.category_film)
            "İnformasiya" -> getString(R.string.category_info)
            "İdman" -> getString(R.string.category_sport)
            "VideoClub" -> getString(R.string.category_videoclub)
            "TV" -> getString(R.string.category_tv)
            else -> netName
        }
    }

    override fun onDestroy() {
        Timber.i("${javaClass.simpleName}.onDestroy")
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    var isStarted: Boolean = false
        private set
    var isResume: Boolean = false
        private set

    override fun onStart() {
        Timber.i("${javaClass.simpleName}.onStart")
        super.onStart()
        isStarted = true
        activeActivity = this
        startedScope = CoroutineScope(Dispatchers.Main)
    }

    override fun onStop() {
        Timber.i("${javaClass.simpleName}.onStop")
        startedScope?.cancel()
        startedScope = null
        isStarted = false
        if (activeActivity === this) {
            activeActivity = null
        }
        super.onStop()
    }

    override fun onResume() {
        Timber.i("${javaClass.simpleName}.onResume")
        super.onResume()
        isResume = true
    }

    override fun onPause() {
        Timber.i("${javaClass.simpleName}.onPause")
        super.onPause()
        isResume = false
    }

    fun restart() {
        Timber.i("${javaClass.simpleName}.restart")
        val newIntent = Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        finish()
        startActivity(newIntent)
    }
}