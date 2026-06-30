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

interface WorkOrderApi {

    @GET("mobile/workorder/list")
    suspend fun list(@Query("status") status: String? = null): BaseResp<List<WorkOrderListDto>>

    @GET("mobile/workorder/all")
    suspend fun all(@Query("status") status: String? = null): BaseResp<List<WorkOrderListDto>>

    @GET("mobile/workorder/detail/{id}")
    suspend fun detail(@Path("id") id: String): BaseResp<WorkOrderDetailDto>

    @POST("mobile/workorder/create")
    suspend fun create(@Body body: CreateWorkOrderRequest): BaseResp<CreateWorkOrderResult>

    @GET("mobile/workorder/options")
    suspend fun options(): BaseResp<WorkOrderOptionsDto>

    @POST("mobile/workorder/approve")
    suspend fun approve(@Body body: ApproveWorkOrderRequest): BaseResp<Any?>

    @Multipart
    @POST("mobile/workorder/attachment/upload")
    suspend fun uploadAttachment(
        @Query("workOrderId") workOrderId: String,
        @Part file: MultipartBody.Part
    ): BaseResp<WorkOrderAttachmentDto>

    @DELETE("mobile/workorder/attachment/{id}")
    suspend fun deleteAttachment(@Path("id") id: String): BaseResp<Any?>
}
