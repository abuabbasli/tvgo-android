package com.mc2soft.ontv.common.consoleapi

@kotlinx.serialization.Serializable
data class NetConsoleCompanyApplication(val id: Long, val name: String, val description: String? = null)

@kotlinx.serialization.Serializable
data class NetConsoleCompany(val id: Long,
                             val name: String,
                             val logo: String? = null,
                             val color_preset: String? = null,
                             val update_server: String? = null,
                             val portal: String? = null,
                             val description: String? = null,
                             val application: List<NetConsoleCompanyApplication>? = null) {
    companion object {
        const val COLOR_PRESET_ONTV = "ONTV"
        const val COLOR_PRESET_TvinTV = "TvinTV"
        const val COLOR_PRESET_BirLinkTV = "BirLink"
    }
}

@kotlinx.serialization.Serializable
data class NetConsoleInfo(val id: Int? = null,
                          val mac: String? = null,
                          val serial: String? = null,
                          val company_id: Int? = null,
                          val company: NetConsoleCompany? = null,
                          val error_text: List<String>? = null) {
    fun isEnableApplication(name_id: String): Boolean {
        return company?.application?.any { it.name == name_id } == true
    }
    fun getApplicationRunPackageName(name_id: String): String? {
        return company?.application?.find { it.name == name_id }?.description
    }
}

@kotlinx.serialization.Serializable
data class NetConsoleInfoResponse(val success: Boolean, val message: NetConsoleInfo?)
