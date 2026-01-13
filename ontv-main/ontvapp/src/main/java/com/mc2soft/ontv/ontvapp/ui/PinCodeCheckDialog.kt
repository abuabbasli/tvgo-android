package com.mc2soft.ontv.ontvapp.ui

import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import kotlinx.coroutines.launch

class PinCodeCheckDialog : PinCodeDialog {
    constructor()

    private var onSuccess: (()->Unit)? = null
    constructor(cb: ()->Unit) {
        onSuccess = cb
    }
    override fun onOk() {
        if (actionJob?.isActive == true)return
        val pin = pin
        actionJob = actionScope?.launch {
            if (AuthorizedUser.isValidParentalPassword(pin)) {
                scope?.launch {
                    dismiss()
                    onSuccess?.invoke()
                }
            } else {
                setActionResultTexts(null, "wrong pin", true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (onSuccess == null)
            dismiss()
    }
}