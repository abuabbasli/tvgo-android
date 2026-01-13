package com.mc2soft.ontv.ontvapp

import android.app.AlertDialog
import android.os.Bundle
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.consoleapi.NetConsoleCompany
import com.mc2soft.ontv.common.settings.ISharedSettingsStorage
import com.mc2soft.ontv.common.settings.ISharedSettingsStorage.Companion.UPDATE_APPS_URL_DROPBOX
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.ui.BaseActivity
import com.mc2soft.ontv.ontvapp.databinding.SettingsActivityBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {
    private lateinit var vb: SettingsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vb = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.buttonSetDefaultMac.setOnClickListener {
            vb.mac.setText(null)
        }
        vb.buttonSetFakeMac.setOnClickListener {
            vb.mac.setText(ISharedSettingsStorage.FAKE_MAC)
        }
        vb.buttonSetServerDefault.setOnClickListener {
            vb.server.setText(null)
        }
        vb.buttonSetServerDefault1.setOnClickListener {
            vb.server.setText(ISharedSettingsStorage.SERVER_DEFAULT_1)
        }
        vb.buttonSetServerDefault2.setOnClickListener {
            vb.server.setText(ISharedSettingsStorage.SERVER_DEFAULT_2)
        }

        vb.buttonSetThemeDefault.setOnClickListener {
            vb.theme.setText(null)
        }
        vb.buttonSetThemeAZERONLINE.setOnClickListener {
            vb.theme.setText(NetConsoleCompany.COLOR_PRESET_ONTV)
        }
        vb.buttonSetThemeBirlink.setOnClickListener {
            vb.theme.setText(NetConsoleCompany.COLOR_PRESET_BirLinkTV)
        }
        vb.buttonSetThemeTvinTV.setOnClickListener {
            vb.theme.setText(NetConsoleCompany.COLOR_PRESET_TvinTV)
        }
        vb.buttonSetThemeTest.setOnClickListener {
            vb.theme.setText(SharedSettingsContentResolverHelper.THEME_TEST)
        }

        vb.buttonUpdateAppsDefault.setOnClickListener {
            vb.updateAppsUrl.setText(null)
        }
        vb.buttonUpdateAppsDropbox.setOnClickListener {
            vb.updateAppsUrl.setText(UPDATE_APPS_URL_DROPBOX)
        }
        vb.useTextureViewButton.setOnClickListener {
            vb.useTextureViewButton.isSelected = !vb.useTextureViewButton.isSelected
            updateTextureViewButtonText()
        }

        vb.mac.hint = BaseApp.sharedSettings.defaultMac
        vb.server.hint = BaseApp.sharedSettings.defaultServer
        vb.theme.hint = BaseApp.sharedSettings.defaultTheme
        vb.updateAppsUrl.hint = BaseApp.sharedSettings.defaultUpdateAppsUrl

        vb.mac.setText(BaseApp.sharedSettings.mac)
        vb.server.setText(BaseApp.sharedSettings.server)
        vb.theme.setText(BaseApp.sharedSettings.theme)
        vb.updateAppsUrl.setText(BaseApp.sharedSettings.updateAppsUrl)
        vb.useTextureViewButton.isSelected = ISharedSettingsStorage.isUseTextureViewInPlayer
        updateTextureViewButtonText()
    }

    override fun mapNameToTheme(name: String?): Int? {
        return when (name) {
            SharedSettingsContentResolverHelper.THEME_TEST -> R.style.Theme_OnTv_Test
            NetConsoleCompany.COLOR_PRESET_ONTV ->  R.style.Theme_OnTv_OnTV
            NetConsoleCompany.COLOR_PRESET_TvinTV ->  R.style.Theme_OnTv_TvinTV
            NetConsoleCompany.COLOR_PRESET_BirLinkTV ->  R.style.Theme_OnTv_BirLink
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        vb.buttonSetDefaultMac.requestFocus()
    }

    override fun onBackPressed() {
        val mac = vb.mac.text.toString().takeIf { it.isNotBlank() }?.toUpperCase()
        val server = vb.server.text.toString().takeIf { it.isNotBlank() }
        val theme = vb.theme.text.toString().takeIf { it.isNotBlank() }
        val updateAppsUrl = vb.updateAppsUrl.text.toString().takeIf { it.isNotBlank() }
        val macOrServerChanges = BaseApp.sharedSettings.mac != mac || BaseApp.sharedSettings.server != server
        if (macOrServerChanges ||
            BaseApp.sharedSettings.theme != theme ||
            BaseApp.sharedSettings.updateAppsUrl != updateAppsUrl ||
            vb.useTextureViewButton.isSelected != ISharedSettingsStorage.isUseTextureViewInPlayer) {
            AlertDialog.Builder(this).apply {
                setMessage("Apply changes before exit?")
                setCancelable(false)
                setPositiveButton("Apply") { dialog, id ->
                    dialog.cancel()
                    BaseApp.sharedSettings.mac = mac
                    BaseApp.sharedSettings.server = server
                    BaseApp.sharedSettings.theme = theme
                    BaseApp.sharedSettings.updateAppsUrl = updateAppsUrl
                    BaseApp.sharedSettings.setUseTextureViewInPlayerTimestamp = if (vb.useTextureViewButton.isSelected) System.currentTimeMillis() else null

                    if (macOrServerChanges) {
                        GlobalScope.launch(AuthorizedUser.mainOpsDispatcher) {
                            AuthorizedUser.logout()
                        }
                    }
                    finish()
                }
                setNegativeButton("No, exit without changes") { dialog, id ->
                    dialog.cancel()
                    finish()
                }
            }.create().show()
        } else {
            finish()
        }
    }

    fun updateTextureViewButtonText() {
        vb.useTextureViewButton.apply {
            text = if (isSelected) "TextureView in player is ON(screenshots ok)" else "TextureView in player is OFF(default)"
        }
    }
}