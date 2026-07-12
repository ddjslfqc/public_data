package com.fuusy.hiddendanger.repository

import com.fuusy.common.network.BaseResp
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdHeaderInterceptor
import com.fuusy.hiddendanger.data.AlignOptionsResponse
import com.fuusy.hiddendanger.data.AlignableKr
import com.fuusy.hiddendanger.data.CreateObjectiveRequest
import com.fuusy.hiddendanger.data.CreateUpdateRecordRequest
import com.fuusy.hiddendanger.data.KrApproveRequest
import com.fuusy.hiddendanger.data.KrCommentCreateRequest
import com.fuusy.hiddendanger.data.KrUpdateProgressRequest
import com.fuusy.hiddendanger.data.MyGoalResponse
import com.fuusy.hiddendanger.data.OkrAlignmentTreeResponse
import com.fuusy.hiddendanger.data.OkrApi
import com.fuusy.hiddendanger.data.OkrAttachmentDto
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.data.OkrKrDetailResponse
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.OkrUpdateRecordItem
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.PeerEvalOrgOverviewResponse
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.data.PendingUpdateRecordItem
import com.fuusy.hiddendanger.data.UpdateRecordApproveRequest
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrNavHelper
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
            .baseUrl(ServerConfig.getOkrBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OkrApi::class.java)
    }

    suspend fun getMyGoal(periodType: String?): Result<MyGoalResponse> =
        safeCall { api.getMyGoal(periodType) }

    suspend fun getAlignmentTree(
        periodType: String,
        deptId: Long? = null
    ): Result<OkrAlignmentTreeResponse> =
        safeCall { api.getAlignmentTree(periodType, deptId) }

    /** 员工名录，用于补全组织 OKR 负责人显示名 */
    suspend fun getColleagueDirectory(): Result<Map<Long, String>> = try {
        val resp = api.getPeerEvalColleagues()
        if (!resp.isSuccess) {
            Result.failure(IllegalStateException(resp.errorMsg ?: "加载员工名录失败(${resp.errorCode})"))
        } else {
            val map = resp.data.orEmpty().associate { colleague ->
                colleague.id to colleague.displayName()
            }
            Result.success(map)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getPeerEvalOrgOverview(
        period: String,
        deptId: Long? = null
    ): Result<PeerEvalOrgOverviewResponse> =
        safeCall { api.getPeerEvalOrgOverview(period, deptId) }

    suspend fun getPeerEvalOrgReviewPrep(
        period: String,
        userId: Long
    ): Result<OkrReviewPrep?> = try {
        val resp = api.getPeerEvalOrgReviewPrep(period, userId)
        if (resp.isSuccess) {
            Result.success(resp.data)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "加载复盘失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getObjectiveDetail(objectiveId: Long): Result<OkrObjective> =
        safeCall { api.getObjectiveDetail(objectiveId) }

    suspend fun getKrDetail(krId: Long): Result<OkrKrDetailResponse> = try {
        val resp = api.getKrDetail(krId)
        if (resp.isSuccess && resp.data != null) {
            Result.success(resp.data!!)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "KR不存在"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getAlignOptions(): Result<AlignOptionsResponse> =
        safeCall { api.getAlignOptions() }

    suspend fun getDeptOptions(): Result<List<OkrDepartment>> =
        safeListCall { api.getDeptOptions() }

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
            UpdateRecordApproveRequest(recordId = recordId, remark = remark)
        )
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "审批失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun rejectUpdateRecord(recordId: Long, remark: String?): Result<Unit> = try {
        val resp = api.rejectUpdateRecord(
            UpdateRecordApproveRequest(recordId = recordId, remark = remark)
        )
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "驳回失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getUpdateRecordList(okrType: String, okrId: Long): Result<List<OkrUpdateRecordItem>> =
        safeListCall { api.getUpdateRecordList(okrType, okrId) }

    suspend fun createKrComment(krId: Long, content: String): Result<Long> =
        safeCall { api.createKrComment(KrCommentCreateRequest(krId, content)) }

    suspend fun getKrCommentList(krId: Long): Result<List<OkrKrComment>> =
        safeListCall { api.getKrCommentList(krId) }

    suspend fun getReceivedComments(): Result<List<OkrKrComment>> =
        safeListCall { api.getReceivedComments() }

    suspend fun getSentComments(): Result<List<OkrKrComment>> =
        safeListCall { api.getSentComments() }

    suspend fun deleteKrComment(commentId: Long): Result<Unit> = try {
        val resp = api.deleteKrComment(commentId)
        if (resp.isSuccess) Result.success(Unit)
        else Result.failure(IllegalStateException(resp.errorMsg ?: "删除失败(${resp.errorCode})"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 按 krId 解析完整 KR，优先 KR 详情接口，失败时回退 my-goal 扫描 */
    suspend fun findKrItem(krId: Long, periodEndDate: String? = null): Result<GoalKrItem> {
        getKrDetail(krId).fold(
            onSuccess = { return Result.success(KrNavHelper.goalKrItem(it, periodEndDate)) },
            onFailure = { /* 回退 */ }
        )
        return findKrItemFromMyGoal(krId)
    }

    private suspend fun findKrItemFromMyGoal(krId: Long): Result<GoalKrItem> {
        return try {
            val resp = api.getMyGoal(null)
            if (!resp.isSuccess) {
                Result.failure(IllegalStateException(resp.errorMsg ?: "加载失败(${resp.errorCode})"))
            } else {
                val data = resp.data
                if (data == null) {
                    Result.failure(IllegalStateException("未找到该 KR"))
                } else {
                    val objectives = buildList {
                        data.currentObjective?.let { add(it) }
                        addAll(data.objectives.orEmpty())
                    }.distinctBy { it.id }
                    val matched = objectives.firstNotNullOfOrNull { objective ->
                        objective.keyResults.orEmpty()
                            .find { it.id == krId }
                            ?.let { kr -> KrNavHelper.goalKrItem(objective, kr) }
                    }
                    matched?.let { Result.success(it) }
                        ?: Result.failure(IllegalStateException("未找到该 KR"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
