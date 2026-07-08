package com.fuusy.hiddendanger.repository

import android.app.Application
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdHeaderInterceptor
import com.fuusy.common.network.UserIdProvider
import com.fuusy.hiddendanger.data.AlignOptionsResponse
import com.fuusy.hiddendanger.data.OkrApi
import com.fuusy.hiddendanger.data.OkrPeerUser
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.OkrReviewPrepRequest
import com.fuusy.hiddendanger.data.OkrUser
import com.fuusy.hiddendanger.data.PeerEvalReceivedResponse
import com.fuusy.hiddendanger.data.PeerEvalSubmissionDetail
import com.fuusy.hiddendanger.data.PeerEvalSubmitRequest
import com.fuusy.hiddendanger.data.PeerEvalSummary
import com.fuusy.hiddendanger.data.PeerEvalTask
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PeerEvalRepository(app: Application) {

    private val okrRepo = OkrRepository()

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

    suspend fun getSummary(period: String): Result<PeerEvalSummary> =
        safeCall { api.getPeerEvalSummary(period) }

    suspend fun getReviewPrep(period: String): Result<OkrReviewPrep> = try {
        val resp = api.getReviewPrep(period)
        if (resp.isSuccess) {
            Result.success(resp.data ?: OkrReviewPrep(period = period, phase = "PRE_MEETING"))
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "加载复盘失败"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveReviewPrep(request: OkrReviewPrepRequest): Result<OkrReviewPrep> =
        safeCall { api.saveReviewPrep(request) }

    suspend fun getTasks(period: String): Result<List<PeerEvalTask>> =
        safeListCall { api.getPeerEvalTasks(period) }

    suspend fun submitEval(request: PeerEvalSubmitRequest): Result<Unit> = try {
        val resp = api.submitPeerEval(request)
        if (resp.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "提交失败"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getSubmissionDetail(
        period: String,
        targetUserId: Long,
        targetUserName: String? = null,
        deptName: String? = null
    ): Result<PeerEvalSubmissionDetail> = safeCall {
        api.getPeerEvalSubmission(period, targetUserId)
    }.map { detail ->
        if (detail.targetUserName.isNullOrBlank() && !targetUserName.isNullOrBlank()) {
            detail.copy(targetUserName = targetUserName, deptName = detail.deptName ?: deptName)
        } else {
            detail
        }
    }

    suspend fun getReceivedEval(period: String): Result<PeerEvalReceivedResponse> =
        safeCall { api.getPeerEvalReceived(period) }

    suspend fun getColleagues(): Result<List<OkrPeerUser>> =
        try {
            val resp = api.getPeerEvalColleagues()
            if (resp.isSuccess) {
                Result.success(resp.data.orEmpty().map { it.toPeerUser() })
            } else {
                loadColleaguesFallback(resp.errorMsg ?: "加载同事列表失败")
            }
        } catch (e: Exception) {
            loadColleaguesFallback(e.message ?: "加载同事列表失败", e)
        }

    private suspend fun loadColleaguesFallback(
        reason: String,
        cause: Exception? = null
    ): Result<List<OkrPeerUser>> {
        val selfId = UserIdProvider.current()
        return okrRepo.getAlignOptions().map { options ->
            options.toPeerUsers().filter { user ->
                selfId == null || user.userId != selfId
            }
        }.fold(
            onSuccess = {
                if (it.isNotEmpty()) Result.success(it)
                else Result.failure(cause ?: IllegalStateException(reason))
            },
            onFailure = { Result.failure(cause ?: it) }
        )
    }

    private suspend inline fun <T> safeCall(
        crossinline block: suspend () -> com.fuusy.common.network.BaseResp<T>
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
        crossinline block: suspend () -> com.fuusy.common.network.BaseResp<List<T>>
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

    private fun AlignOptionsResponse.toPeerUsers(): List<OkrPeerUser> =
        users.orEmpty().map { it.toPeerUser() }

    private fun OkrUser.toPeerUser(): OkrPeerUser =
        OkrPeerUser(userId = id, nickName = displayName, deptName = null)
}
