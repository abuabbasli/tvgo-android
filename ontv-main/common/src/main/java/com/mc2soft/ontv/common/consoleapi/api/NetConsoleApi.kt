package com.mc2soft.ontv.common.consoleapi.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mc2soft.ontv.common.BuildConfig
import com.mc2soft.ontv.common.consoleapi.ConsoleAuthData
import com.mc2soft.ontv.common.consoleapi.NetConsoleInfo
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit


object NetConsoleApi {
    const val SERVER_URL = "https://ps.ags.az"

    val netapi: INetConsoleApi by lazy {
        val logInterceptor = HttpLoggingInterceptor().apply {
            setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC)
        }
        val okHttpClientBuilder = OkHttpClient.Builder().apply { addNetworkInterceptor(logInterceptor) }

        val contentType = "application/json; charset=utf-8".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(SERVER_URL)
            .addConverterFactory(Json{
                    ignoreUnknownKeys = true
                }.asConverterFactory(contentType))
            .client(okHttpClientBuilder.build())
            .build()

        retrofit.create(INetConsoleApi::class.java)
    }

    suspend fun getInfo(auth: ConsoleAuthData): NetConsoleInfo {
        val resp = netapi.getInfo(auth.mac, auth.token)
        if (!resp.success) {
            throw java.lang.Exception(resp.message?.error_text?.firstOrNull())
        } else {
            resp.message?.let {
                return it
            }
            throw java.lang.Exception("no response")
        }
    }
}