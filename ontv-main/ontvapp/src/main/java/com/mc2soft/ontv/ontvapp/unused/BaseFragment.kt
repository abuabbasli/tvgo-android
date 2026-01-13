package com.mc2soft.ontv.ontvapp.unused

import android.os.Bundle
import android.util.Size
import android.view.View
import androidx.fragment.app.Fragment
import com.mc2soft.ontv.common.ui.screenSize
import com.mc2soft.ontv.ontvapp.MainActivity
import com.mc2soft.ontv.ontvapp.player.PlaybackSource
import com.mc2soft.ontv.ontvapp.player.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.lang.ref.WeakReference

//yes it non android arguments or viewmodel style, but arguments & viewmodel is ugly
abstract class BaseFragment<TVM : BaseFragment.BaseVM>(open val vm: TVM) : Fragment() {
    open class BaseVM {
        var fragment: WeakReference<BaseFragment<*>>? = null
        var viewScope: CoroutineScope? = null

        open fun onPreViewCreate() {
        }

        open fun onPostViewDestroy() {
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.tag = ThisIsFragmentViewTag(this)
        vm.fragment = WeakReference(this)
        vm.viewScope = CoroutineScope(Dispatchers.Main)
        vm.onPreViewCreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vm.fragment = null
        vm.viewScope?.cancel()
        vm.viewScope = null
        vm.onPostViewDestroy()
    }

    open val isFocusAvailable: Boolean
        get() = isResumed

    open fun onSaveFocus(){}
    open fun onRestoreFocus(){}

    override fun onResume() {
        super.onResume()
        if (isFocusAvailable) {
            onRestoreFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        onSaveFocus()
    }

    class ThisIsFragmentViewTag(fr: Fragment) {
        val fragment = fr
    }

    val screenSize: Size by lazy {
        requireActivity().screenSize
    }

    val mainActivity: MainActivity?
        get() = (activity as? MainActivity)
    val player: PlayerView?
        get() = mainActivity?.player
    val playbackSource: PlaybackSource?
        get() = player?.playbackSource
    val channelPlaybackSource: PlaybackSource.ChannelPlaybackSource?
        get() = playbackSource as? PlaybackSource.ChannelPlaybackSource
}