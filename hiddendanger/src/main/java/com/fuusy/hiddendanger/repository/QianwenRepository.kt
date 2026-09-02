package com.fuusy.hiddendanger.repository

import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdProvider
import com.fuusy.common.utils.SpUtils
import com.fuusy.hiddendanger.data.DailyReportReviewRequest
import com.fuusy.hiddendanger.data.MonthlyReportDraftRequest
import com.fuusy.hiddendanger.data.QianwenApi
import com.fuusy.hiddendanger.data.QianwenChatRequest
import com.fuusy.hiddendanger.data.QianwenChatResponse
import com.fuusy.hiddendanger.data.QianwenDraftResponse
import com.fuusy.hiddendanger.data.QianwenHistoryItem
import com.fuusy.hiddendanger.data.QianwenStatementRequest
import com.fuusy.hiddendanger.data.QianwenSubmitRequest
import com.fuusy.hiddendanger.data.QianwenSubmitResponse
import com.fuusy.hiddendanger.data.QianwenUsersResponse
import com.fuusy.hiddendanger.data.WeeklyOkrProgressResponse
import com.fuusy.hiddendanger.data.WeeklyReportDraftRequest
import com.google.gson.JsonObject
import okhttp3.Interceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class QianwenRepository {

    private val api: QianwenApi by lazy {
        val apiKey = System.getenv("QIANWEN_API_KEY").orEmpty()
            .ifBlank {
                SpUtils.getString("qianwen_api_key").orEmpty()
            }
        val clientBuilder = RetrofitManager.client.newBuilder()
            .callTimeout(310, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(310, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
        clientBuilder.addInterceptor(Interceptor { chain ->
            val builder = chain.request().newBuilder()
            if (apiKey.isNotBlank()) {
                builder.header("X-API-Key", apiKey)
            }
            currentUserId()?.let { builder.header("X-User-Id", it.toString()) }
            chain.proceed(builder.build())
        })
        Retrofit.Builder()
            .baseUrl(ServerConfig.getQianwenBaseUrl())
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QianwenApi::class.java)
    }

    private fun currentUserId(): Long? = UserIdProvider.current()

    suspend fun health(): Result<Boolean> = runCatching {
        val res = api.health()
        res.status.isNullOrBlank() || res.status.equals("ok", true) ||
            res.status.equals("healthy", true) || res.status.equals("up", true)
    }.recoverCatching {
        // 部分服务 /health 无 body 也算通
        if (it is HttpException && it.code() in 200..299) true else throw it
    }

    suspend fun chat(
        question: String,
        history: List<QianwenHistoryItem>,
        webSearchOn: Boolean = false
    ): Result<QianwenChatResponse> = runCatching {
        api.chat(
            QianwenChatRequest(
                question = question,
                sourceScope = "local",
                webSearchMode = if (webSearchOn) "on" else null,
                history = history
            )
        )
    }.recoverCatching { e -> throw wrapHttp(e) }

    suspend fun workOrderDraft(statement: String): Result<QianwenDraftResponse> =
        runCatching { api.workOrderDraft(QianwenStatementRequest(statement)) }
            .recoverCatching { e -> throw wrapHttp(e) }

    suspend fun workOrderUsers(departmentId: String): Result<QianwenUsersResponse> =
        runCatching { api.workOrderUsers(departmentId) }
            .recoverCatching { e -> throw wrapHttp(e) }

    suspend fun workOrderSubmit(draftId: String, payload: JsonObject): Result<QianwenSubmitResponse> =
        runCatching { api.workOrderSubmit(QianwenSubmitRequest(draftId, payload)) }
            .recoverCatching { e -> throw wrapHttp(e) }

    suspend fun dailyReportReview(
        content: String,
        reportDate: String
    ): Result<com.fuusy.hiddendanger.data.DailyReportReviewResponse> =
        runCatching {
            api.dailyReportReview(
                DailyReportReviewRequest(
                    content = content.trim(),
                    reportDate = reportDate.trim()
                )
            )
        }.recoverCatching { e -> throw wrapHttp(e) }

    suspend fun weeklyReportDraft(
        weekStart: String,
        weekEnd: String
    ): Result<QianwenDraftResponse> =
        runCatching {
            api.weeklyReportDraft(
                WeeklyReportDraftRequest(
                    userId = currentUserId(),
                    weekStart = weekStart,
                    weekEnd = weekEnd
                )
            )
        }.recoverCatching { e -> throw wrapHttp(e) }

    suspend fun weeklyReportSubmit(draftId: String, payload: JsonObject): Result<QianwenSubmitResponse> =
        runCatching { api.weeklyReportSubmit(QianwenSubmitRequest(draftId, payload)) }
            .recoverCatching { e -> throw wrapHttp(e) }

    suspend fun weeklyReportOkrProgress(draftId: String): Result<WeeklyOkrProgressResponse> =
        runCatching { api.weeklyReportOkrProgress(draftId) }
            .recoverCatching { e -> throw wrapHttp(e) }

    suspend fun monthlyReportDraft(month: String): Result<QianwenDraftResponse> =
        runCatching {
            api.monthlyReportDraft(
                MonthlyReportDraftRequest(
                    userId = currentUserId(),
                    month = month
                )
            )
        }.recoverCatching { e -> throw wrapHttp(e) }

    suspend fun monthlyReportSubmit(draftId: String, payload: JsonObject): Result<QianwenSubmitResponse> =
        runCatching { api.monthlyReportSubmit(QianwenSubmitRequest(draftId, payload)) }
            .recoverCatching { e -> throw wrapHttp(e) }

    private fun wrapHttp(e: Throwable): Throwable {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
                ?: body.takeIf { it.isNotBlank() }
                ?: e.message()
            return Exception(detail ?: "请求失败")
        }
        return e
    }
}
