package com.mc2soft.ontv.common.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mc2soft.ontv.common.databinding.FatalErrorActivityBinding

abstract class BaseFatalErrorActivity : BaseActivity() {
    companion object {
        const val INTENT_TITLE = "TITLE"
        const val INTENT_MSG = "MSG"

        fun addIntentParams(intent: Intent, title: String, msg: String): Intent {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.putExtra(INTENT_TITLE, title)
            intent.putExtra(INTENT_MSG, msg)
            return intent
        }
    }

    private lateinit var vb: FatalErrorActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vb = FatalErrorActivityBinding.inflate(layoutInflater)

        vb.text.isFocusable = true
        vb.text.isFocusableInTouchMode = true
        vb.text.setOnClickListener {
            finish()
        }

        setContentView(vb.root)
    }

    override fun onStart() {
        super.onStart()
        vb.title.text = intent?.getStringExtra(INTENT_TITLE)
        intent?.getStringExtra(INTENT_MSG)?.takeIf {it.isNotEmpty() }?.let { msg->
            vb.text.text = msg
        } ?: run {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        vb.text.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}