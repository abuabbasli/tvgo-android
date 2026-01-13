package com.mc2soft.ontv.common.stalker_portal.entities

import com.mc2soft.ontv.common.BaseApp

@kotlinx.serialization.Serializable
data class NetMessageData(
    //val additional_services_on: String? = null, //"1"
    val auto_hide_timeout: Long? = null, //"0",
    val event: String? = null, //"send_msg",
    val id: Int? = null, //"43829",
    val msg: String? = null, //"test",
    val msgs: Int? = null, //1,
    val need_confirm: String? = null, //"1",
    //val param1: String? = null, //"",
    //val reboot_after_ok: String? = null, //"0",
    val valid_until: Long? = null //1679385347
    ) {

    enum class EventType {
        send_msg,
        update_subscription
    }
    val eventType: EventType?
        get() = event?.let {
            try {
                val ev = EventType.valueOf(it)
                if (ev == EventType.send_msg && msg == "Tariff plan is changed, please restart your STB")
                    EventType.update_subscription
                else
                    ev
            } catch (ex: java.lang.Exception) {
                BaseApp.handleError(ex, false, false)
                return null
            }
        }

    val isNeedConfirm: Boolean
        get() = need_confirm == "1"
}


@kotlinx.serialization.Serializable
data class NetMessage(val data: NetMessageData?)