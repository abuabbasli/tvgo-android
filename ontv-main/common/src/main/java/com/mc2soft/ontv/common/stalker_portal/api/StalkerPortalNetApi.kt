package com.mc2soft.ontv.common.stalker_portal.api

import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.BuildConfig
import com.mc2soft.ontv.common.stalker_portal.AuthData
import com.mc2soft.ontv.common.stalker_portal.entities.*
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.joda.time.DateTime
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import timber.log.Timber
import java.text.SimpleDateFormat
import kotlin.random.Random


object StalkerPortalNetApi {

    @kotlinx.serialization.Serializable
    data class NetErrorRegistration(val status: Int, val msg: String, val block_msg: String)

    open class NetException(val auth: AuthData?, val url: String, val code: Int?, val body: String?, inMessage: String?, exc: java.lang.Exception? = null): java.lang.Exception(inMessage, exc) {
        val userTitle: String
            get() = "${code?.let { "$it " } ?: ""}$url${auth?.let { "\n${it.mac}/${it.token}" } ?: ""}"
        val userMessage: String
            get() = "${super.message ?: ""}${body?.let { "\n\n${it.take(1000)}" } ?: ""}"
        override val message: String?
            get() = userTitle + "\n" + userMessage
    }

    class UnauthorizedException(auth: AuthData?, url: String, code: Int?, body: String?): NetException(auth, url, code, body, "Unauthorized")
    class AccessDeniedException(auth: AuthData?, url: String, code: Int?, body: String?): NetException(auth, url, code, body, "Access Denied")
    class AutoRegistrationDisabledContactProviderException(auth: AuthData?, url: String, code: Int?, body: String?, val data: NetErrorRegistration):
        NetException(auth, url, code, body, data.block_msg)

    val json by lazy { Json {
        ignoreUnknownKeys = true
    } }

    private var _netapi: IStalkerPortalNetApi? = null
    var serverUrl: String? = null
        private set

    val serverUrlWithEndSlash: String?
        get() = serverUrl?.let { if (it.endsWith('/')) it else it + "/" }

    val netapi: IStalkerPortalNetApi
        get() {
            if (_netapi == null) {
                reinit()
            }
            return _netapi!!
        }

    fun reinit() {
        val newServerUrl = BaseApp.sharedSettings.serverOrDefault ?: throw java.lang.NullPointerException("server url == null")

        serverUrl = newServerUrl
        Timber.e("server url = $serverUrl")

        val logInterceptor = HttpLoggingInterceptor().apply {
            setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC)
        }
        val okHttpClientBuilder = OkHttpClient.Builder().apply {
            addNetworkInterceptor(logInterceptor)
            addInterceptor { chain ->
                val requestWithUserAgent = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
        }
        //val contentType = "application/json; charset=utf-8".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(serverUrl!!)
            .addConverterFactory(ScalarsConverterFactory.create())
            /*.addConverterFactory(Json{
                    ignoreUnknownKeys = true
                }.asConverterFactory(contentType))
             */
            .client(okHttpClientBuilder.build())
            .build()

        _netapi = retrofit.create(IStalkerPortalNetApi::class.java)
    }

    fun cookieCommonByMac(mac: String): String {
        return "mac=$mac; stb_lang=en; timezone=Europe/London"
    }

    fun authBearer(auth: AuthData): String {
        return "Bearer ${auth.token}"
    }

    fun authCookie(auth: AuthData): String {
        return cookieCommonByMac(auth.mac)
    }

    @kotlinx.serialization.Serializable
    data class ResponseText(val text: String)

