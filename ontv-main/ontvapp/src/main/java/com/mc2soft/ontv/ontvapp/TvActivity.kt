package com.mc2soft.ontv.ontvapp

import android.os.Bundle
import com.mc2soft.ontv.common.run_helpers.RunOnTvActivityHelper
import com.mc2soft.ontv.common.ui.BaseActivity

class TvActivity: BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RunOnTvActivityHelper.run(this, false)
    }
}