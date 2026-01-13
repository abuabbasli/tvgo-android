package com.mc2soft.ontv.common.stalker_portal.entities

import com.mc2soft.ontv.common.stalker_portal.api.StalkerPortalNetApi

@kotlinx.serialization.Serializable
data class NetMovieOrSeries(
    val id : Int,
    val name : String,
    //val owner : String? = null,
    //val old_name : String? = null,
    //val o_name : String? = null,
    //val fname : String? = null,
    val description : String? = null,
    //val pic : String? = null,
    //val cost : String? = null,
    val time : String? = null,
    //val file : String? = null,
    //val path : String? = null,
    //val protocol : String? = null,
    //val rtsp_url : String? = null,
    val censored : String? = null,
    //val volume_correction : String? = null,
    //val category_id : String? = null,
    //val genre_id : String? = null,
    //val genre_id_1 : String? = null,
    //val genre_id_2 : String? = null,
    //val genre_id_3 : String? = null,
    //val genre_id_4 : String? = null,
    //val cat_genre_id_1 : String? = null,
    //val cat_genre_id_2 : String? = null,
    //val cat_genre_id_3 : String? = null,
    //val cat_genre_id_4 : String? = null,
    val director : String? = null,
    val actors : String? = null,
    val year : String? = null,
    //val accessed : String? = null,
    //val status : String? = null,
    //val disable_for_hd_devices : String? = null,
    //val added : String? = null,
    //val count : String? = null,
    //val count_first_0_5 : String? = null,
    //val count_second_0_5 : String? = null,
    //val vote_sound_good : String? = null,
    //val vote_sound_bad : String? = null,
    //val vote_video_good : String? = null,
    //val vote_video_bad : String? = null,
    //val rate : String? = null,
    //val last_rate_update : String? = null,
    //val last_played : String? = null,
    //val for_sd_stb : String? = null,
    val kinopoisk_id : String? = null,
    val rating_kinopoisk : String? = null,
    val rating_count_kinopoisk : String? = null,
    val rating_imdb : String? = null,
    val rating_count_imdb : String? = null,
    val rating_last_update : String? = null,
    val age : String? = null,
    val rating_mpaa : String? = null,
    //val high_quality : String? = null,
    //val comments : String? = null,
    //val low_quality : Int = 0,
    val country : String? = null,
    val is_series : String? = null,
    val year_end : String? = null,
    //val autocomplete_provider : String? = null,
    //val screenshots : String? = null,
    //val is_movie : Boolean = true,
    //val lock : Int = 0,
    val fav : Int = 0,
    //val for_rent : Int = 0,
    //val has_files : Int = 0,
    val screenshot_uri : String? = null,
    val genres_str : String? = null,
    //val cmd : String? = null,
    //val url : String? = null,
    //val week_and_more : String? = null
    ) {
    val tileImageUrl: String?
        get() = screenshot_uri?.let { StalkerPortalNetApi.serverUrlWithEndSlash + it }

    val posterUrl: String?
        get() = tileImageUrl

    val playerLogoUrl: String?
        get() = tileImageUrl

    val isCensored: Boolean
        get() = censored == "1"

    val isFav: Boolean
        get() = fav == 1

    val isSeries: Boolean
        get() = is_series == "1"

    val isMovie: Boolean
        get() = !isSeries
}

@kotlinx.serialization.Serializable
data class NetMovieOrSeriesList(
    val total_items: Int = 0,
    //val max_page_items: Int = 0,
    //val selected_item: Int = 0,
    //val cur_page: Int = 0,
    val data: List<NetMovieOrSeries> = emptyList()
)