    inline fun<reified T> doAndParseCall(auth: AuthData?, apicall: ()->Response<String>, parse: (body: String)->T): T {
        val resp = try {
            apicall()
        } catch (ex: Exception) {
            throw ex
        }

        val url = resp.raw().request.url.toString()
        val code = resp.code()
        val body = resp.body() ?: ""

        body.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("Authorization failed"))
                throw UnauthorizedException(auth, url, code, body)
            if (it.startsWith("Access denied"))
                throw AccessDeniedException(auth, url, code, body)
        }

        if (!resp.isSuccessful) {
            throw NetException(auth, url, code, body, null)
        }

        //if (Random.nextInt(50) == 7)
        //    throw UnauthorizedException(auth, url, code, body)

        try {
            return parse(body)
        } catch (ex: java.lang.Exception) {
            try {
                json.decodeFromString<NetErrorRegistration>(body)
            } catch (ex: java.lang.Exception) {
                null
            }?.let {
                throw AutoRegistrationDisabledContactProviderException(auth, url, code, body, it)
            }
            throw NetException(auth, url, code, body, null, ex)
        }
    }

    inline fun<reified T> doCall(auth: AuthData?, apicall: ()->Response<String>): T {
        return doAndParseCall<T>(auth, apicall) {
            json.decodeFromString<T>(it)
        }
    }

    inline fun doCall(auth: AuthData?, apicall: ()->Response<String>) {
        return doAndParseCall<Unit>(auth, apicall) {}
    }

    inline fun doCallSilent(auth: AuthData?, apicall: ()->Response<String>) {
        try {
            doCall(auth, apicall)
        } catch (ex: java.lang.Exception) {
            BaseApp.handleError(ex, false, false)
        }
    }

    suspend inline fun<reified T> retry(retryCount: Int = 2, delayMS: Long = 400, call: ()->T): T {
        for (i in 0 .. retryCount) {
            try {
                return call()
            } catch (ue: UnauthorizedException) {
                throw ue
            } catch (ex: Exception) {
                if (i == retryCount) {
                    throw ex
                } else {
                    delay(delayMS * (i + 1))
                }
            }
        }
        throw java.lang.Exception()
    }

    suspend fun getToken(mac: String): String {
        @kotlinx.serialization.Serializable
        data class HandshakeResp(val token: String)

        @kotlinx.serialization.Serializable
        data class HandshakeExResp(val js: HandshakeResp)

        return doCall<HandshakeExResp>(null) {
            netapi.getToken(cookieCommonByMac(mac))
        }.js.token
    }

    suspend fun getProfile(auth: AuthData): NetProfile {
        @kotlinx.serialization.Serializable
        data class NetProfileEx(val js: NetProfile)

        @kotlinx.serialization.Serializable
        data class NetErrorRegistration(val status: Int, val msg: String, val block_msg: String)

        return doCall<NetProfileEx>(auth) {
            netapi.getProfile(authBearer(auth), authCookie(auth))
        }.js
    }

    suspend fun getChannels(auth: AuthData): NetChannelsList {
        @kotlinx.serialization.Serializable
        data class NetChannelsEx(val js: NetChannelsList)

        return doCall<NetChannelsEx>(auth) {
            netapi.getChannels(authBearer(auth), authCookie(auth))
        }.js
    }

    suspend fun getChannelVideoStreamUrl(auth: AuthData, magic_cmd: String): NetChannelLink {
        @kotlinx.serialization.Serializable
        data class NetPlaybackUrlEx(val js: NetChannelLink)

        return doCall<NetPlaybackUrlEx>(auth) {
            netapi.getVideoStreamUrl(authBearer(auth), authCookie(auth), magic_cmd)
        }.js
    }

    suspend fun getMovieVOD(auth: AuthData, movieId: Int): NetVOD {
        @kotlinx.serialization.Serializable
        data class NetMoviePlaybackDataListEx(val js: NetVODList)

        return doCall<NetMoviePlaybackDataListEx>(auth){
            netapi.getMovieOrSeriesElements(authBearer(auth), authCookie(auth), movieId)
        }.js.data.first()
    }

    suspend fun getSeriesSeasons(auth: AuthData, seriesId: Int): List<NetSeason> {
        @kotlinx.serialization.Serializable
        data class NetSeasonsListEx(val js: NetSeasonsList)

        return doCall<NetSeasonsListEx>(auth){
            netapi.getMovieOrSeriesElements(authBearer(auth), authCookie(auth), seriesId)
        }.js.data
    }

    suspend fun getSeriesSeasonEpisodes(auth: AuthData, seriesId: Int, seasonId: Int): List<NetEpisode> {
        @kotlinx.serialization.Serializable
        data class NetEpisodeListEx(val js: NetEpisodeList)

        return doCall<NetEpisodeListEx>(auth) {
            netapi.getSeriesSeasonEpisodes(authBearer(auth), authCookie(auth), seriesId, seasonId)
        }.js.data
    }

    suspend fun getSeriesSeasonEpisodeVOD(auth: AuthData, seriesId: Int, seasonId: Int, episodeId: Int): List<NetVOD> {
        @kotlinx.serialization.Serializable
        data class NetEpisodesListEx(val js: NetVODList)

        return doCall<NetEpisodesListEx>(auth) {
            netapi.getSeriesSeasonEpisodeVOD(authBearer(auth), authCookie(auth), seriesId, seasonId, episodeId)
        }.js.data
    }


    suspend fun getChannelPrograms(auth: AuthData, ch_id: Int): List<NetChannelProgram> {
        @kotlinx.serialization.Serializable
        data class NetChannelProgramsEx(val js: List<NetChannelProgram>)

        return doCall<NetChannelProgramsEx>(auth) {
            netapi.getChannelPrograms(authBearer(auth), authCookie(auth), ch_id)
        }.js
    }

    val programDayFormat = SimpleDateFormat("yyyy-MM-dd")
    suspend fun getChannelProgramsByDay(auth: AuthData, ch_id: Int, day: DateTime): List<NetChannelProgram> {
        @kotlinx.serialization.Serializable
        data class NetChannelProgramsEx(val js: NetChannelProgramList)

        val dayStr = programDayFormat.format(day.toDate())

        return doCall<NetChannelProgramsEx>(auth) {
            netapi.getChannelProgramsByDay(authBearer(auth), authCookie(auth), ch_id, dayStr)
        }.js.data
    }

    suspend fun getTvGenres(auth: AuthData): List<NetGenre> {
        @kotlinx.serialization.Serializable
        data class NetGenresEx(val js: List<NetGenre>)

        return doCall<NetGenresEx>(auth){
            netapi.getTVGenres(authBearer(auth), authCookie(auth))
        }.js
    }

    suspend fun getMovieCategories(auth: AuthData): List<NetGenre> {
        @kotlinx.serialization.Serializable
        data class NetFilmCategoriesEx(val js: List<NetGenre>)

        return doCall<NetFilmCategoriesEx>(auth) {
            netapi.getMovieCategories(authBearer(auth), authCookie(auth))
        }.js
    }

    suspend fun getMovieOrderedList(auth: AuthData,
                                    category: String, page: Int, fav: Int? = null, search: String? = null): NetMovieOrSeriesList {
        @kotlinx.serialization.Serializable
        data class NetMoviesListEx(val js: NetMovieOrSeriesList)

        return doCall<NetMoviesListEx> (auth){
            netapi.getMovieOrderedList(authBearer(auth), authCookie(auth), category, 0, "added", page, "all", 0, fav, search)
        }.js
    }

    suspend fun getMovieCategoriesByAlias(auth: AuthData, alias: String): List<NetGenre> {
        @kotlinx.serialization.Serializable
        data class NetFilmCategoriesEx2(val js: List<NetGenre>)

        return doCall<NetFilmCategoriesEx2> (auth){
            netapi.getMovieCategoriesByAlias(authBearer(auth), authCookie(auth), alias)
        }.js
    }

    /*
    suspend fun getMovieYears(auth: SharedAuthStorage.AuthData, category: String): String {
        return doCall<String> { netapi.getMovieYears(authBearer(auth), authCookie(auth), category) }
    }

    suspend fun getMovieABC(auth: SharedAuthStorage.AuthData): String {
        return doCall<String> { netapi.getMovieABC(authBearer(auth), authCookie(auth))}
    }
*/

    suspend fun setParentalPassword(auth: AuthData, oldPass: String, newPass: String) {
        doCall(auth) {
            netapi.setParentalPassword(authBearer(auth), authCookie(auth), oldPass, newPass, newPass)
        }
    }
    suspend fun addMovieToFav(auth: AuthData, id: Int) {
        doCall(auth) {
            netapi.addMovieToFav(authBearer(auth), authCookie(auth), id)
        }
    }

    suspend fun removeMovieFromFav(auth: AuthData, id: Int) {
        doCall(auth) {
            netapi.removeMovieFromFav(authBearer(auth), authCookie(auth), id)
        }
    }

    suspend fun setFavoriteChannelList(auth: AuthData, list: List<Int>) {
        val str = StringBuilder().apply {
            list.forEachIndexed { i, v->
                append(v)
                if (i + 1 != list.size)
                    append(",")
            }
        }.toString()
        doCall(auth) {
            netapi.setFavoriteChannelList(authBearer(auth), authCookie(auth), str)
        }
    }

    suspend fun getProgramVOD(auth: AuthData, program_id: Int): NetProgramVOD {
        @kotlinx.serialization.Serializable
        data class NetProgramVODEx(val js: NetProgramVOD)

        return doCall<NetProgramVODEx>(auth) {
            netapi.getProgramVOD(authBearer(auth), authCookie(auth), "auto /media/$program_id.mpg")
        }.js
    }

    suspend fun getMessages(auth: AuthData): NetMessage? {
        @kotlinx.serialization.Serializable
        data class NetMessageEx(val js: NetMessage)

        return doCall<NetMessageEx>(auth) {
            netapi.getMessages(authBearer(auth), authCookie(auth))
        }.js.takeIf { it.data?.id != null }
    }

    suspend fun maskMessageRead(auth: AuthData, msgId: Int) {
        return doCall(auth) {
            netapi.maskMessageRead(authBearer(auth), authCookie(auth), msgId)
        }
    }



    suspend fun analyticsOnChannelStartPlay(auth: AuthData, content_id: Int, link_id: Int, ch_id: Int) {
        return doCallSilent(auth) {
            netapi.analyticsOnChannelStartPlay(authBearer(auth), authCookie(auth), content_id, link_id, ch_id)
        }
    }

    suspend fun analyticsOnChannelStartPlayMulticast(auth: AuthData, content_id: Int, param: String) {
        return doCallSilent(auth) {
            netapi.analyticsOnChannelStartPlayMulticast(authBearer(auth), authCookie(auth), content_id, param)
        }
    }

    suspend fun analyticsOnChannelTryGetLink(auth: AuthData, itv_id: Int) {
        return doCallSilent(auth) {
            netapi.analyticsOnChannelTryGetLink(authBearer(auth), authCookie(auth), itv_id)
        }
    }
    suspend fun analyticsSetLastPlayedChannel(auth: AuthData, id: Int) {
        return doCallSilent(auth) {
            netapi.analyticsSetLastPlayedChannel(authBearer(auth), authCookie(auth), id)
        }
    }

    suspend fun analyticsOnVodPlaybackEnded(auth: AuthData, video_id: Int) {
        return doCallSilent(auth) {
            netapi.analyticsOnVodPlaybackEnded(authBearer(auth), authCookie(auth), video_id)
        }
    }

    suspend fun analyticsOnVodPlaybackNotEnded(auth: AuthData, video_id: Int, end_time: Int, file_id: Int) {
        return doCallSilent(auth) {
            netapi.analyticsOnVodPlaybackNotEnded(authBearer(auth), authCookie(auth), video_id, end_time, file_id)
        }
    }
}