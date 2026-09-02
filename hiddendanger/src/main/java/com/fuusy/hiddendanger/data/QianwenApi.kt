package com.fuusy.hiddendanger.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody

interface QianwenApi {

    @GET("health")
    suspend fun health(): QianwenHealthResponse

    @POST("api/chat")
    suspend fun chat(@Body body: QianwenChatRequest): QianwenChatResponse

    @POST("api/skills/work_order/draft")
    suspend fun workOrderDraft(@Body body: QianwenStatementRequest): QianwenDraftResponse

    @GET("api/skills/work_order/users")
    suspend fun workOrderUsers(@Query("department_id") departmentId: String): QianwenUsersResponse

    @POST("api/skills/work_order/submit")
    suspend fun workOrderSubmit(@Body body: QianwenSubmitRequest): QianwenSubmitResponse

    /** 日报严重逻辑漏洞审查；仅提示，不负责保存 */
    @POST("api/skills/daily_report/review")
    suspend fun dailyReportReview(@Body body: DailyReportReviewRequest): DailyReportReviewResponse

    @POST("api/skills/weekly_report/draft")
    suspend fun weeklyReportDraft(@Body body: WeeklyReportDraftRequest): QianwenDraftResponse

    /** 周报草稿后台 OKR 关联状态（异步轮询，与 Web 一致） */
    @GET("api/skills/weekly_report/draft/{draftId}/okr-progress")
    suspend fun weeklyReportOkrProgress(
        @Path("draftId") draftId: String
    ): WeeklyOkrProgressResponse

    @POST("api/skills/weekly_report/submit")
    suspend fun weeklyReportSubmit(@Body body: QianwenSubmitRequest): QianwenSubmitResponse

    @POST("api/skills/monthly_report/draft")
    suspend fun monthlyReportDraft(@Body body: MonthlyReportDraftRequest): QianwenDraftResponse

    @POST("api/skills/monthly_report/submit")
    suspend fun monthlyReportSubmit(@Body body: QianwenSubmitRequest): QianwenSubmitResponse

    /** 提交文件链接，触发 PDF/Word/图片 OCR 等识别入库 */
    @POST("api/knowledge/ingest-by-url")
    suspend fun ingestByUrl(@Body body: KnowledgeIngestRequest): KnowledgeIngestResponse

    /** 千文直传（部分部署支持 multipart 一步上传+入库） */
    @Multipart
    @POST("api/knowledge/upload")
    suspend fun uploadAndIngest(@Part file: MultipartBody.Part): KnowledgeIngestResponse
}
