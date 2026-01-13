package com.mc2soft.ontv.common.stalker_portal

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.BuildConfig
import com.mc2soft.ontv.common.stalker_portal.api.StalkerPortalNetApi
import com.mc2soft.ontv.common.stalker_portal.entities.*
import com.microsoft.appcenter.crashes.Crashes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.joda.time.DateTime
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object AuthorizedUser {
    const val BIG_RETRY_COUNT = 5
    val REAUTH_HACK_DELAY_TIME = if (BuildConfig.DEBUG) 60000L else 60 * 60000L

    var scope = CoroutineScope(Dispatchers.IO)
        private set

    private val _profile = MutableStateFlow<NetProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _tvGenres = MutableStateFlow(emptyList<NetGenre>())
    val tvGenres = _tvGenres.asStateFlow()

    private val _filmCategories = MutableStateFlow(emptyList<NetGenre>())
    val filmCategories = _filmCategories.asStateFlow()

    var auth: AuthData? = null
        private set

    class NullAuthorizationException : java.lang.Exception()

    var clearCachesCb: (()->Unit)? = null

    val mainOpsDispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun logout(clearCachesFlag: Boolean = true) = withContext(mainOpsDispatcher) {
        isStartupSuccess = false

        auth?.let {
            Timber.e("logout from ${it.mac} ${it.token} ${it.server}")
        }

        scope.cancel()

        auth = null
        BaseApp.sharedSettings.auth = null
        if (clearCachesFlag)
            clearCaches()

        scope = CoroutineScope(Dispatchers.IO)
    }

    fun clearCaches() {
        _profile.value = null
        _tvGenres.value = emptyList()
        _filmCategories.value = emptyList()
        NotificationMessages.clear()
        clearCachesCb?.invoke()
    }


    @Volatile var isStartupSuccess: Boolean = false
        private set

    suspend fun startup(needTVGenres: Boolean, needFilmCategories: Boolean, additionalLoad: (suspend ()->Unit)? = null) = withContext(mainOpsDispatcher) {
        StalkerPortalNetApi.serverUrl?.let {
            if (it != BaseApp.sharedSettings.serverOrDefault) {
                StalkerPortalNetApi.reinit()
                clearCaches()
            }
        }

        StalkerPortalNetApi.netapi

        /*
        val sharedAuth = AuthData.deserialize(BaseApp.sharedSettings.auth)

        sharedAuth?.let {
            if (it.mac != BaseApp.sharedSettings.macOrDefault || it.server != StalkerPortalNetApi.serverUrl) {
                logoutSync(true)
                startup(needProfile, needTVGenres, needFilmCategories)
                return
            }
        }

        if (auth == null) {
            auth = sharedAuth
        } else {
            if (auth != sharedAuth) {
                logoutSync(false)
                startup(needProfile, needTVGenres, needFilmCategories)
                return
            }
        }

        if (auth == null) {
            authorize()
        }

         */

        val mac = BaseApp.sharedSettings.macOrDefault
        val token = StalkerPortalNetApi.retry {
            StalkerPortalNetApi.getToken(mac)
        }

        val newAuth = AuthData(mac, token, StalkerPortalNetApi.serverUrl!!)

        Timber.e("new auth ${newAuth.mac} ${newAuth.token} ${newAuth.server}")

        //нужно делать каждый раз после получения токена
        _profile.value = StalkerPortalNetApi.retry {
            StalkerPortalNetApi.getProfile(newAuth)
        }

        auth = newAuth
        BaseApp.sharedSettings.auth = newAuth.serialize()

        /*
        if (needProfile) {
            if (profile.value == null) {
                updateProfile()
            } else {
                scope.launch {
                    updateProfile()
                }
            }
        }
         */

        if (needTVGenres) {
            if (tvGenres.value.isEmpty()) {
                updateTVGenres()
            } else {
                scope.launch {
                    try {
                        updateTVGenres()
                    } catch (ex: Exception) {
                        BaseApp.handleError(ex)
                    }
                }
            }
        }

        if (needFilmCategories) {
            if (filmCategories.value.isEmpty()) {
                updateFilmCategories()
            } else {
                scope.launch {
                    try {
                        updateFilmCategories()
                    } catch (ex: Exception) {
                        BaseApp.handleError(ex)
                    }
                }
            }
        }

        additionalLoad?.invoke()

        isStartupSuccess = true
    }

    fun stop() {
        isStartupSuccess = false
        NotificationMessages.clear()
    }

    suspend fun getVideoStreamUrl(magic_cmd: String): NetChannelLink {
        return StalkerPortalNetApi.retry(BIG_RETRY_COUNT) {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getChannelVideoStreamUrl(auth, magic_cmd).apply {
                if (cmd.isBlank())throw java.lang.Exception("cmd empty")
            }
        }
    }

    suspend fun getProgramVideoStreamUrlHack(program: NetChannelProgram): String {
        val channelUrl = StalkerPortalNetApi.retry(BIG_RETRY_COUNT) {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getProgramVOD(auth, program.id).getChannelUrl() ?: throw Exception("cant get channel playback url")
        }

        return NetChannelLink(channelUrl, null).tuneLink(program.start_timestamp, program.stop_timestamp - program.start_timestamp) ?: throw java.lang.Exception("invalid link")
    }

    suspend fun getMovieVOD(movieId: Int): NetVOD {
        return StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getMovieVOD(auth, movieId)
        }
    }
    suspend fun getSeriesSeasons(seasonId: Int): List<NetSeason> {
        return StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getSeriesSeasons(auth, seasonId)
        }
    }

    suspend fun getSeriesSeasonEpisodes(seriesId: Int, seasonId: Int): List<NetEpisode> {
        return StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getSeriesSeasonEpisodes(auth, seriesId, seasonId)
        }
    }

    suspend fun getSeriesSeasonEpisodeVOD(seriesId: Int, seasonId: Int, episodeId: Int): List<NetVOD> {
        return StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getSeriesSeasonEpisodeVOD(auth, seriesId, seasonId, episodeId)
        }
    }

    class BadChannelsListLoadException(val list: List<Int>): java.lang.Exception("bad channels response")

    suspend fun getChannels(): NetChannelsList {
        var bestResponse: NetChannelsList? = null
        var bestResponseValidsCount = 0
        try {
            return StalkerPortalNetApi.retry(10, 200) {
                val auth = auth ?: throw NullAuthorizationException()
                StalkerPortalNetApi.getChannels(auth).apply {
                    val valids = data.count { it.isValidResponse }
                    if (bestResponseValidsCount < valids) {
                        bestResponseValidsCount = valids
                        bestResponse = this
                    }
                    if (valids != data.size) throw java.lang.Exception("bad channels data")
                }
            }.apply {
                if (data.any { !it.isValidResponse }) {
                    val ex = BadChannelsListLoadException(data.filter { !it.isValidResponse }.map { it.id })
                    var ids = ""
                    ex.list.forEach { ids += "$it\n" }
                    Crashes.trackError(ex, mapOf( "ids" to ids), null)
                    val crashlytics = Firebase.crashlytics
                    crashlytics.setCustomKeys {
                        key("ids", ids)
                    }
                    crashlytics.recordException(ex)
                }
            }
        } catch (ex: java.lang.Exception) {
            return bestResponse ?: throw ex
        }
    }

    class EmptyChannelsProgramsException: Exception()

    suspend fun getChannelPrograms(ch_id: Int): List<NetChannelProgram> {
        return StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getChannelPrograms(auth, ch_id).apply {
                if (isEmpty()) {
                    throw EmptyChannelsProgramsException()
                }
            }
        }
    }

    suspend fun getChannelPrograms(ch_id: Int, day: DateTime): List<NetChannelProgram> {
        return StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getChannelProgramsByDay(auth, ch_id, day).apply {
                if (isEmpty()) {
                    throw EmptyChannelsProgramsException()
                }
            }
        }
    }

    suspend fun updateProfile(): StateFlow<NetProfile?> {
        _profile.value = StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getProfile(auth)
        }
        return profile
    }

    suspend fun updateTVGenres(): StateFlow<List<NetGenre>> {
        _tvGenres.value = StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getTvGenres(auth).apply {
                if (isEmpty()) {
                    throw java.lang.Exception("empty genres")
                }
            }
        }
        return tvGenres
    }

    suspend fun updateFilmCategories(): StateFlow<List<NetGenre>> {
        _filmCategories.value = StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getMovieCategories(auth).apply {
                if (isEmpty()) {
                    throw java.lang.Exception("empty genres")
                }
            }
        }
        return filmCategories
    }

    suspend fun getMovies(page: Int = 0, category: String = NetGenre.ALL_ID): NetMovieOrSeriesList {
        return StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            when (category) {
                NetGenre.favorites.id -> StalkerPortalNetApi.getMovieOrderedList(auth, NetGenre.ALL_ID, page, 1)
                else-> StalkerPortalNetApi.getMovieOrderedList(auth, category, page)
            }
        }
    }

    suspend fun getSearchMovies(page: Int = 0, search: String): NetMovieOrSeriesList {
        return StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getMovieOrderedList(auth, NetGenre.ALL_ID, page, null, search)
        }
    }

    suspend fun setParentalPassword(pass: String) {
        val oldPass = profile.value?.parent_password ?: throw java.lang.Exception("no profile loaded")
        StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.setParentalPassword(auth, oldPass, pass)
        }
        profile.value?.let {
            _profile.value = it.copy(parent_password = pass)
        }
    }

    suspend fun isValidParentalPassword(pass: String): Boolean {
        updateProfile()
        return profile.value?.parent_password == pass
    }

    suspend fun addMovieToFav(id: Int) {
        return StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.addMovieToFav(auth, id)
        }
    }

    suspend fun removeMovieFromFav(id: Int) {
        return StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.removeMovieFromFav(auth, id)
        }
    }
    suspend fun setFavoriteChannelList(list: List<Int>) {
        return StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.setFavoriteChannelList(auth, list)
        }
    }

    suspend fun getProgramVOD(program_id: Int): NetProgramVOD {
        return StalkerPortalNetApi.retry {
            val auth = AuthorizedUser.auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getProgramVOD(auth, program_id)
        }
    }

    suspend fun getMessage(): NetMessage? {
        return StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.getMessages(auth)
        }
        /*
        val str = "{\n" +
                "      \"data\":{\n" +
                "         \"msgs\":1,\n" +
                "         \"id\":\"50700\",\n" +
                "         \"event\":\"send_msg\",\n" +
                "         \"need_confirm\":\"1\",\n" +
                "         \"msg\":\"Test1 for ontv\",\n" +
                "         \"reboot_after_ok\":\"0\",\n" +
                "         \"auto_hide_timeout\":\"0\",\n" +
                "         \"param1\":\"\",\n" +
                "         \"valid_until\":1681142101,\n" +
                "         \"additional_services_on\":\"1\"\n" +
                "      } }\n"
        return Json { ignoreUnknownKeys = true }.decodeFromString<NetMessage>(str)
         */
    }

    suspend fun markMessageRead(id: Int) {
        return StalkerPortalNetApi.retry {
            val auth = auth ?: throw NullAuthorizationException()
            StalkerPortalNetApi.maskMessageRead(auth, id)
        }
    }
}