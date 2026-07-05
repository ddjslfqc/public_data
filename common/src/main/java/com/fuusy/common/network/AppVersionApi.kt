package com.fuusy.common.network

import com.fuusy.common.data.AppVersionCheckResult
import retrofit2.http.GET
import retrofit2.http.Query

interface AppVersionApi {

    @GET("mobile/app-version/check")
    suspend fun checkUpdate(
        @Query("appType") appType: String = "android",
        @Query("versionCode") versionCode: Int
    ): BaseResp<AppVersionCheckResult>
}
