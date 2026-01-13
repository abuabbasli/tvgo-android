package com.mc2soft.ontv.update_applications

import com.mc2soft.ontv.common.BaseApp
import kotlinx.coroutines.CancellationException

class UpdateApplicationsApp : BaseApp("53e0e808-1952-4965-b85d-46e9719ab98d") {
    companion object {
        val inst: UpdateApplicationsApp
            get() = instance as UpdateApplicationsApp
    }

    override fun handleError(ex: java.lang.Exception, fatal: Boolean?, show: Boolean?) {
        logError(ex)
        if (ex !is CancellationException)
            showErrorDialog(ex.javaClass.simpleName,ex.message ?: "error", true)
    }
}