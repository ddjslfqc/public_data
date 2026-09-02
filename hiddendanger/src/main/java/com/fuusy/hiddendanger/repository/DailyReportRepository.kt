package com.fuusy.hiddendanger.repository

import com.fuusy.common.network.BaseResp
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdHeaderInterceptor
import com.fuusy.hiddendanger.data.DailyReportApi
import com.fuusy.hiddendanger.data.DailyReportDetail
import com.fuusy.hiddendanger.data.NormalizeSiteNamesRequest
import com.fuusy.hiddendanger.data.ReportArchiveData
import com.fuusy.hiddendanger.data.SaveDailyReportRequest
import com.fuusy.hiddendanger.data.SaveMonthlyPeriodRequest
import com.fuusy.hiddendanger.data.SavePeriodReportResult
import com.fuusy.hiddendanger.data.SaveWeeklyPeriodRequest
import com.fuusy.hiddendanger.data.UpdateDailyReportRequest
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DailyReportRepository {

    private val api: DailyReportApi by lazy {
        val client = RetrofitManager.client.newBuilder()
            .addInterceptor(UserIdHeaderInterceptor())
            .build()
        Retrofit.Builder()
            .baseUrl(ServerConfig.getWorkOrderBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DailyReportApi::class.java)
    }

    /**
     * 整理日报正文前的项目名归一化。
     * 与 Web：POST /mobile/daily-report/normalize-site-names { texts: [source] }
     */
    suspend fun normalizeSiteNames(source: String): Result<String> = try {
        val resp = api.normalizeSiteNames(
            NormalizeSiteNamesRequest(texts = listOf(source.trim()))
        )
        if (!resp.isSuccess) {
            Result.failure(
                IllegalStateException(resp.errorMsg ?: "项目名称转换失败(${resp.errorCode})")
            )
        } else {
            val content = resp.data?.texts?.firstOrNull()?.trim().orEmpty()
            if (content.isBlank()) {
                Result.failure(IllegalStateException("项目名称转换接口未返回日报正文"))
            } else {
                Result.success(content)
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveDailyReport(
        reportDate: String,
        content: String
    ): Result<Long?> = try {
        val resp = api.saveDailyReport(
            SaveDailyReportRequest(
                reportDate = reportDate.trim(),
                content = content.trim()
            )
        )
        if (resp.isSuccess) {
            Result.success(resp.data?.id)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "日报保存失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveWeeklyPeriod(
        weekStart: String,
        weekEnd: String,
        summary: String,
        issues: String?
    ): Result<SavePeriodReportResult> = try {
        val resp = api.saveWeeklyPeriod(
            SaveWeeklyPeriodRequest(
                weekStart = weekStart.trim(),
                weekEnd = weekEnd.trim(),
                summary = summary.trim(),
                issues = issues?.trim().orEmpty()
            )
        )
        if (resp.isSuccess && resp.data != null) {
            Result.success(resp.data!!)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "周报归档失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveMonthlyPeriod(
        month: String,
        achievements: String,
        issues: String?
    ): Result<SavePeriodReportResult> = try {
        val resp = api.saveMonthlyPeriod(
            SaveMonthlyPeriodRequest(
                month = month.trim(),
                achievements = achievements.trim(),
                issues = issues?.trim().orEmpty(),
                summary = achievements.trim()
            )
        )
        if (resp.isSuccess && resp.data != null) {
            Result.success(resp.data!!)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "月报归档失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getDetail(id: Long): Result<DailyReportDetail> = safeCall {
        api.getDetail(id)
    }

    suspend fun updateDailyReport(
        id: Long,
        reportDate: String,
        content: String
    ): Result<Unit> = try {
        val resp = api.updateDailyReport(
            id,
            UpdateDailyReportRequest(
                reportDate = reportDate.trim(),
                content = content.trim()
            )
        )
        if (resp.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "日报修改失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteDailyReport(id: Long): Result<Unit> = try {
        val resp = api.deleteDailyReport(id)
        if (resp.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "日报删除失败(${resp.errorCode})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getReportArchive(
        type: String? = null,
        userId: Long? = null,
        year: Int? = null,
        month: Int? = null
    ): Result<ReportArchiveData> = safeCall {
        api.getReportArchive(type, userId, year, month)
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
}
