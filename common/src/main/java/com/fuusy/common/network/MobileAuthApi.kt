package com.fuusy.common.network

import retrofit2.http.GET
import retrofit2.http.POST

/** 移动端认证接口，与 login 模块保持一致 */
interface MobileAuthApi {

    @POST("mobile/auth/logout")
    suspend fun logout(): BaseResp<Any?>

    /** 当前用户资料（含是否部门负责人 deptLeader） */
    @GET("mobile/auth/profile")
    suspend fun profile(): BaseResp<AuthProfileResp>
}

data class AuthProfileResp(
    val userId: Long? = null,
    val username: String? = null,
    val nickName: String? = null,
    val deptId: Long? = null,
    val deptName: String? = null,
    val deptLeader: Boolean? = null
)
