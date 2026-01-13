package com.mc2soft.ontv.common.stalker_portal.entities

import android.net.Uri

@kotlinx.serialization.Serializable
data class NetProgramVOD(
    val id : Int,
    val cmd : String? = null,
    //val storage_id : String? = null,
    //val download_cmd : String? = null,
    //val to_file : String? = null
) {
    fun getChannelUrl(): String? {
        val uri = cmd?.let { Uri.parse(it) } ?: return null
        val channelUri = Uri.Builder().scheme(uri.scheme).appendPath(uri.host)
        uri.getQueryParameter("token")?.let {
            channelUri.appendQueryParameter("token", it)
        }
        for (i in 0 until uri.pathSegments.lastIndex) {
            channelUri.appendPath(uri.pathSegments.get(i))
        }
        val last = uri.lastPathSegment!!
        val tireIndex = last.indexOf("-")
        val dotIndex = last.lastIndexOf(".")
        channelUri.appendPath(
            if (tireIndex > 0 && dotIndex > 0 && dotIndex > tireIndex)
                last.substring(0, tireIndex) + last.substring(dotIndex)
            else last)
        return channelUri.build().toString()
    }
}

