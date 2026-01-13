package com.mc2soft.ontv.common.stalker_portal.entities

@kotlinx.serialization.Serializable
data class NetProfile(val id: Int,
                      var name: String? = null,
                      var sname: String? = null,
                      val parent_password: String? = null)




