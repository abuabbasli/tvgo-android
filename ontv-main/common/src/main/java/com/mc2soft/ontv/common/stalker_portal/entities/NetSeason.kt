package com.mc2soft.ontv.common.stalker_portal.entities

@kotlinx.serialization.Serializable
data class NetSeason(
    val id : Int,
    val name : String? = null,
    val video_id : String? = null,
    val season_number : Int? = null,
    val season_name : String? = null,
    val season_original_name : String? = null,
    val season_series : Int? = null,
    val is_season : Boolean = true) {
}

@kotlinx.serialization.Serializable
data class NetSeasonsList(
    //val total_items: Int = 0,
    //val max_page_items: Int = 0,
    //val selected_item: Int = 0,
    //val cur_page: Int = 0,
    val data: List<NetSeason> = emptyList<NetSeason>()
)