package com.mc2soft.ontv.ontvapp

import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.settings.ISharedSettingsStorage
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper
import com.mc2soft.ontv.common.settings.SharedSettingsStorage
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.api.StalkerPortalNetApi
import com.mc2soft.ontv.common.ui.BaseActivity
import com.mc2soft.ontv.ontvapp.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.SocketTimeoutException


class OnTVApp : BaseApp("7a3a772e-7c56-4154-a2b4-3e1fed320a7a") {
    companion object {
        val inst: OnTVApp
            get() = instance as OnTVApp

        val isStandalone: Boolean
            get() = BuildConfig.FLAVOR == "standalone"
    }

    init {
        AuthorizedUser.clearCachesCb = {
            UserLocalData.inst.clear()
            ChannelsCache.clear()
            MoviesCache.clear()
            FavoriteChannelsCache.clear()
            FavoriteMoviesCache.clear()
        }
    }

    override fun createSharedSettings(): ISharedSettingsStorage {
        if (isStandalone)
            return SharedSettingsStorage()
        else
            return SharedSettingsContentResolverHelper
    }

    override fun handleError(ex: java.lang.Exception, fatal: Boolean?, show: Boolean?) {
        logError(ex)

        when (ex) {
            is StalkerPortalNetApi.UnauthorizedException -> {
                if (ex.auth != null && ex.auth == AuthorizedUser.auth) {
                    (BaseActivity.activeActivity as? MainActivity)?.startedScope?.let {
                        it.launch(AuthorizedUser.mainOpsDispatcher) {
                            AuthorizedUser.logout(false)
                            try {
                                AuthorizedUser.startup(true, true)
                            } catch (ex: Exception) {
                                logError(ex)
                            }
                        }
                        if (BuildConfig.DEBUG) {
                            showErrorDialog(ex, false)
                        }
                    }
                }
            }
            is StalkerPortalNetApi.AutoRegistrationDisabledContactProviderException -> {
                if (ex.auth != null && ex.auth == AuthorizedUser.auth) {
                    showFatalErrorActivity("AutoRegistrationDisabledContactProviderException", ex.data.block_msg, true)
                }
            }
            is StalkerPortalNetApi.AccessDeniedException -> {
                if (ex.auth != null && ex.auth == AuthorizedUser.auth) {
                    showFatalErrorActivity(ex, true)
                }
            }
            else -> if (ex !is CancellationException) {
                if (fatal == true && show == false) {
                    showFatalErrorActivity("", "", false)
                } else if (show != false) {
                    if (fatal == true) {
                        showFatalErrorActivity(ex, false)
                    } else {
                        showErrorDialog(ex, false)
                    }
                }
            }
        }
    }

    fun showFatalErrorActivity(ex: java.lang.Exception, logout: Boolean) {
        if (ex is StalkerPortalNetApi.NetException) {
            showFatalErrorActivity(ex.userTitle, ex.userMessage, logout)
        } else {
            showFatalErrorActivity(ex.javaClass.simpleName, ex.message ?: "", logout)
        }
    }

    fun showErrorDialog(ex: java.lang.Exception, fatal: Boolean) {
        if (ex is SocketTimeoutException){
            Timber.e("ErrorMsg ${ex.javaClass} ${ex.cause} ${ex.message}")
        } else {
            if (ex is StalkerPortalNetApi.NetException) {
                showErrorDialog(ex.userTitle, ex.userMessage, fatal)
            } else {
                showErrorDialog(ex.javaClass.simpleName, ex.message ?: "", fatal)
            }
        }
    }

    fun showFatalErrorActivity(title: String, msg: String, logout: Boolean) {
        GlobalScope.launch(Dispatchers.Main) {
            BaseActivity.activeActivity?.let { act ->
                if (act !is FatalErrorActivity) {
                    FatalErrorActivity.launch(act, title, msg, logout)
                }
            }
        }
    }
}