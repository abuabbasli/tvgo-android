package com.mc2soft.ontv.ontvapp.player.ui

import android.os.Bundle
import android.view.View
import com.mc2soft.ontv.common.ui.NotificationDialog
import com.mc2soft.ontv.ontvapp.R

open class UseOrNotSavedPositionDialog : NotificationDialog() {
    var callback: ((useSaved: Boolean)->Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStyleInfo()
        setTexts(resources.getString(R.string.use_saved_or_not_dlg_title), resources.getString(R.string.use_saved_or_not_dlg_message))
        addButton(resources.getString(R.string.use_saved_or_not_dlg_button_saved)) {
            callback?.invoke(true)
            dismiss()
        }
        addButton(resources.getString(R.string.use_saved_or_not_dlg_button_begin)) {
            callback?.invoke(false)
            dismiss()
        }
    }
}