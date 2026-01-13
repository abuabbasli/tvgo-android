package com.mc2soft.ontv.ontvapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.mc2soft.ontv.common.AppsList
import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.consoleapi.ConsoleAuthData
import com.mc2soft.ontv.common.consoleapi.NetConsoleCompany
import com.mc2soft.ontv.common.consoleapi.api.NetConsoleApi
import com.mc2soft.ontv.common.run_helpers.RunOnTvActivityHelper
import com.mc2soft.ontv.common.run_helpers.RunUpdateApplicationsActivityHelper
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper
import com.mc2soft.ontv.common.settings.SharedSettingsContentResolverHelper.mac
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.NotificationMessages
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.common.stalker_portal.entities.NetMessageData
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.ui.BaseActivity
import com.mc2soft.ontv.common.ui.StalkerPortalNotificationDialog
import com.mc2soft.ontv.common.ui.UiUtil
import com.mc2soft.ontv.ontvapp.databinding.MainActivityBinding
import com.mc2soft.ontv.ontvapp.model.ChannelsCache
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.model.MoviesCache
import com.mc2soft.ontv.ontvapp.model.UserLocalData
import com.mc2soft.ontv.ontvapp.movies.MovieDetailsDialog
import com.mc2soft.ontv.ontvapp.movies.MovieGenreTileView
import com.mc2soft.ontv.ontvapp.movies.MovieTileView
import com.mc2soft.ontv.ontvapp.movies.MoviesMenuView
import com.mc2soft.ontv.ontvapp.movies.SeriesDetailsDialog
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.player.PlayerView
import com.mc2soft.ontv.ontvapp.player.ui.EnterTVNumberDialog
import com.mc2soft.ontv.ontvapp.player.ui.PlayerHotbarView
import com.mc2soft.ontv.ontvapp.player.ui.PlayerOverlayView
import com.mc2soft.ontv.ontvapp.tv.ChannelTileView
import com.mc2soft.ontv.ontvapp.tv.ProgramDayTileView
import com.mc2soft.ontv.ontvapp.tv.ProgramTileView
import com.mc2soft.ontv.ontvapp.tv.TVGenreTileView
import com.mc2soft.ontv.ontvapp.tv.TVMenuView
import com.mc2soft.ontv.ontvapp.ui.TextFormatter
import com.microsoft.appcenter.analytics.Analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.neonwindwalker.hardtiles.ViewCache
import org.neonwindwalker.hardtiles.ViewCachesMap
import timber.log.Timber


class MainActivity : BaseActivity() {
    companion object {
        const val TICK_DT = 300L
        val RELOAD_DATA_TIME = if (BuildConfig.DEBUG) 60000L else 3*60000L
        fun get(context: Context?): MainActivity? {
            return UiUtil.getActivity<MainActivity>(context)
        }
    }

    lateinit var vb: MainActivityBinding

    val player: PlayerView
        get() = vb.player
    val playerOverlay: PlayerOverlayView
        get() = vb.playerOverlay
    val playbackSource: PlaybackSource?
        get() = player.playbackSource
    val channelPlaybackSource: PlaybackSource.ChannelPlaybackSource?
        get() = playbackSource as? PlaybackSource.ChannelPlaybackSource

    val isMovieModeInIntent: Boolean?
        get() = if (intent?.hasExtra(RunOnTvActivityHelper.MOVIE_MODE) == true)intent?.extras?.getBoolean(
            RunOnTvActivityHelper.MOVIE_MODE, isMovieMode) else null

    private val _isMovieModeFlow = MutableStateFlow<Boolean>(false)
    val isMovieModeFlow = _isMovieModeFlow.asStateFlow()

    val isMovieMode: Boolean
        get() = _isMovieModeFlow.value

    private var menu: MenuBaseView? = null

    var viewCaches: ViewCachesMap? = null
        private set

    private var needStartupOpenFlag = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isMovieModeInIntent?.let {
            _isMovieModeFlow.value = it
        }

        needStartupOpenFlag = true

        vb = MainActivityBinding.inflate(layoutInflater)
        setContentView(vb.root)

