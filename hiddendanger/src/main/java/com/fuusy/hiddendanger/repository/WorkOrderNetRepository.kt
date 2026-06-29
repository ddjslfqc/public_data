package com.fuusy.hiddendanger.repository

import com.fuusy.common.network.ServerConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import DetailedLoggingInterceptor

class WorkOrderNetRepository {
    private val api: WorkOrderApi by lazy {
        // 创建 OkHttp 客户端，禁用重定向
        val okHttpClient =
            okhttp3.OkHttpClient.Builder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(false) // 禁用重定向，解决 "Too many follow-up requests" 问题
                .addInterceptor(DetailedLoggingInterceptor())
                .build()

        Retrofit.Builder().baseUrl(ServerConfig.getWorkOrderBaseUrl()) // 使用全局服务器配置
            .client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
            .create(WorkOrderApi::class.java)
    }

    suspend fun getHiddenDangerCount(): Int? {
        return try {
            val resp = api.getHiddenDangerCount()
            if (resp.code == 200) resp.data else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun logoutApi(): Boolean {
        return try {
            val resp = api.logout()
            resp.code == 200
        } catch (e: Exception) {
            false
        }
    }

    interface WorkOrderApi {
        @GET("/work/getHiddenDangerCount")
        suspend fun getHiddenDangerCount(): HiddenDangerCountResp

        @GET("/auth/logout")
        suspend fun logout(): LogoutApiResp
    }

    data class HiddenDangerCountResp(
        val code: Int, val status: String, val data: Int
    )

    data class LogoutApiResp(
        val code: Int, val status: String
    )
} 