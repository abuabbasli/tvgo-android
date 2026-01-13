package com.mc2soft.ontv.ontvapp.analytics

import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.api.StalkerPortalNetApi
import com.mc2soft.ontv.common.stalker_portal.entities.NetChannel
import com.mc2soft.ontv.common.stalker_portal.entities.NetVOD
import kotlinx.coroutines.launch
import timber.log.Timber

object Analytics {
    fun analyticsOnChannelTryGetLink(channel: NetChannel?) {
        channel ?: return
        Timber.i("analyticsOnChannelTryGetLink ${channel.id}")
        AuthorizedUser.scope.launch {
            val auth = AuthorizedUser.auth ?: return@launch
            StalkerPortalNetApi.analyticsOnChannelTryGetLink(auth, channel.id)
        }
    }

    fun analyticsOnChannelPlayableLinkObtained(channel: NetChannel?, link_id: Int?) {
        channel ?: return
        link_id ?: return
        Timber.i("analyticsOnChannelPlayableLinkObtained ${channel.id} $link_id")
        AuthorizedUser.scope.launch {
            val auth = AuthorizedUser.auth ?: return@launch
            StalkerPortalNetApi.analyticsOnChannelStartPlay(auth, channel.id, link_id, channel.id)
        }
    }

    fun analyticsOnChannelStartPlayMulticast(channel: NetChannel?) {
        channel ?: return
        val url = channel.playableUrl ?: channel.cmdFixed ?: return
        Timber.i("analyticsOnChannelStartPlayMulticast ${channel.id} $url")
        AuthorizedUser.scope.launch {
            val auth = AuthorizedUser.auth ?: return@launch
            StalkerPortalNetApi.analyticsOnChannelStartPlayMulticast(auth, channel.id, url)
        }
    }

    fun analyticsSetLastPlayedChannel(channel: NetChannel?) {
        channel ?: return
        Timber.i("analyticsSetLastPlayedChannel ${channel.id}")
        AuthorizedUser.scope.launch {
            val auth = AuthorizedUser.auth ?: return@launch
            StalkerPortalNetApi.analyticsSetLastPlayedChannel(auth, channel.id)
        }
    }

    fun analyticsOnVodPlaybackEnded(vod: NetVOD?) {
        vod ?: return
        val video_id = vod.video_id ?: return
        Timber.i("analyticsOnVodPlaybackEnded $video_id")
        AuthorizedUser.scope.launch {
            val auth = AuthorizedUser.auth ?: return@launch
            StalkerPortalNetApi.analyticsOnVodPlaybackEnded(auth, video_id)
        }
    }

    fun analyticsOnVodPlaybackNotEnded(vod: NetVOD?, seek_seek: Int) {
        vod ?: return
        val video_id = vod.video_id ?: return
        Timber.i("analyticsOnVodPlaybackNotEnded $video_id ${vod.id}")
        AuthorizedUser.scope.launch {
            val auth = AuthorizedUser.auth ?: return@launch
            StalkerPortalNetApi.analyticsOnVodPlaybackNotEnded(auth, video_id, seek_seek, vod.id)
        }
    }
}