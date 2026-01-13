package com.mc2soft.ontv.common.ui

import android.os.Bundle
import android.view.View
import com.mc2soft.ontv.common.R

open class ErrorDialog : UserDialog() {
    var title: String = ""
    var message: String = ""
    var fatal = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStyleError()
        setTexts(title, message)
        addButton(resources.getString(R.string.ok)) {
            if (fatal)
                activity?.finish()
            dismiss()
        }
    }
}