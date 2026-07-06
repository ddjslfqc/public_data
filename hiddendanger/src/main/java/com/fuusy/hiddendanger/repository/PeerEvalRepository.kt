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
import com.fuusy.hiddendanger.data.PeerEvalSubmitRequest
import com.fuusy.hiddendanger.data.PeerEvalSummary
import com.fuusy.hiddendanger.data.PeerEvalTask
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PeerEvalRepository(app: Application) {

    private val local = PeerEvalLocalStore(app)
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
        apiCallOrLocal(
            apiCall = {
                val resp = api.getPeerEvalSummary(period)
                if (resp.isSuccess && resp.data != null) resp.data!!
                else throw IllegalStateException(resp.errorMsg ?: "加载失败")
            },
            localCall = { local.getSummary(period) }
        )

    suspend fun getReviewPrep(period: String): Result<OkrReviewPrep> =
        apiCallOrLocal(
            apiCall = {
                val resp = api.getReviewPrep(period)
                if (resp.isSuccess) {
                    resp.data ?: OkrReviewPrep(period = period, phase = "PRE_MEETING")
                } else throw IllegalStateException(resp.errorMsg ?: "加载失败")
            },
            localCall = {
                local.getReviewPrep(period) ?: OkrReviewPrep(
                    period = period,
                    phase = "POST_MEETING"
                )
            }
        )

    suspend fun saveReviewPrep(request: OkrReviewPrepRequest): Result<OkrReviewPrep> {
        val users = loadUserDirectory()
        return try {
            val resp = api.saveReviewPrep(request)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data!!)
            else Result.failure(IllegalStateException(resp.errorMsg ?: "保存失败"))
        } catch (e: Exception) {
            runCatching {
                val prep = local.buildPrepFromRequest(request, users)
                local.saveReviewPrep(prep)
                prep
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(e) }
            )
        }
    }

    suspend fun getTasks(period: String): Result<List<PeerEvalTask>> =
        apiCallOrLocal(
            apiCall = {
                val resp = api.getPeerEvalTasks(period)
                if (resp.isSuccess) resp.data.orEmpty()
                else throw IllegalStateException(resp.errorMsg ?: "加载失败")
            },
            localCall = { local.getTasks(period) }
        )

    suspend fun submitEval(request: PeerEvalSubmitRequest): Result<Unit> =
        try {
            val resp = api.submitPeerEval(request)
            if (resp.isSuccess) Result.success(Unit)
            else Result.failure(IllegalStateException(resp.errorMsg ?: "提交失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** 获取所有同事（优先新接口，失败时回退 align-options） */
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

    private suspend fun loadUserDirectory(): List<OkrPeerUser> =
        getColleagues().getOrElse { emptyList() }

    private suspend inline fun <T> apiCallOrLocal(
        crossinline apiCall: suspend () -> T,
        crossinline localCall: suspend () -> T
    ): Result<T> = try {
        Result.success(apiCall())
    } catch (e: Exception) {
        runCatching { localCall() }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(e) }
        )
    }

    private fun AlignOptionsResponse.toPeerUsers(): List<OkrPeerUser> =
        users.orEmpty().map { it.toPeerUser() }

    private fun OkrUser.toPeerUser(): OkrPeerUser =
        OkrPeerUser(userId = id, nickName = displayName, deptName = null)
}
