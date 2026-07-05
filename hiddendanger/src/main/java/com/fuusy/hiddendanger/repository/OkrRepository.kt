package com.fuusy.hiddendanger.repository

import com.fuusy.common.network.BaseResp
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.UserIdHeaderInterceptor
import com.fuusy.hiddendanger.data.AlignOptionsResponse
import com.fuusy.hiddendanger.data.AlignableKr
import com.fuusy.hiddendanger.data.CreateObjectiveRequest
import com.fuusy.hiddendanger.data.CreateUpdateRecordRequest
import com.fuusy.hiddendanger.data.KrApproveRequest
import com.fuusy.hiddendanger.data.KrUpdateProgressRequest
import com.fuusy.hiddendanger.data.MyGoalResponse
import com.fuusy.hiddendanger.data.OkrApi
import com.fuusy.hiddendanger.data.OkrAttachmentDto
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.data.PendingUpdateRecordItem
import com.fuusy.hiddendanger.data.UpdateRecordApproveRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class OkrRepository {

    private val api: OkrApi by lazy {
        val client = RetrofitManager.client.newBuilder()
            .addInterceptor(UserIdHeaderInterceptor())
            .build()
        Retrofit.Builder()
            .baseUrl(com.fuusy.common.network.ServerConfig.getBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OkrApi::class.java)
    }

    suspend fun getMyGoal(periodType: String?): Result<MyGoalResponse> =
        safeCall { api.getMyGoal(periodType) }

    suspend fun getAlignOptions(): Result<AlignOptionsResponse> =
        safeCall { api.getAlignOptions() }

    suspend fun getAlignableKrs(userId: Long): Result<List<AlignableKr>> =
        safeListCall { api.getAlignableKrs(userId) }

    suspend fun getAlignObjectives(deptId: Long?, targetUserId: Long?): Result<List<OkrObjective>> =
        safeListCall { api.getAlignObjectives(deptId, targetUserId) }

    suspend fun createObjective(body: CreateObjectiveRequest): Result<Long> =
        safeCall { api.createObjective(body) }

    suspend fun getPendingKrs(): Result<List<PendingKrItem>> =
        safeListCall { api.getPendingKrs() }

    suspend fun approveKr(body: KrApproveRequest): Result<Unit> = try {
        val resp = api.approveKr(body)
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "审批失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateKrProgress(body: KrUpdateProgressRequest): Result<Unit> = try {
        val resp = api.updateKrProgress(body)
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "更新失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createUpdateRecord(body: CreateUpdateRecordRequest): Result<Long> =
        safeCall { api.createUpdateRecord(body) }

    suspend fun uploadKrAttachment(krId: Long, file: File): Result<OkrAttachmentDto> =
        safeCall {
            val body = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, body)
            api.uploadKrAttachment(krId, part)
        }

    suspend fun getPendingUpdateRecords(): Result<List<PendingUpdateRecordItem>> =
        safeListCall { api.getPendingUpdateRecords() }

    suspend fun approveUpdateRecord(recordId: Long, remark: String?): Result<Unit> = try {
        val resp = api.approveUpdateRecord(
            UpdateRecordApproveRequest(recordId = recordId, approvalRemark = remark)
        )
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "审批失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun rejectUpdateRecord(recordId: Long, remark: String?): Result<Unit> = try {
        val resp = api.rejectUpdateRecord(
            UpdateRecordApproveRequest(recordId = recordId, approvalRemark = remark)
        )
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "驳回失败(${resp.errorCode})"))
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

    private suspend inline fun <T> safeListCall(
        crossinline block: suspend () -> BaseResp<List<T>>
    ): Result<List<T>> = try {
        val resp = block()
        if (resp.isSuccess) {
            Result.success(resp.data.orEmpty())
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "请求失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
