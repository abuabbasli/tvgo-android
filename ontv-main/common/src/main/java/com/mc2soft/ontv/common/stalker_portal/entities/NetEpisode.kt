package com.mc2soft.ontv.common.stalker_portal.entities

@kotlinx.serialization.Serializable
data class NetEpisode(
    val id : Int,
    val name : String? = null) {
}

@kotlinx.serialization.Serializable
data class NetEpisodeList(
    //val total_items: Int = 0,
    //val max_page_items: Int = 0,
    //val selected_item: Int = 0,
    //val cur_page: Int = 0,
    val data: List<NetEpisode> = emptyList<NetEpisode>()
)