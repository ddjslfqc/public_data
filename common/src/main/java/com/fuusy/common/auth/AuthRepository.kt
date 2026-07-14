package com.fuusy.common.auth

import com.fuusy.common.network.AuthProfileResp
import com.fuusy.common.network.MobileAuthApi
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdHeaderInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthRepository {

    private fun createApi(): MobileAuthApi {
        val client = RetrofitManager.client.newBuilder()
            .addInterceptor(UserIdHeaderInterceptor())
            .build()
        return Retrofit.Builder()
            .baseUrl(ServerConfig.getWorkOrderBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MobileAuthApi::class.java)
    }

    /** 调用服务端登出；失败不影响本地清会话 */
    suspend fun logoutRemote(): Result<Unit> = try {
        val resp = createApi().logout()
        if (resp.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "退出登录失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 刷新是否部门负责人等资料标志 */
    suspend fun refreshProfile(): Result<AuthProfileResp> = try {
        val resp = createApi().profile()
        val data = resp.data
        if (resp.isSuccess && data != null) {
            DeptRoleHelper.setDeptLeader(data.deptLeader == true)
            Result.success(data)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "获取用户资料失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
