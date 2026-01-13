package com.mc2soft.ontv.ontvapp.model

import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannelProgram
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannelsList
import com.mc2soft.ontv.common.stalker_portal.entities.NetGenre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.joda.time.DateTime

object ChannelsCache {
    const val RELOAD_PROGRAMS_MIN_TIME = 20 * 60000
    const val MAX_PREV_DAYS = 7

    private val _channels = MutableStateFlow(NetChannelsList())
    val channels = _channels.asStateFlow()
    val channelsOpsDispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun updateChannelsSync() {
        FavoriteChannelsCache.upload()
        val list = AuthorizedUser.getChannels()
        UserLocalData.inst.updateChannelsCmdFields(list.data)
        _channels.value = list
        FavoriteChannelsCache.removeOldRecords()
    }

    fun updateChannels() {
        AuthorizedUser.scope.launch(channelsOpsDispatcher) {
            try {
                updateChannelsSync()
            } catch (ex: Exception) {
                BaseApp.handleError(ex)
            }
        }
    }

    fun isChannelHavePrograms(channelId: Int): Boolean? {
        return channels.value.data.find { it.id == channelId }?.isHavePrograms
    }

    fun getChannel(id: Int): NetChannel? {
        return channels.value.data.find { it.id == id }
    }

    class ChannelPrograms(val channelId: Int) {
        private val _programs = MutableStateFlow(emptyList<NetChannelProgram>())
        val programs: StateFlow<List<NetChannelProgram>> = _programs

        private var loadJob: Job? = null
        var lastLoadTime = 0L
            private set
        val isLoaded: Boolean
            get() = lastLoadTime > 0
        var firstLoadDay: DateTime? = null
            private set
        var loadDay: DateTime? = null
            private set
        var isCompleteLoaded: Boolean = false
            private set

        fun maybeNeedReload() {
            if (loadJob?.isActive != true &&
                isChannelHavePrograms(channelId) != false &&
                lastLoadTime + RELOAD_PROGRAMS_MIN_TIME < System.currentTimeMillis()) {
                loadJob = AuthorizedUser.scope.launch {
                    try {
                        val progs = AuthorizedUser.getChannelPrograms(channelId)
                        _programs.value = progs
                        firstLoadDay = progs.getOrNull(0)?.startTimeMS?.let { DateTime(it).let { DateTime(it.year, it.monthOfYear, it.dayOfMonth, 0, 0, 0, 0) } }
                        lastLoadTime = System.currentTimeMillis()
                        isCompleteLoaded = false
                        _totalProgramsLoaded.value += progs.size
                    } catch (ex: AuthorizedUser.EmptyChannelsProgramsException) {
                        lastLoadTime = System.currentTimeMillis()
                        isCompleteLoaded = true
                        _programs.value = emptyList()
                    } catch (ex: Exception) {
                        BaseApp.handleError(ex)
                    }
                    loadJob = null
                }
            }
        }

        fun loadMorePrev() {
            if (loadJob?.isActive == true || isCompleteLoaded)return
            val firstLoadDay = firstLoadDay ?: return
            val day = loadDay?.let { it.minusDays(1) } ?: firstLoadDay
            loadJob = AuthorizedUser.scope.launch {
                try {
                    val progs = AuthorizedUser.getChannelPrograms(channelId, day)
                    val existIds = programs.value.map { it.id }.toSet()
                    loadDay = day
                    if (progs.isEmpty() || firstLoadDay.millis - day.millis >= MAX_PREV_DAYS*24*60*60*1000) {
                        isCompleteLoaded = true
                    }
                    _programs.value = progs.filter { !existIds.contains(it.id) }.plus(programs.value)
                    if (day == firstLoadDay) {
                        AuthorizedUser.scope.launch {
                            loadMorePrev()
                        }
                    }
                } catch (ex: AuthorizedUser.EmptyChannelsProgramsException) {
                    isCompleteLoaded = true
                } catch (ex: Exception) {
                    BaseApp.handleError(ex)
                }
                loadJob = null
            }
        }

        fun setNeedBeReload() {
            lastLoadTime = 0L
        }
    }

    private val _totalProgramsLoaded = MutableStateFlow(0)
    val totalProgramsLoaded = _totalProgramsLoaded.asStateFlow()
    private val programsMap = HashMap<Int, ChannelPrograms>()

    fun triggerUpdatePrograms(chId: Int): StateFlow<List<NetChannelProgram>> {
        return programsMap.getOrPut(chId) { ChannelPrograms(chId) }.apply {
            maybeNeedReload()
        }.programs
    }

    fun triggerLoadMorePrevPrograms(chId: Int?) {
        chId ?: return
        programsMap.get(chId)?.loadMorePrev()
    }

    fun markAllProgramsShouldBeReload() {
        programsMap.values.forEach {
            it.setNeedBeReload()
        }
    }

    fun getPrograms(chId: Int?): StateFlow<List<NetChannelProgram>>? {
        return programsMap.get(chId)?.programs
    }

    fun getLiveProgram(chId: Int): NetChannelProgram? {
        return programsMap.get(chId)?.programs?.value?.find { it.isLive }
    }

    fun scrollChannelsLooped(current: NetChannel, dir: Int, genre: NetGenre?): NetChannel? {
        val list = channels.value.data.filter { ch->
            UserLocalData.inst.isInGenre(ch, genre)
        }
        val ind = list.indexOfFirst {
            it.id == current.id
        }
        val next = if (ind >= 0) list.getOrNull(ind + dir) else null
        return (next ?: if (dir > 0) list.firstOrNull() else list.lastOrNull())?.takeIf { it.id != current.id }
    }

    fun clear() {
        _channels.value = NetChannelsList()
        programsMap.clear()
    }
}