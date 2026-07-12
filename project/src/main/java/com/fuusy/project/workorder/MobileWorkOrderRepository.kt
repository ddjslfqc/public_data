package com.fuusy.project.workorder

import com.fuusy.common.network.BaseResp
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdHeaderInterceptor
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class MobileWorkOrderRepository {

    /** 当前用户工单，需 X-User-Id */
    private val authApi: WorkOrderApi by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.getWorkOrderBaseUrl())
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
            .baseUrl(ServerConfig.getWorkOrderBaseUrl())
            .client(RetrofitManager.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WorkOrderApi::class.java)
    }

    suspend fun list(
        status: WorkOrderStatus? = null,
        scope: String = WorkOrderListScope.RELATED
    ): Result<List<WorkOrderItem>> =
        safeList { authApi.list(WorkOrderMapper.localStatusToQuery(status), scope) }
            .map { list -> list.map { WorkOrderMapper.listDtoToItem(it) } }

    /** 兼容旧调用：拉当前用户相关工单 */
    suspend fun listAll(scope: String = WorkOrderListScope.RELATED): Result<List<WorkOrderItem>> =
        list(null, scope)

    suspend fun listPublic(status: WorkOrderStatus? = null): Result<List<WorkOrderItem>> =
        safeList { publicApi.all(WorkOrderMapper.localStatusToQuery(status)) }
            .map { list -> list.map { WorkOrderMapper.listDtoToItem(it) } }

    suspend fun detail(id: String): Result<WorkOrderItem> =
        safeCall { authApi.detail(id) }.map { WorkOrderMapper.detailDtoToItem(it) }

    suspend fun create(
        body: CreateWorkOrderRequest,
        files: List<File> = emptyList()
    ): Result<CreateWorkOrderResult> = safeCall {
        val json = Gson().toJson(body)
        val dataPart = MultipartBody.Part.createFormData(
            "data",
            null,
            json.toRequestBody("application/json; charset=utf-8".toMediaType())
        )
        val fileParts = files.map { file ->
            val partBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("files", file.name, partBody)
        }
        authApi.create(dataPart, fileParts)
    }

    suspend fun options(): Result<WorkOrderOptionsDto> =
        safeCall { publicApi.options() }

    suspend fun users(deptId: String): Result<List<OptionItemDto>> =
        safeList { publicApi.users(deptId) }

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

    suspend fun claim(workOrderId: String): Result<Unit> = try {
        val resp = authApi.claim(workOrderId)
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "认领失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reject(workOrderId: String, reason: String): Result<Unit> = try {
        val resp = authApi.reject(
            RejectWorkOrderRequest(
                workOrderId = workOrderId,
                rejectReason = reason.trim()
            )
        )
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "驳回失败(${resp.errorCode})"))
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

    suspend fun evaluate(workOrderId: String, score: Int, content: String?): Result<Unit> = try {
        val resp = authApi.evaluate(WorkOrderEvaluateRequest(workOrderId, score, content))
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "评价失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun dashboard(): Result<WorkOrderDashboardDto> = safeCall { authApi.dashboard() }

    suspend fun ranking(limit: Int = 20): Result<List<WorkOrderRankingItemDto>> =
        safeList { authApi.ranking(limit) }

    suspend fun evaluationSummary(type: String): Result<WorkOrderEvaluationSummaryDto> =
        safeCall { authApi.evaluationSummary(type) }

    suspend fun evaluationList(type: String): Result<List<WorkOrderEvaluationItemDto>> =
        safeList { authApi.evaluationList(type) }

    suspend fun archive(): Result<WorkOrderArchiveDto> = safeCall { authApi.archive() }

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
