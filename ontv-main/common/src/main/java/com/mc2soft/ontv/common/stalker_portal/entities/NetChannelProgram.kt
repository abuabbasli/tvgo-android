package com.mc2soft.ontv.common.stalker_portal.entities

@kotlinx.serialization.Serializable
data class NetChannelProgram(
    val id: Int,
    //val ch_id: Int,
    //val time: String,
    //val time_to: String,
    //val duration: Int = 0,
    val name: String = "",
    //val descr: String = "",
    //val real_id: String = "",
    //val category: String = "",
    //val director: String = "",
    //val actor: String = "",
    val start_timestamp: Long = 0,
    val stop_timestamp: Long = 0,
    //val t_time: String = "",
    //val t_time_to: String = "",
    //val mark_memo: Int = 0,
    //val mark_archive: Int = 0
    ) {

    val startTimeMS: Long
        get() = start_timestamp * 1000

    val stopTimeMS: Long
        get() = stop_timestamp * 1000

    val durationTimeMS: Long
        get() = (stop_timestamp - start_timestamp) * 1000

    val isLive: Boolean
        get() = System.currentTimeMillis() in startTimeMS .. stopTimeMS

    val isWasStarted: Boolean
        get() = System.currentTimeMillis() > startTimeMS
}

@kotlinx.serialization.Serializable
data class NetChannelProgramList(
    //val total_items: Int = 0,
    //val max_page_items: Int = 0,
    //val selected_item: Int = 0,
    //val cur_page: Int = 0,
    val data: List<NetChannelProgram> = emptyList()
)



