package com.mc2soft.ontv.common.stalker_portal.entities

@kotlinx.serialization.Serializable
data class NetChannelLink(val cmd: String, val link_id: Int? = null) {
    val fixedLink: String?
        get() {
            if (cmd.isBlank())return null
            return cmd.lastIndexOf(' ').takeIf { it >= 0 }?.let { lastIndex->
                cmd.substring(lastIndex)
            } ?: cmd
        }

    private fun insertToPathBeforeDotExt(url: String, lastPath: String): String {
        val src = android.net.Uri.parse(url)
        val newPath = StringBuilder()
        src.pathSegments.dropLast(1).forEach {
            newPath.append(it)
            newPath.append("/")
        }
        newPath.append(lastPath)
        val lastSrcPath = src.lastPathSegment
        lastSrcPath?.lastIndexOf('.')?.takeIf { it >= 0 }?.let { dotIndex->
            newPath.append(lastSrcPath.substring(dotIndex))
        }
        return android.net.Uri.parse(url).buildUpon().apply {
            path(newPath.toString())
        }.toString()
    }

    fun tuneLink(startTimeSec: Long? = null, durationSec: Long? = null, liveOffsetSec: Long? = null): String? {
        var url = fixedLink ?: return null
        startTimeSec?.let { startTimeSec->
            durationSec?.let { durationSec ->
                url = insertToPathBeforeDotExt(url, "archive-$startTimeSec-$durationSec")
            } ?: run {
                url = insertToPathBeforeDotExt(url, "timeshift_abs-$startTimeSec")
            }
        } ?: liveOffsetSec?.let { liveOffsetSec->
            url = insertToPathBeforeDotExt(url, "timeshift_rel-$liveOffsetSec")
        }
        return url
    }
}