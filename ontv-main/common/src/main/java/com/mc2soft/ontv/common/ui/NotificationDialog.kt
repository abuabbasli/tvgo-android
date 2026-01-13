package com.mc2soft.ontv.common.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mc2soft.ontv.common.R
import com.mc2soft.ontv.common.databinding.NotificationDlgBinding

open class NotificationDialog : BaseDialog() {
    private lateinit var vb: NotificationDlgBinding
        private set


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        vb = NotificationDlgBinding.inflate(inflater, container, false)
        vb.button1.visible(false)
        vb.button2.visible(false)
        vb.button3.visible(false)
        return vb.root
    }

    fun addButton(text: String, cb: View.OnClickListener) {
        val bt: TextView = if (vb.button3.isVisible) return
            else if (vb.button2.isVisible) vb.button3
            else if (vb.button1.isVisible) vb.button2
            else vb.button1
        bt.text = text
        bt.setOnClickListener(cb)
        bt.visible(true)
    }

    fun setStyleInfo() {
        vb.icon.setImageResource(R.drawable.outline_info_224)
    }

    fun setStyleQuestion() {
        vb.icon.setImageResource(R.drawable.sharp_question_mark_224)
    }

    fun setTexts(title: String?, message: String?) {
        vb.title.text = title
        vb.message.text = message
    }

    override fun onResume() {
        super.onResume()
        vb.button1.requestFocus()
    }
}