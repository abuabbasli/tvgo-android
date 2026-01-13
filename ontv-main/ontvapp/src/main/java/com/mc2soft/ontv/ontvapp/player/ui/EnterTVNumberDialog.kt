package com.mc2soft.ontv.ontvapp.player.ui

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mc2soft.ontv.common.ui.BaseDialog
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.PlayerEnterTvNumberDlgBinding
import com.mc2soft.ontv.ontvapp.model.ChannelsCache

class EnterTVNumberDialog(val initialKeycode: Int) : BaseDialog()  {
    companion object {
        const val AUTO_CLOSE_DELAY = 2500L
        const val MAX_NUMBER_LEN = 3
    }

    var vb: PlayerEnterTvNumberDlgBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        vb = PlayerEnterTvNumberDlgBinding.inflate(layoutInflater)
        return vb!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (event?.action == KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener handleKeyCode(keyCode)
            }
            false
        }
        handleKeyCode(initialKeycode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vb = null
    }

    override fun onResume() {
        super.onResume()
        view?.requestFocus()
        autoCloseAfterDelay()
    }

    override fun onPause() {
        super.onPause()
        autoCloseHandler.removeCallbacksAndMessages(null)
    }

    fun handleKeyCode(keyCode: Int): Boolean {
        val text = vb!!.textNumber.text?.toString() ?: ""
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            if (text.length < MAX_NUMBER_LEN) {
                val n = keyCode - KeyEvent.KEYCODE_0
                vb!!.textNumber.text = text + n.toString()
                setOrdinaryStyle()
            }
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (text.isNotEmpty()) {
                vb!!.textNumber.text = text.substring(0, text.length - 1)
                setOrdinaryStyle()
            }
        } else if (keyCode >= KeyEvent.KEYCODE_DPAD_CENTER || keyCode >= KeyEvent.KEYCODE_ENTER) {
            openChannel()
        } else {
            return false
        }
        autoCloseAfterDelay()
        return true
    }

    private val autoCloseHandler = Handler()

    fun autoCloseAfterDelay() {
        val vb = vb ?: return
        autoCloseHandler.removeCallbacksAndMessages(null)
        autoCloseHandler.postDelayed( {
            if (vb.textError.visibility == View.VISIBLE) {
                dismiss()
            } else {
                openChannel()
                autoCloseAfterDelay()
            }
        }, AUTO_CLOSE_DELAY)
    }

    fun openChannel() {
        val vb = vb ?: return
        val text = vb.textNumber.text?.toString() ?: return
        if (text.isEmpty()) {
            dismiss()
            return
        }
        val number = Integer.parseInt(text)
        ChannelsCache.channels.value.data.find { it.number == number }?.let { ch->
            dismiss()
            MainActivity.get(context)?.openChannel(ch)
        } ?: run {
            setErrorView(resources.getString(R.string.channel_not_found))
        }
    }

    fun setErrorView(text: String) {
        vb?.textError?.text = text
        vb?.textError?.visible(true)
    }

    fun setOrdinaryStyle() {
        vb?.textError?.visible(false)
    }
}