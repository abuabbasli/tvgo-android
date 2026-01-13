package com.mc2soft.ontv.common.stalker_portal.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface IStalkerPortalNetApi {
    @POST("/stalker_portal/server/load.php?type=stb&action=handshake&JsHttpRequest=1-xml")
    suspend fun getToken(@Header("Cookie") cookie: String): Response<String>

    @POST("/stalker_portal/server/load.php?type=stb&action=get_profile&JsHttpRequest=1-xml")
    suspend fun getProfile(@Header("Authorization") auth: String, @Header("Cookie") cookie: String): Response<String>

    @GET("/stalker_portal/server/load.php?type=itv&action=get_all_channels&JsHttpRequest=1-xml")
    suspend fun getChannels(@Header("Authorization") auth: String, @Header("Cookie") cookie: String): Response<String>

    @GET("/stalker_portal/server/load.php?type=itv&action=create_link&JsHttpRequest=1-xml")
    suspend fun getVideoStreamUrl(@Header("Authorization") auth: String, @Header("Cookie") cookie: String, @Query("cmd") cmd: String): Response<String>

    @GET("/stalker_portal/server/load.php?action=get_ordered_list&type=vod&JsHttpRequest=1-xml&category=*&hd=0&sortby=added&p=1&video=all&not_ended=0")
    suspend fun getMovieOrSeriesElements(@Header("Authorization") auth: String, @Header("Cookie") cookie: String, @Query("movie_id") movie_id: Int): Response<String>

    @GET("/stalker_portal/server/load.php?action=get_ordered_list&type=vod&JsHttpRequest=1-xml&category=*&hd=0&sortby=added&p=1&video=all&not_ended=0")
    suspend fun getSeriesSeasonEpisodes(@Header("Authorization") auth: String, @Header("Cookie") cookie: String, @Query("movie_id") movie_id: Int, @Query("season_id") season_id: Int): Response<String>

    @GET("/stalker_portal/server/load.php?action=get_ordered_list&type=vod&JsHttpRequest=1-xml&category=*&hd=0&sortby=added&p=1&video=all&not_ended=0")
    suspend fun getSeriesSeasonEpisodeVOD(@Header("Authorization") auth: String, @Header("Cookie") cookie: String, @Query("movie_id") movie_id: Int, @Query("season_id") season_id: Int, @Query("episode_id") episode_id: Int): Response<String>


    @GET("/stalker_portal/server/load.php?type=itv&action=get_short_epg&size=42&JsHttpRequest=1-xml")
    suspend fun getChannelPrograms(@Header("Authorization") auth: String, @Header("Cookie") cookie: String, @Query("ch_id") ch_id: Int): Response<String>

    @GET("/stalker_portal/server/load.php?action=get_simple_data_table&type=epg&JsHttpRequest=1-xml")
    suspend fun getChannelProgramsByDay(@Header("Authorization") auth: String, @Header("Cookie") cookie: String, @Query("ch_id") ch_id: Int, @Query("date") date: String, @Query("p") p: Int = 1): Response<String>


    @GET("/stalker_portal/server/load.php?type=itv&action=get_genres&size=12&JsHttpRequest=1-xml")
    suspend fun getTVGenres(@Header("Authorization") auth: String, @Header("Cookie") cookie: String): Response<String>

    @GET("/stalker_portal/server/load.php?type=vod&action=get_categories&JsHttpRequest=1-xml")
    suspend fun getMovieCategories(@Header("Authorization") auth: String, @Header("Cookie") cookie: String): Response<String>


    @GET("/stalker_portal/server/load.php?type=vod&action=get_ordered_list&JsHttpRequest=1-xml")
    suspend fun getMovieOrderedList(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                    @Query("category") category: String, @Query("hd") hd: Int,
                                    @Query("sortby") sortby: String, @Query("p") p: Int,
                                    @Query("video") video: String, @Query("not_ended") not_ended: Int, @Query("fav") fav: Int? = null, @Query("search") search: String? = null): Response<String>

    @GET("/stalker_portal/server/load.php?type=vod&action=get_genres_by_category_alias&JsHttpRequest=1-xml")
    suspend fun getMovieCategoriesByAlias(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                          @Query("cat_alias") cat_alias: String): Response<String>

    @GET("/stalker_portal/server/load.php?type=vod&action=get_years&JsHttpRequest=1-xml")
    suspend fun getMovieYears(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                              @Query("category") category: String): Response<String>

    @GET("/stalker_portal/server/load.php?type=vod&action=get_abc&JsHttpRequest=1-xml")
    suspend fun getMovieABC(@Header("Authorization") auth: String, @Header("Cookie") cookie: String): Response<String>

    @POST("/stalker_portal/server/load.php?type=stb&action=set_parent_password&JsHttpRequest=UNIXTIME-xml")
    suspend fun setParentalPassword(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                            @Query("parent_password") parent_password: String, @Query("pass") pass: String, @Query("repeat_pass") repeat_pass: String): Response<String>

    @POST("/stalker_portal/server/load.php?action=set_fav&type=vod&JsHttpRequest=1-xml")
    suspend fun addMovieToFav(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                    @Query("video_id") video_id: Int): Response<String>

    @POST("/stalker_portal/server/load.php?action=del_fav&type=vod&JsHttpRequest=1-xml")
    suspend fun removeMovieFromFav(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                    @Query("video_id") video_id: Int): Response<String>

    @POST("/stalker_portal/server/load.php?action=set_fav&type=itv&JsHttpRequest=1-xml")
    suspend fun setFavoriteChannelList(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                            @Query("fav_ch", encoded = true) fav_ch: String): Response<String>

    @GET("/stalker_portal/server/load.php?type=tv_archive&action=create_link&series=&forced_storage=&disable_ad=0&download=0&JsHttpRequest=1-xml")
    suspend fun getProgramVOD(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                       @Query("cmd") cmd: String): Response<String>

    @GET("/stalker_portal/server/load.php?type=watchdog&action=get_events&cur_play_type=1&event_active_id=0&init=0&JsHttpRequest=1-xml")
    suspend fun getMessages(@Header("Authorization") auth: String, @Header("Cookie") cookie: String): Response<String>

    @POST("/stalker_portal/server/load.php?type=watchdog&action=confirm_event&JsHttpRequest=1-xml")
    suspend fun maskMessageRead(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                            @Query("event_active_id") event_active_id: Int): Response<String>



    //analytics
    @POST("/stalker_portal/server/load.php?type=stb&action=log&real_action=play&param=%5Bobject%20Object%5D&tmp_type=1&streamer_id=0&JsHttpRequest=1-xml")
    suspend fun analyticsOnChannelStartPlay(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                            @Query("content_id") content_id: Int,
                                            @Query("link_id") link_id: Int,
                                            @Query("ch_id") ch_id: Int): Response<String>

    @POST("/stalker_portal/server/load.php?type=stb&action=log&real_action=play&tmp_type=1&JsHttpRequest=1-xml")
    suspend fun analyticsOnChannelStartPlayMulticast(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                            @Query("content_id") content_id: Int,
                                            @Query("param") param: String): Response<String>


    @POST("/stalker_portal/server/load.php?action=set_played&type=itv&JsHttpRequest=1-xml&censored=false")
    suspend fun analyticsOnChannelTryGetLink(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                            @Query("itv_id") itv_id: Int): Response<String>

    @POST("/stalker_portal/server/load.php?type=itv&action=set_last_id&JsHttpRequest=1-xml")
    suspend fun analyticsSetLastPlayedChannel(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                               @Query("id") id: Int): Response<String>


    @POST("/stalker_portal/server/load.php?action=set_ended&type=vod&JsHttpRequest=1-xml")
    suspend fun analyticsOnVodPlaybackEnded(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                                       @Query("video_id") video_id: Int): Response<String>


    @POST("/stalker_portal/server/load.php?action=set_not_ended&type=vod&JsHttpRequest=1-xml")
    suspend fun analyticsOnVodPlaybackNotEnded(@Header("Authorization") auth: String, @Header("Cookie") cookie: String,
                                            @Query("video_id") video_id: Int,
                                            @Query("end_time") end_time: Int,
                                            @Query("file_id") file_id: Int): Response<String>

}