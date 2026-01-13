package com.mc2soft.ontv.common.stalker_portal.entities

import com.mc2soft.ontv.common.stalker_portal.api.StalkerPortalNetApi

@kotlinx.serialization.Serializable
data class NetChannel(
    val id: Int,
    val name: String? = null,
    val number: Int? = null,
    val cmd: String? = null,
    val censored: String? = null,
    val tv_genre_id: String? = null,
    //val base_ch: Int = 0,
    //val hd: Int = 0,
    val fav: Int = 0,
    //val archive: Int = 0,
    //val genres_str:String = "",
    //val open: Int = 1,
    //val pvr: Int = 0,
    //val cur_playing: String? = null,
    val xmltv_id: String? = null,
    val tv_archive_type: String? = null) {

    val tileImageUrl: String
        get() = "${StalkerPortalNetApi.serverUrlWithEndSlash}stalker_portal/misc/logos/120/$id.png"

    val playerLogoUrl: String
        get() =  "${StalkerPortalNetApi.serverUrlWithEndSlash}stalker_portal/misc/logos/160/$id.png"

    val isHavePrograms: Boolean
        get() = xmltv_id?.isNotEmpty() == true

    val isHaveArchive: Boolean
        get() = tv_archive_type?.isNotEmpty() == true &&
                //use archives from epg
                (!isMulticast || isHavePrograms)

    val isMulticast: Boolean
        get() {
            val c = cmdFixed
            return c?.startsWith("udp:") == true ||
                    c?.startsWith("rtp udp:") == true
        }

    val multicastLink: String?
        get() = cmdFixed?.let {
            val pref = "rtp "
            if (it.startsWith(pref))
                it.substring(pref.length)
            else
                it
        }?.replace("@", "")

    val cmdFixed: String?
        get() = cmd?.takeIf { it.isNotBlank() } ?: getChannelsCmdCacheCallback?.invoke(id)

    val playableUrl: String?
        get() = cmd?.takeIf { it.isNotBlank() && !it.contains("localhost") }

    val isCensored: Boolean
        get() = censored == "1"

    val isFav: Boolean
        get() = fav == 1

    val isValidResponse: Boolean
        get() = name?.isNotBlank() == true && cmd?.isNotBlank() == true && number != null

    companion object {
        var getChannelsCmdCacheCallback: ((id: Int)->String?)? = null
    }
}

@kotlinx.serialization.Serializable
data class NetChannelsList(
    //val total_items: Int = 0,
    //val max_page_items: Int = 0,
    //val selected_item: Int = 0,
    //val cur_page: Int = 0,
    val data: List<NetChannel> = emptyList()
)