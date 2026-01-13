package com.mc2soft.ontv.common.consoleapi.api

import com.mc2soft.ontv.common.consoleapi.NetConsoleInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface INetConsoleApi {
    @GET("/console/info")
    suspend fun getInfo(@Query("mac") mac: String, @Query("token") token: String): NetConsoleInfoResponse
}