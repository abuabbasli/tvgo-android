package com.mc2soft.ontv.ontvapp.ui

import android.os.Bundle
import android.view.View
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.ontvapp.R
import kotlinx.coroutines.launch

class PinCodeChangeDialog : PinCodeDialog() {
     enum class State {
        OldPin,
        NewPin,
        NewPinAgain
    }
    private var state: State = State.OldPin

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setActionResultTexts(null, true)
    }

    private var newPin: String? = null

    override fun onOk() {
        if (actionJob?.isActive == true)return
        val pin = pin
        actionJob = actionScope?.launch {
            when (state) {
                State.OldPin -> {
                    if (AuthorizedUser.isValidParentalPassword(pin)) {
                        state = State.NewPin
                        setActionResultTexts(null, true)
                    } else {
                        setActionResultTexts("wrong pin", true)
                    }
                }
                State.NewPin -> {
                    newPin = pin
                    state = State.NewPinAgain
                    setActionResultTexts(null, true)
                }
                State.NewPinAgain -> {
                    if (newPin == pin) {
                        AuthorizedUser.setParentalPassword(pin)
                        scope?.launch {
                            dismiss()
                        }
                    } else {
                        state = State.NewPin
                        setActionResultTexts("not same pin, try again", true)
                    }
                }
            }
        }
    }

    fun setActionResultTexts(err: String?, dropPin: Boolean) {
        setActionResultTexts(when (state) {
            State.OldPin -> resources.getString(R.string.pin_code_dlg_enter_old_pin)
            State.NewPin -> resources.getString(R.string.pin_code_dlg_enter_new_pin)
            State.NewPinAgain -> resources.getString(R.string.pin_code_dlg_enter_new_pin_again)
        }, err, dropPin)
    }
}