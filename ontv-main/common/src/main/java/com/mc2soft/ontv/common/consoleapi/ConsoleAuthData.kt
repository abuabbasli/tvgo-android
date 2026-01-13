package com.mc2soft.ontv.common.consoleapi

import com.mc2soft.ontv.common.CryptoUtils
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
data class ConsoleAuthData(val mac: String, val token: String) {
    companion object {
        fun deserialize(str: String?): ConsoleAuthData? {
            return try {
                str?.let {
                    Json { ignoreUnknownKeys = true }.decodeFromString<ConsoleAuthData>(it)
                }
            } catch (ex: Exception) {
                return null
            }
        }

        fun genToken(mac: String): String {
            return CryptoUtils.md5("X$9aQzz#rDwWG2*3_$mac")
        }
    }

    fun serialize(): String {
        return Json.encodeToString(this)
    }
}