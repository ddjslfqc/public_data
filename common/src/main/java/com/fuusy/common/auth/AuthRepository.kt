package com.fuusy.common.auth

import com.fuusy.common.network.MobileAuthApi
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthRepository {

    private val api: MobileAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.getWorkOrderBaseUrl())
            .client(RetrofitManager.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MobileAuthApi::class.java)
    }

    /** 调用服务端登出；失败不影响本地清会话 */
    suspend fun logoutRemote(): Result<Unit> = try {
        val resp = api.logout()
        if (resp.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "退出登录失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
