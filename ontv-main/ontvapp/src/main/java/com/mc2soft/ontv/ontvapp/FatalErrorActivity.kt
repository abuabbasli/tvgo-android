package com.mc2soft.ontv.ontvapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mc2soft.ontv.common.databinding.FatalErrorActivityBinding
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.ui.BaseFatalErrorActivity
import kotlinx.coroutines.launch

class FatalErrorActivity : BaseFatalErrorActivity() {
    companion object {
        const val INTENT_LOGOUT = "LOGOUT"
        fun launch(activity: Activity, title: String, msg: String, logout: Boolean) {
            activity.startActivity(addIntentParams(Intent(activity, FatalErrorActivity::class.java).apply {
                putExtra(INTENT_LOGOUT, logout)
            }, title, msg))
            activity.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(INTENT_LOGOUT, false) == true) {
            scope?.launch(AuthorizedUser.mainOpsDispatcher) {
                AuthorizedUser.logout()
            }
        }
    }
}