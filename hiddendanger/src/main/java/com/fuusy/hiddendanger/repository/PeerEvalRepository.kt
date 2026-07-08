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

    suspend fun getSummary(period: String, forceRefresh: Boolean = false): Result<PeerEvalSummary> {
        if (!forceRefresh) {
            PeerEvalCache.getSummary<PeerEvalSummary>(period)?.let { return Result.success(it) }
        }
        return safeCall { api.getPeerEvalSummary(period) }.also { result ->
            result.onSuccess { PeerEvalCache.putSummary(period, it) }
        }
    }

    suspend fun getReviewPrep(period: String, forceRefresh: Boolean = false): Result<OkrReviewPrep> {
        if (!forceRefresh) {
            PeerEvalCache.getReviewPrep<OkrReviewPrep>(period)?.let { return Result.success(it) }
        }
        return try {
            val resp = api.getReviewPrep(period)
            if (resp.isSuccess) {
                val prep = resp.data ?: OkrReviewPrep(period = period, phase = "PRE_MEETING")
                PeerEvalCache.putReviewPrep(period, prep)
                Result.success(prep)
            } else {
                Result.failure(IllegalStateException(resp.errorMsg ?: "加载复盘失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveReviewPrep(request: OkrReviewPrepRequest): Result<OkrReviewPrep> =
        safeCall { api.saveReviewPrep(request) }.also { result ->
            result.onSuccess { prep ->
                val period = request.period
                PeerEvalCache.putReviewPrep(period, prep)
                PeerEvalCache.invalidateTasks(period)
                PeerEvalCache.invalidateReviewPrep(period)
            }
        }

    suspend fun getTasks(period: String, forceRefresh: Boolean = false): Result<List<PeerEvalTask>> {
        if (!forceRefresh) {
            PeerEvalCache.getTasks<List<PeerEvalTask>>(period)?.let { return Result.success(it) }
        }
        return safeListCall { api.getPeerEvalTasks(period) }.also { result ->
            result.onSuccess { PeerEvalCache.putTasks(period, it) }
        }
    }

    suspend fun submitEval(request: PeerEvalSubmitRequest): Result<Unit> = try {
        val resp = api.submitPeerEval(request)
        if (resp.isSuccess) {
            PeerEvalCache.invalidateTasks(request.period)
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

    suspend fun getReceivedEval(period: String, forceRefresh: Boolean = false): Result<PeerEvalReceivedResponse> {
        if (!forceRefresh) {
            PeerEvalCache.getReceived<PeerEvalReceivedResponse>(period)?.let { return Result.success(it) }
        }
        return safeCall { api.getPeerEvalReceived(period) }.also { result ->
            result.onSuccess { PeerEvalCache.putReceived(period, it) }
        }
    }

    suspend fun getColleagues(forceRefresh: Boolean = false): Result<List<OkrPeerUser>> {
        if (!forceRefresh) {
            PeerEvalCache.getColleagues<List<OkrPeerUser>>()?.let { return Result.success(it) }
        }
        return try {
            val resp = api.getPeerEvalColleagues()
            if (resp.isSuccess) {
                val users = resp.data.orEmpty().map { it.toPeerUser() }
                PeerEvalCache.putColleagues(users)
                Result.success(users)
            } else {
                loadColleaguesFallback(resp.errorMsg ?: "加载同事列表失败")
            }
        } catch (e: Exception) {
            loadColleaguesFallback(e.message ?: "加载同事列表失败", e)
        }
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
