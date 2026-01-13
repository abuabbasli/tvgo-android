package com.mc2soft.ontv.common.ui

import android.os.Bundle
import android.view.View
import com.mc2soft.ontv.common.stalker_portal.NotificationMessages
import com.mc2soft.ontv.common.stalker_portal.entities.NetMessageData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StalkerPortalNotificationDialog : NotificationDialog() {
    var msg: NetMessageData? = null
        private set

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        closeByBackButton = false
        msg = NotificationMessages.netMessage.value?.data
        msg?.let {
            setStyleInfo()
            setTexts(null, it.msg)
            addButton(resources.getString(com.mc2soft.ontv.common.R.string.ok)) {
                NotificationMessages.markMessageReadOrExpire()
                dismiss()
            }
        }
    }

    private var autoCloseJob: Job? = null

    override fun onResume() {
        super.onResume()
        val msg = msg ?: run {
            dismiss()
            return
        }
        (msg.auto_hide_timeout?.takeIf { it > 0 } ?: msg.valid_until?.let { it - System.currentTimeMillis()/1000 }?.takeIf { it > 0 })?.let {
            autoCloseJob = scope?.launch {
                delay(it * 1000)
                NotificationMessages.markMessageReadOrExpire()
                dismiss()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoCloseJob?.cancel()
        autoCloseJob = null
    }

    companion object {
        const val TAG = "StalkerPortalNotificationDialog"
    }
}