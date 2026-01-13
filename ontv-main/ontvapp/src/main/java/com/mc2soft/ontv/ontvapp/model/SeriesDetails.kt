package com.mc2soft.ontv.ontvapp.model

import com.mc2soft.ontv.common.stalker_portal.AuthorizedUser
import com.mc2soft.ontv.common.stalker_portal.entities.NetEpisode
import com.mc2soft.ontv.common.stalker_portal.entities.NetMovieOrSeries
import com.mc2soft.ontv.common.stalker_portal.entities.NetSeason
import kotlinx.coroutines.*

class SeriesDetails(val series: NetMovieOrSeries, val seasons: List<Season>) {
    class Season(val season: NetSeason, val episodes: List<NetEpisode>)

    companion object {
        suspend fun load(series: NetMovieOrSeries): SeriesDetails = coroutineScope {
            val seasons = AuthorizedUser.getSeriesSeasons(series.id)
            val seasonsList = seasons.map {
                async {
                    Season(it, AuthorizedUser.getSeriesSeasonEpisodes(series.id, it.id))
                }
            }.map {
                it.await()
            }
            SeriesDetails(series, seasonsList)
        }
    }

    val isEmpty: Boolean
        get() = seasons.firstOrNull()?.episodes?.firstOrNull() == null
}