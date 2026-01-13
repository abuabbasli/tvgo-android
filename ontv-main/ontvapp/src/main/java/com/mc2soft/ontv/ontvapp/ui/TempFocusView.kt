package com.mc2soft.ontv.ontvapp.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.*

class TempFocusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0) : View(context, attrs, defStyle) {

    private var unfocusJob: Job? = null

    fun tempFocus(): Boolean {
        isFocusable = true
        unfocusJob?.cancel()
        unfocusJob = GlobalScope.launch(Dispatchers.Main) {
            delay(300)
            isFocusable = false
            unfocusJob = null
        }
        return requestFocus()
    }
}