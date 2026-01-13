package com.mc2soft.ontv.common.ui

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

open class BaseDialog : DialogFragment() {
    var scope: CoroutineScope? = null
        private set

    var closeByBackButton: Boolean = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        scope = CoroutineScope(Dispatchers.Main)
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog?.setOnKeyListener { dlg, keyCode, event ->
            this@BaseDialog.onKey(dlg, keyCode, event)
        }
    }

    open fun onKey(dlg: DialogInterface?, keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) && event?.action == KeyEvent.ACTION_DOWN) {
            return onBackPressed()
        }
        return false
    }

    open fun onBackPressed(): Boolean {
        if (closeByBackButton) {
            dismiss()
        }
        return true
    }

    override fun show(fm: FragmentManager, tag: String?) {
        show(fm.beginTransaction().addToBackStack(tag), tag)
    }
}