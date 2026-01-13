package com.mc2soft.ontv.ontvapp.player.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.mc2soft.ontv.common.ui.visible
import com.mc2soft.ontv.ontvapp.R
import com.mc2soft.ontv.ontvapp.databinding.PlayerContentInfoBinding
import com.mc2soft.ontv.ontvapp.model.LocalizeStringMap
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.player.PlayerView
import com.mc2soft.ontv.ontvapp.ui.load

class PlayerContentInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    val vb = PlayerContentInfoBinding.inflate(LayoutInflater.from(context), this, true)

    lateinit var player: PlayerView

    fun tick() {
        val ps = player.playbackSource
        when (ps) {
            is PlaybackSource.ChannelPlaybackSource -> {
                vb.title.text = LocalizeStringMap.translate(ps.channel.name)

                ps.programOrLiveProgram?.let {
                    vb.program1.update(it)
                    vb.program1.visible(true)
                } ?: run {
                    vb.program1.visible(false)
                }

                ps.nextProgramInList()?.let {
                    vb.program2.update(it)
                    vb.program2.visible(true)
                } ?: run {
                    vb.program2.visible(false)
                }

                vb.icon.load(ps.channel.playerLogoUrl, R.drawable.player_content_image_placeholder)
            }
            is PlaybackSource.MoviePlaybackSource -> {
                vb.title.text = LocalizeStringMap.translate(ps.movie.name)
                vb.program1.visible(false)
                vb.program2.visible(false)
                vb.icon.load(ps.movie.playerLogoUrl)
            }
            is PlaybackSource.SeriesPlaybackSource -> {
                vb.title.text = (LocalizeStringMap.translate(ps.season?.season_original_name) ?: "") + " " + (LocalizeStringMap.translate(ps.episode?.name) ?: "")
                vb.program1.visible(false)
                vb.program2.visible(false)
                vb.icon.load(ps.series.playerLogoUrl)
            }
            else -> {
                vb.title.text = null
                vb.program1.visible(false)
                vb.program2.visible(false)
                vb.icon.load(null)
            }
        }
    }
}