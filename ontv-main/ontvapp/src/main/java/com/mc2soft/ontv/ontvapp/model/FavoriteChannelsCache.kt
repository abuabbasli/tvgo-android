package com.mc2soft.ontv.ontvapp.model

import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FavoriteChannelsCache {
    private val _overlaps = MutableStateFlow(emptyMap<Int, Boolean>())
    val overlaps = _overlaps.asStateFlow()

    fun isInFav(ch: NetChannel?): Boolean {
        ch ?: return false
        return overlaps.value.get(ch.id) ?: ch.isFav
    }

    fun setFav(ch: NetChannel, fav: Boolean) {
        _overlaps.value = _overlaps.value.toMutableMap().apply {
            put(ch.id, fav)
        }
        AuthorizedUser.scope.launch(ChannelsCache.channelsOpsDispatcher) {
            upload()
        }
    }

    fun clear() {
        _overlaps.value = emptyMap()
    }

    suspend fun upload() {
        if (overlaps.value.isEmpty())return
        val allChannels = ChannelsCache.channels.value.data
        if (allChannels.isEmpty())return
        val favlist = allChannels.mapNotNull {
            if (isInFav(it)) it.id else null
        }
        AuthorizedUser.setFavoriteChannelList(favlist)
    }

    fun removeOldRecords() {
        val allChannels = ChannelsCache.channels.value.data
        _overlaps.value = _overlaps.value.filter { record->
            allChannels.find { it.id == record.key }?.let { it.isFav != record.value } ?: true
        }
    }
}