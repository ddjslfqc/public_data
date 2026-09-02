package com.fuusy.hiddendanger.data

import com.fuusy.common.network.BaseResp
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 日报 / 报告归档接口，与 Web 管理端对齐（日报看板已下线）。 */
interface DailyReportApi {

    /** 用户填写纯文本日报并直接保存（与 Web 一致） */
    @POST("mobile/daily-report")
    suspend fun saveDailyReport(
        @Body body: SaveDailyReportRequest
    ): BaseResp<SaveDailyReportResult>

    /** 整理日报前归一化项目名（与 Web normalize-site-names 一致） */
    @POST("mobile/daily-report/normalize-site-names")
    suspend fun normalizeSiteNames(
        @Body body: NormalizeSiteNamesRequest
    ): BaseResp<NormalizeSiteNamesData>

    @GET("mobile/daily-report/{id}")
    suspend fun getDetail(
        @Path("id") id: Long
    ): BaseResp<DailyReportDetail>

    @PUT("mobile/daily-report/{id}")
    suspend fun updateDailyReport(
        @Path("id") id: Long,
        @Body body: UpdateDailyReportRequest
    ): BaseResp<SaveDailyReportResult>

    @DELETE("mobile/daily-report/{id}")
    suspend fun deleteDailyReport(
        @Path("id") id: Long
    ): BaseResp<Unit?>

    /**
     * 报告归档列表（人员档案）。
     * App「我的报告」固定传 userId=本人，与 Web 对齐。
     */
    @GET("mobile/report-archive")
    suspend fun getReportArchive(
        @Query("type") type: String? = null,
        @Query("userId") userId: Long? = null,
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null
    ): BaseResp<ReportArchiveData>

    /** 周报定稿入库（归档列表读此表） */
    @POST("mobile/period-report/weekly")
    suspend fun saveWeeklyPeriod(
        @Body body: SaveWeeklyPeriodRequest
    ): BaseResp<SavePeriodReportResult>

    /** 月报定稿入库（归档列表读此表） */
    @POST("mobile/period-report/monthly")
    suspend fun saveMonthlyPeriod(
        @Body body: SaveMonthlyPeriodRequest
    ): BaseResp<SavePeriodReportResult>
}
