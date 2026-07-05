package com.fuusy.common.network

import retrofit2.http.POST

/** 移动端认证接口，与 login 模块保持一致 */
interface MobileAuthApi {

    @POST("mobile/auth/logout")
    suspend fun logout(): BaseResp<Any?>
}
