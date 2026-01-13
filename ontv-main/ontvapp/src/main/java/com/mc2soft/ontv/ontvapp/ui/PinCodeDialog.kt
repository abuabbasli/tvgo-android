package com.mc2soft.ontv.ontvapp.ui

import android.animation.TimeInterpolator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mc2soft.ontv.common.ui.BaseDialog
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.databinding.PinCodeDialogBinding
import kotlinx.coroutines.*


abstract class PinCodeDialog : BaseDialog() {
    lateinit var vb: PinCodeDialogBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        vb = PinCodeDialogBinding.inflate(inflater, container, false)
        return vb.root
    }

    var pin: String = ""
        set(v) {
            field = v
            update()
        }

    protected var actionScope: CoroutineScope? = null
    protected var actionJob: Job? = null

    var onCancel: (()->Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        actionScope = CoroutineScope(Dispatchers.IO)
        vb.title.visible(false)
        vb.btn0.setOnClickListener {
            plus(0)
        }
        vb.btn1.setOnClickListener {
            plus(1)
        }
        vb.btn2.setOnClickListener {
            plus(2)
        }
        vb.btn3.setOnClickListener {
            plus(3)
        }
        vb.btn4.setOnClickListener {
            plus(4)
        }
        vb.btn5.setOnClickListener {
            plus(5)
        }
        vb.btn6.setOnClickListener {
            plus(6)
        }
        vb.btn7.setOnClickListener {
            plus(7)
        }
        vb.btn8.setOnClickListener {
            plus(8)
        }
        vb.btn9.setOnClickListener {
            plus(9)
        }
        vb.btnBack.setOnClickListener {
            if (pin.length > 0) {
                pin = pin.substring(0, pin.length - 1)
                update()
            }
        }
        vb.btnOk.setOnClickListener {
            if (pin.length == 4) {
                onOk()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope?.cancel()
        actionScope?.cancel()
        actionScope = null
        actionJob = null
    }

    override fun onResume() {
        super.onResume()
        update()
        vb.btn5.requestFocus()
    }

    override fun onBackPressed(): Boolean {
        val ret = super.onBackPressed()
        onCancel?.invoke()
        return ret
    }

    fun plus(i: Int) {
        if (pin.length < 4) {
            pin += i.toString()
        }
    }

    fun update() {
        val l = pin.length
        vb.pin1.isSelected = l >= 1
        vb.pin2.isSelected = l >= 2
        vb.pin3.isSelected = l >= 3
        vb.pin4.isSelected = l >= 4
        vb.btnOk.isEnabled = l == 4
    }

    abstract fun onOk()

    fun setActionResultTexts(title: String?, err: String?, dropPin: Boolean) {
        scope?.launch {
            vb.title.text = title
            vb.title.visible(title != null)

            if (err != null) {
                val FREQ = 3f
                val DECAY = 2f
                // interpolator that goes 1 -> -1 -> 1 -> -1 in a sine wave pattern.
                // interpolator that goes 1 -> -1 -> 1 -> -1 in a sine wave pattern.
                val decayingSineWave = TimeInterpolator { input ->
                    val raw = Math.sin(FREQ * input * 2 * Math.PI)
                    (raw * Math.exp((-input * DECAY).toDouble())).toFloat()
                }

                vb.pins.animate()
                    .xBy(-100.0f)
                    .setInterpolator(decayingSineWave)
                    .setDuration(500)
                    .start()
            }

            if (dropPin) {
                if (vb.btnOk.hasFocus()) {
                    vb.btn5.requestFocus()
                }
                pin = ""
            }
        }
    }
}