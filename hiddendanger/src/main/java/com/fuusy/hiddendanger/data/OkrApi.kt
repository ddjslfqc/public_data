package com.fuusy.hiddendanger.data

import com.fuusy.common.network.BaseResp
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OkrApi {

    @GET("mobile/okr/my-goal")
    suspend fun getMyGoal(
        @Query("periodType") periodType: String? = null
    ): BaseResp<MyGoalResponse>

    @GET("mobile/okr/align-options")
    suspend fun getAlignOptions(): BaseResp<AlignOptionsResponse>

    @GET("mobile/okr/alignable-krs")
    suspend fun getAlignableKrs(
        @Query("deptId") deptId: Long? = null,
        @Query("targetUserId") targetUserId: Long? = null
    ): BaseResp<List<AlignableKr>>

    @POST("mobile/okr/create")
    suspend fun createObjective(
        @Body body: CreateObjectiveRequest
    ): BaseResp<Long>

    @GET("mobile/okr/pending/kr/user")
    suspend fun getPendingKrs(): BaseResp<List<PendingKrItem>>

    @POST("mobile/okr/kr/approve")
    suspend fun approveKr(
        @Body body: KrApproveRequest
    ): BaseResp<Any?>
}
