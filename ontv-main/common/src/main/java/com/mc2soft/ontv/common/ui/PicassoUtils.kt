package com.mc2soft.ontv.ontvapp.ui

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.mc2soft.ontv.common.BuildConfig
import com.squareup.picasso.Picasso
import timber.log.Timber

fun ImageView.load(url: String?, placeholder: Drawable?) {
    if (url == tag)return
    tag = url
    Picasso.get().load(url).apply {
        placeholder?.let {
            placeholder(it)
        }
    }.into(this)
    if (BuildConfig.DEBUG) {
        Timber.i(url)
    }
}

fun ImageView.load(url: String?, placeholder: Int) {
    if (url == tag)return
    tag = url
    Picasso.get().load(url).placeholder(placeholder).into(this)
    if (BuildConfig.DEBUG) {
        Timber.i(url)
    }
}

fun ImageView.load(url: String?) {
    if (url == tag)return
    tag = url
    Picasso.get().load(url).into(this)
    if (BuildConfig.DEBUG) {
        Timber.i(url)
    }
}