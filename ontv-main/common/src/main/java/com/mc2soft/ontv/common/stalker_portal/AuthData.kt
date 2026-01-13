package com.mc2soft.ontv.common.stalker_portal

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
data class AuthData(val mac: String, val token: String, val server: String) {
    companion object {
        fun deserialize(str: String?): AuthData? {
            return try {
                str?.let {
                    Json { ignoreUnknownKeys = true }.decodeFromString<AuthData>(it)
                }
            } catch (ex: Exception) {
                return null
            }
        }
    }

    fun serialize(): String {
        return Json.encodeToString(this)
    }
}