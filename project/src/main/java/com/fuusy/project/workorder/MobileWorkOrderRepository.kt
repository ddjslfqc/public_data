package com.fuusy.project.workorder

import com.fuusy.common.network.BaseResp
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdHeaderInterceptor
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class MobileWorkOrderRepository {

    /** 当前用户工单，需 X-User-Id */
    private val authApi: WorkOrderApi by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.getBaseUrl())
            .client(
                RetrofitManager.client.newBuilder()
                    .addInterceptor(UserIdHeaderInterceptor())
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WorkOrderApi::class.java)
    }

    /** 全部工单，无需 X-User-Id */
    private val publicApi: WorkOrderApi by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.getBaseUrl())
            .client(RetrofitManager.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WorkOrderApi::class.java)
    }

    suspend fun list(status: WorkOrderStatus? = null): Result<List<WorkOrderItem>> =
        safeList { authApi.list(WorkOrderMapper.localStatusToQuery(status)) }
            .map { list -> list.map { WorkOrderMapper.listDtoToItem(it) } }

    /** 兼容旧调用：拉当前用户全部状态工单 */
    suspend fun listAll(): Result<List<WorkOrderItem>> = list(null)

    suspend fun listPublic(status: WorkOrderStatus? = null): Result<List<WorkOrderItem>> =
        safeList { publicApi.all(WorkOrderMapper.localStatusToQuery(status)) }
            .map { list -> list.map { WorkOrderMapper.listDtoToItem(it) } }

    suspend fun detail(id: String): Result<WorkOrderItem> =
        safeCall { authApi.detail(id) }.map { WorkOrderMapper.detailDtoToItem(it) }

    suspend fun create(body: CreateWorkOrderRequest): Result<CreateWorkOrderResult> =
        safeCall { authApi.create(body) }

    suspend fun options(): Result<WorkOrderOptionsDto> =
        safeCall { publicApi.options() }

    suspend fun approve(
        workOrderId: String,
        approve: Boolean,
        opinion: String? = null
    ): Result<Unit> = try {
        val resp = authApi.approve(
            ApproveWorkOrderRequest(
                workOrderId = workOrderId,
                approvalResult = if (approve) "APPROVE" else "REJECT",
                approvalOpinion = opinion?.trim()?.ifBlank { null }
            )
        )
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "审批失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun uploadAttachment(workOrderId: String, file: File): Result<WorkOrderAttachmentDto> =
        safeCall {
            val body = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, body)
            authApi.uploadAttachment(workOrderId, part)
        }

    suspend fun deleteAttachment(id: String): Result<Unit> = try {
        val resp = authApi.deleteAttachment(id)
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "删除失败"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend inline fun <T> safeCall(
        crossinline block: suspend () -> BaseResp<T>
    ): Result<T> = try {
        val resp = block()
        if (resp.isSuccess && resp.data != null) {
            Result.success(resp.data!!)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "请求失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend inline fun <T> safeList(
        crossinline block: suspend () -> BaseResp<List<T>>
    ): Result<List<T>> = try {
        val resp = block()
        if (resp.isSuccess) Result.success(resp.data.orEmpty())
        else Result.failure(IllegalStateException(resp.errorMsg ?: "请求失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
