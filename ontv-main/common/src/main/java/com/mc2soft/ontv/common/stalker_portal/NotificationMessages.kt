package com.mc2soft.ontv.common.stalker_portal

import com.mc2soft.ontv.common.BaseApp
import com.mc2soft.ontv.common.stalker_portal.entities.NetMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object NotificationMessages {
    const val READ_NET_MESSAGES_POOL_DELAY = 60000L

    private val _netMessage = MutableStateFlow<NetMessage?>(null)
    val netMessage = _netMessage.asStateFlow()

    private var requestNetMessagesJob: Job? = null
    private var markMessageReadJob: Job? = null

    fun triggerReadNetMessages() {
        if (!AuthorizedUser.isStartupSuccess)return
        if (requestNetMessagesJob?.isActive == true)return
        if (_netMessage.value != null)return
        requestNetMessagesJob = AuthorizedUser.scope.launch {
            try {
                markMessageReadJob?.join()
                _netMessage.value = AuthorizedUser.getMessage()
            } catch (ex: java.lang.Exception) {
                BaseApp.handleError(ex)
            }
        }
    }

    fun markMessageReadOrExpire() {
        val msg = netMessage.value?.data ?: return
        val msgId = msg.id
        if (msg.isNeedConfirm && msgId != null) {
            markMessageReadJob = AuthorizedUser.scope.launch {
                _netMessage.value = null
                try {
                    AuthorizedUser.markMessageRead(msgId)
                } catch (ex: java.lang.Exception) {
                    BaseApp.handleError(ex)
                }
                triggerReadNetMessages()
            }
        } else {
            _netMessage.value = null
            triggerReadNetMessages()
        }
    }

    fun clear() {
        requestNetMessagesJob?.cancel()
        requestNetMessagesJob = null
        markMessageReadJob?.cancel()
        markMessageReadJob = null
        _netMessage.value = null
    }
}