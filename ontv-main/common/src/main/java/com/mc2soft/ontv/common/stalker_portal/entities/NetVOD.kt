package com.mc2soft.ontv.common.stalker_portal.entities

@kotlinx.serialization.Serializable
data class NetVOD(
    val id : Int,
    val video_id : Int? = null,
    val name : String? = null,
    val cmd : String? = null,
    val url : String? = null,
    val languages : String? = null) {
    fun getPlaybackUrl(): String? {
        return url ?: cmd
    }
}

@kotlinx.serialization.Serializable
data class NetVODList(
    //val total_items: Int = 0,
    //val max_page_items: Int = 0,
    //val selected_item: Int = 0,
    //val cur_page: Int = 0,
    val data: List<NetVOD> = emptyList<NetVOD>()
)

//{"id":"13","video_id":"13","series_id":"1","file_type":"video","protocol":"custom","url":"http:\/\/vod.birlink.az\/vod\/SERIAL\/SHELDON\/S_1\/YS_1.mp4","file_name":"","languages":"a:1:{i:0;s:2:\"ru\";}","quality":"1","volume_level":"0","accessed":"1","status":"1","date_add":"2021-03-16 00:12:05","date_modify":"2021-03-16 00:12:05","tmp_link_type":"","for_rent":0,"name":"Russian \/ Regular quality (240)","is_file":true,"cmd":"http:\/\/vod.birlink.az\/vod\/SERIAL\/SHELDON\/S_1\/YS_1.mp4"}]},"text":"generated in: 0.019s; query counter: 13; cache hits: 0; cache miss: 0; php errors: 0; sql errors: 0;"}