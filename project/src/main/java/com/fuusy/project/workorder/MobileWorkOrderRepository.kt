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

    private val api: WorkOrderApi by lazy {
        val client = RetrofitManager.client.newBuilder()
            .addInterceptor(UserIdHeaderInterceptor())
            .build()
        Retrofit.Builder()
            .baseUrl(ServerConfig.getBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WorkOrderApi::class.java)
    }

    suspend fun list(status: WorkOrderStatus? = null): Result<List<WorkOrderItem>> =
        safeList { api.list(WorkOrderMapper.localStatusToQuery(status)) }
            .map { list -> list.map { WorkOrderMapper.listDtoToItem(it) } }

    suspend fun listAll(): Result<List<WorkOrderItem>> =
        safeList { api.list(null) }
            .map { list -> list.map { WorkOrderMapper.listDtoToItem(it) } }

    suspend fun detail(id: String): Result<WorkOrderItem> =
        safeCall { api.detail(id) }.map { WorkOrderMapper.detailDtoToItem(it) }

    suspend fun create(body: CreateWorkOrderRequest): Result<CreateWorkOrderResult> =
        safeCall { api.create(body) }

    suspend fun options(): Result<WorkOrderOptionsDto> =
        safeCall { api.options() }

    suspend fun approve(
        workOrderId: String,
        approve: Boolean,
        opinion: String? = null
    ): Result<Unit> = try {
        val resp = api.approve(
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
            api.uploadAttachment(workOrderId, part)
        }

    suspend fun deleteAttachment(id: String): Result<Unit> = try {
        val resp = api.deleteAttachment(id)
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