        Timber.i("isMovieModeInIntent=$isMovieModeInIntent")
        Timber.i("movieMode=$isMovieMode")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewCaches = ViewCachesMap(this).also {
            it.add(ViewCache.create<TVGenreTileView>(this,10))
            it.add(ViewCache.create<ChannelTileView>(this,10))
            it.add(ViewCache.create<ProgramDayTileView>(this,4))
            it.add(ViewCache.create<ProgramTileView>(this,15))
            it.add(ViewCache.create<PlayerHotbarView.ChannelTileView>(this,5))
            it.add(ViewCache.create<PlayerHotbarView.MovieTileView>(this,5))
            it.add(ViewCache.create<MovieGenreTileView>(this,10))
            it.add(ViewCache.create<MovieTileView>(this,10))
            it.add(ViewCache.create<SeriesDetailsDialog.SeasonTileView>(this,5))
            it.add(ViewCache.create<SeriesDetailsDialog.EpisodeTileView>(this,10))
        }

        player.init()
        vb.playerOverlay.init(this)

        scope?.launch {
            player.playbackSourceFlow.collect {
                player.channelPlaybackSource?.let {
                    if (isEmptyFragments && menu?.showed != true) {
                        vb.playerOverlay.show()
                    }
                }
            }
        }

        scope?.launch {
            NotificationMessages.netMessage.collect {
                handleNotification()
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            handleNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyMenu()
        player.destroy()
        viewCaches?.destroy()
        viewCaches = null
    }

    override fun mapNameToTheme(name: String?): Int? {
        return when (name) {
            SharedSettingsContentResolverHelper.THEME_TEST -> R.style.Theme_OnTv_Test
            NetConsoleCompany.COLOR_PRESET_ONTV ->  R.style.Theme_OnTv_OnTV
            NetConsoleCompany.COLOR_PRESET_TvinTV ->  R.style.Theme_OnTv_TvinTV
            NetConsoleCompany.COLOR_PRESET_BirLinkTV ->  R.style.Theme_OnTv_BirLink
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        this.intent = intent
        Timber.i("onNewIntent $isMovieModeInIntent")
        isMovieModeInIntent?.let {
            if (it != isMovieMode) {
                Timber.i("onNewIntent new isMovieMode=$it")
                _isMovieModeFlow.value = it
                destroyMenu()
                clearMainScreen()
                player.closePlaybackSource()
                playerOverlay.hide()
                if (isMovieMode) {
                    player.outputRect = null
                }
                startUiCompleted = false
                needStartupOpenFlag = true
                startUiIfAllowed()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startedScope?.launch(Dispatchers.IO) {
            try {

                if (OnTVApp.isStandalone) {
                    BaseApp.sharedSettings.mac = "02:AD:32:01:5C:32"
                    val mac = BaseApp.sharedSettings.macOrDefault
                    val authStr = BaseApp.sharedSettings.consoleAuth
                    val auth = ConsoleAuthData.deserialize(authStr)?.takeIf { it.mac == mac } ?: run {
                        ConsoleAuthData(mac, ConsoleAuthData.genToken(mac)).also {
                            BaseApp.sharedSettings.consoleAuth = it.serialize()
                        }
                    }

                    BaseApp.sharedSettings.consoleDefaultsObj = NetConsoleApi.getInfo(auth)
                }

                AuthorizedUser.startup(true, true) {
                    val firebaseAnalytics = Firebase.analytics
                    if (!isMovieMode) {
                        ChannelsCache.updateChannelsSync()
                        Analytics.trackEvent("TV Started")
                        firebaseAnalytics.logEvent(name = "app_started"){
                            param("AppType", "TV")
                        }
                    } else {
                        Analytics.trackEvent("Movie Started")
                        firebaseAnalytics.logEvent(name = "app_started"){
                            param("AppType", "Movie")
                        }
                    }
//                    checkUpdates()
                }
            } catch (ex: java.lang.Exception) {
                BaseApp.handleError(ex, fatal = true)
                return@launch
            }

            launch {
                while(true) {
                    delay(RELOAD_DATA_TIME)
                    if (!isMovieMode) {
                        ChannelsCache.updateChannels()
                    }
                    delay(RELOAD_DATA_TIME)
                    UserLocalData.inst.saveInNeed()
                    delay(RELOAD_DATA_TIME)
                    (menu as? TVMenuView)?.vm?.programs?.channel?.let {
                        ChannelsCache.triggerUpdatePrograms(it.id)
                    }
                    delay(RELOAD_DATA_TIME)
                    player.channelPlaybackSource?.channel?.let {
                        ChannelsCache.triggerUpdatePrograms(it.id)
                    }
                }
            }

            launch {
                while(true) {
                    delay(AuthorizedUser.REAUTH_HACK_DELAY_TIME)
                    try {
                        AuthorizedUser.startup(false, false)
                    } catch (ex: java.lang.Exception) {
                        BaseApp.handleError(ex)
                    }
                }
            }

            launch {
                while (true) {
                    NotificationMessages.triggerReadNetMessages()
                    delay(NotificationMessages.READ_NET_MESSAGES_POOL_DELAY)
                }
            }

            launch(Dispatchers.Main) {
                startUiIfAllowed()
            }
        }

        startedScope?.launch {
            while(true) {
                delay(TICK_DT)
                tick()
            }
        }
    }

    private var playerPausedOnStop: Boolean = false

    override fun onStop() {
        super.onStop()

        AuthorizedUser.stop()
        player.seekProcessDirection = 0
        playerPausedOnStop = player.pause
        player.pause = true
        if (playbackSource?.isCensored == true) {
            player.closePlaybackSource()
        }

        startUiCompleted = false
        UserLocalData.inst.saveInNeed()
    }

    override fun onResume() {
        super.onResume()
        if (createdWithThemeName != BaseApp.sharedSettings.themeOrDefault) {
            intent?.putExtra(RunOnTvActivityHelper.MOVIE_MODE, isMovieMode)
            Timber.i("pre restart isMovieModeInIntent=$isMovieModeInIntent")
            restart()
            return
        }
        player.refreshOutputView()
        LocalizeStringMap.updateIfLocalizationChanged()
        TextFormatter.updateIfLocalizationChanged()
        startUiIfAllowed()
    }

    private suspend fun checkUpdates(){
        try {
            val mac = BaseApp.sharedSettings.macOrDefault
            val authStr = BaseApp.sharedSettings.consoleAuth
            val auth = ConsoleAuthData.deserialize(authStr)?.takeIf { it.mac == mac } ?: run {
                ConsoleAuthData(mac, ConsoleAuthData.genToken(mac)).also {
                    BaseApp.sharedSettings.consoleAuth = it.serialize()
                }
            }
            BaseApp.sharedSettings.consoleDefaultsObj = NetConsoleApi.getInfo(auth)
            val list = AppsList.load()
            if (list.isNeedUpdateAnyApp(applicationContext)) {
                RunUpdateApplicationsActivityHelper.run(context = applicationContext, appsList = list, startAfterIntent = this.intent, background = true)
            }
        } catch (ex: java.lang.Exception) {
            BaseApp.handleError(ex)
        }
    }

    private var startUiCompleted: Boolean = false

    private fun startUiIfAllowed() {
        if (!isResume)return
        if (!AuthorizedUser.isStartupSuccess)return
        if (startUiCompleted)return

        startUiCompleted = true

        buildMenu()

        if (needStartupOpenFlag) {
            needStartupOpenFlag = false
            if (isMovieMode) {
                /*
                UserLocalData.inst.movieHistory.value.firstOrNull()?.let { movieItem ->
                    UserLocalData.inst.moviesCache.value.find { it.id == movieItem.id }?.let { movie->
                        if (movie.isMovie) {
                            PlaybackSource.MoviePlaybackSource(movie, movieItem.pos).showAccessDialogInNeed(this) {
                                it.loadUrlAndPlay(player)
                            }
                        } else {
                            AuthorizedUser.scope.launch {
                                val series = SeriesDetails.load(movie)
                                val season = series.seasons.find { it.season.id == movieItem.seasonId } ?: return@launch
                                val episode = season.episodes.find { it.id == movieItem.episodeId } ?: return@launch
                                scope?.launch {
                                    PlaybackSource.SeriesPlaybackSource(series, series.seasons.indexOf(season), season.episodes.indexOf(episode)).showAccessDialogInNeed(this@MainActivity) {
                                        it.loadUrlAndPlay(player)
                                    }
                                }
                            }
                        }
                    }
                } ?: run {
                    openMenu()
                }
                 */
                openMenu()
            } else {
                UserLocalData.inst.channelHistory.value.firstOrNull()?.let { chId ->
                    ChannelsCache.getChannel(chId)?.let {
                        PlaybackSource.ChannelPlaybackSource(null, it).showAccessDialogInNeed(this) {
                            it.loadUrlAndPlay(player)
                        }
                    }
                } ?: run {
                    openMenu()
                }
            }
        } else {
            playbackSource?.showAccessDialogInNeedWithCancelCallback(this, {
                player.closePlaybackSource()
            }) {
                (playbackSource as? PlaybackSource.ChannelPlaybackSource)?.takeIf { it.isLive }?.let {
                    it.loadUrlAndPlay(player)
                } ?: run {
                    player.pause = playerPausedOnStop
                }
            }
        }
    }

    fun buildMenu() {
        if (menu == null) {
            menu = if (isMovieMode) MoviesMenuView(this) else TVMenuView(this)
            vb.menuHost.addView(menu)
        }
    }

    fun destroyMenu() {
        if (menu != null) {
            menu?.destroy()
            menu = null
            vb.menuHost.removeAllViews()
        }
    }

    fun tick() {
        player.tick()
        playerOverlay.tick()
        menu?.takeIf { it.showed }?.tick()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && isEmptyFragments && menu?.showed != true) {
            if (playerOverlay.dispatchKeyDown(event.keyCode, isMovieMode))return true
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !playerOverlay.isFocusedViewsShowed) {
                openMenu()
                return true
            }
        }

        if (super.dispatchKeyEvent(event))return true

//        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
//            if (menu?.showed == true) {
//                menu?.hide()
//            } else {
//                openMenu()
//            }
//            return true
//        }

        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9 && !isMovieMode) {
            EnterTVNumberDialog(keyCode).show(supportFragmentManager, null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private var openMenuOnBackButton: Boolean = false

    override fun onBackPressed() {
        if (menu?.showed == true) {
            if (playbackSource == null) {
                super.onBackPressed()
                return
            } else {
                menu?.hide()
            }
            return
        }
        if (playerOverlay.showed) {
            playerOverlay.hide()
            return
        }
        if (openMenuOnBackButton) {
            openMenuOnBackButton = false
            openMenu()
            return
        }
        super.onBackPressed()
    }

    fun openMenu() {
        playerOverlay.hide()
        if (isMovieMode) {
            player.closePlaybackSource()
        }
        clearMainScreen()
        menu?.show()
        openMenuOnBackButton = false
    }

    fun hideMenu(openOnBack: Boolean) {
        openMenuOnBackButton = openOnBack
        menu?.hide()
    }

    fun openMovieDetails(movie: NetMovieOrSeries?) {
        movie ?: return
        if (movie.isMovie)
            MovieDetailsDialog(movie).show(supportFragmentManager, null)
        else
            SeriesDetailsDialog(movie).show(supportFragmentManager, null)
    }

    val isEmptyFragments: Boolean
        get() = supportFragmentManager.backStackEntryCount == 0
    fun clearMainScreen() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    fun openChannel(ch: NetChannel) {
        if (menu?.showed == true) {
            (menu as? TVMenuView)?.openChannel(ch)
        } else {
            PlaybackSource.ChannelPlaybackSource(null, ch).showAccessDialogInNeed(this) {
                it.loadUrlAndPlay(player)
            }
        }
    }

    fun handleNotification() {
        NotificationMessages.netMessage.value?.data?.let {
            when(it.eventType) {
                NetMessageData.EventType.send_msg -> {
                    if (isResume && supportFragmentManager.findFragmentByTag(StalkerPortalNotificationDialog.TAG) == null) {
                        StalkerPortalNotificationDialog().show(supportFragmentManager, StalkerPortalNotificationDialog.TAG)
                    } else null
                }
                NetMessageData.EventType.update_subscription -> {
                    ChannelsCache.updateChannels()
                    ChannelsCache.markAllProgramsShouldBeReload()
                    (menu as? TVMenuView)?.vm?.programs?.channel?.let {
                        ChannelsCache.triggerUpdatePrograms(it.id)
                    }
                    player.channelPlaybackSource?.channel?.let {
                        ChannelsCache.triggerUpdatePrograms(it.id)
                    }
                    (menu as? MoviesMenuView)?.vm?.genres?.focusToSelectedGenre()
                    MoviesCache.clear()
                    (menu as? MoviesMenuView)?.vm?.grid?.rebuild()
                    NotificationMessages.markMessageReadOrExpire()
                }
                else -> {
                    NotificationMessages.markMessageReadOrExpire()
                }
            }
        }
    }
}