package com.fuusy.project.workorder

import com.fuusy.common.network.BaseResp
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 工单模块 Retrofit 接口，与 [workorder-api.md] 第二节一致。
 * 需登录的接口通过 [com.fuusy.common.network.UserIdHeaderInterceptor] 注入 X-User-Id。
 */
interface WorkOrderApi {

    @GET("mobile/workorder/list")
    suspend fun list(@Query("status") status: String? = null): BaseResp<List<WorkOrderListDto>>

    @GET("mobile/workorder/all")
    suspend fun all(@Query("status") status: String? = null): BaseResp<List<WorkOrderListDto>>

    @GET("mobile/workorder/detail/{id}")
    suspend fun detail(@Path("id") id: String): BaseResp<WorkOrderDetailDto>

    @Multipart
    @POST("mobile/workorder/create")
    suspend fun create(
        /** 工单 JSON，对应后端 @RequestPart("data") */
        @Part data: MultipartBody.Part,
        /** 附件，对应后端 @RequestParam("files") */
        @Part files: List<MultipartBody.Part> = emptyList()
    ): BaseResp<CreateWorkOrderResult>

    @GET("mobile/workorder/options")
    suspend fun options(): BaseResp<WorkOrderOptionsDto>

    @GET("mobile/workorder/users")
    suspend fun users(@Query("deptId") deptId: String): BaseResp<List<OptionItemDto>>

    @POST("mobile/workorder/approve")
    suspend fun approve(@Body body: ApproveWorkOrderRequest): BaseResp<Any?>

    @POST("mobile/workorder/reject")
    suspend fun reject(@Body body: RejectWorkOrderRequest): BaseResp<Any?>

    @POST("mobile/workorder/claim/{id}")
    suspend fun claim(@Path("id") id: String): BaseResp<Any?>

    @Multipart
    @POST("mobile/workorder/attachment/upload")
    suspend fun uploadAttachment(
        @Query("workOrderId") workOrderId: String,
        @Part file: MultipartBody.Part
    ): BaseResp<WorkOrderAttachmentDto>

    @DELETE("mobile/workorder/attachment/{id}")
    suspend fun deleteAttachment(@Path("id") id: String): BaseResp<Any?>
}
