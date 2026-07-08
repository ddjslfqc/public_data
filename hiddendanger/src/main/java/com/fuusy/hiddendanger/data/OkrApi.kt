package com.fuusy.hiddendanger.data

import com.fuusy.common.network.BaseResp
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * OKR 移动端接口，基础路径 `/mobile/okr`。
 * 与后端 OkrController 文档保持一致。
 */
interface OkrApi {

    @GET("mobile/okr/my-goal")
    suspend fun getMyGoal(
        @Query("periodType") periodType: String? = null
    ): BaseResp<MyGoalResponse>

    @GET("mobile/okr/detail/{id}")
    suspend fun getObjectiveDetail(
        @Path("id") objectiveId: Long
    ): BaseResp<OkrObjective>

    @GET("mobile/okr/align-options")
    suspend fun getAlignOptions(): BaseResp<AlignOptionsResponse>

    /** 获取指定用户可对齐的 KR 列表 */
    @GET("mobile/okr/alignable-krs")
    suspend fun getAlignableKrs(
        @Query("userId") userId: Long
    ): BaseResp<List<AlignableKr>>

    /** 按部门/人员筛选可对齐目标（含 KR） */
    @GET("mobile/okr/align-objectives")
    suspend fun getAlignObjectives(
        @Query("deptId") deptId: Long? = null,
        @Query("targetUserId") targetUserId: Long? = null
    ): BaseResp<List<OkrObjective>>

    @POST("mobile/okr/create")
    suspend fun createObjective(
        @Body body: CreateObjectiveRequest
    ): BaseResp<Long>

    @GET("mobile/okr/pending/kr/user")
    suspend fun getPendingKrs(): BaseResp<List<PendingKrItem>>

    @POST("mobile/okr/kr/approve")
    suspend fun approveKr(
        @Body body: KrApproveRequest
    ): BaseResp<Any?>

    /** 直接更新 KR 进度（无需审核时使用） */
    @PUT("mobile/okr/kr/progress")
    suspend fun updateKrProgress(
        @Body body: KrUpdateProgressRequest
    ): BaseResp<Any?>

    @Multipart
    @POST("mobile/okr/kr/attachment/upload")
    suspend fun uploadKrAttachment(
        @Query("krId") krId: Long,
        @Part file: MultipartBody.Part
    ): BaseResp<OkrAttachmentDto>

    /** 创建进度更新记录（需上级审核） */
    @POST("mobile/okr/update-record/create")
    suspend fun createUpdateRecord(
        @Body body: CreateUpdateRecordRequest
    ): BaseResp<Long>

    @GET("mobile/okr/update-record/pending")
    suspend fun getPendingUpdateRecords(): BaseResp<List<PendingUpdateRecordItem>>

    @POST("mobile/okr/update-record/approve")
    suspend fun approveUpdateRecord(
        @Body body: UpdateRecordApproveRequest
    ): BaseResp<Any?>

    @POST("mobile/okr/update-record/reject")
    suspend fun rejectUpdateRecord(
        @Body body: UpdateRecordApproveRequest
    ): BaseResp<Any?>

    @GET("mobile/okr/update-record/list")
    suspend fun getUpdateRecordList(
        @Query("okrType") okrType: String,
        @Query("okrId") okrId: Long
    ): BaseResp<List<OkrUpdateRecordItem>>

    @POST("mobile/okr/kr/comment/create")
    suspend fun createKrComment(
        @Body body: KrCommentCreateRequest
    ): BaseResp<Long>

    @GET("mobile/okr/kr/comment/list/{krId}")
    suspend fun getKrCommentList(
        @Path("krId") krId: Long
    ): BaseResp<List<OkrKrComment>>

    @GET("mobile/okr/kr/comment/received")
    suspend fun getReceivedComments(): BaseResp<List<OkrKrComment>>

    @GET("mobile/okr/kr/comment/sent")
    suspend fun getSentComments(): BaseResp<List<OkrKrComment>>

    @DELETE("mobile/okr/kr/comment/delete/{commentId}")
    suspend fun deleteKrComment(
        @Path("commentId") commentId: Long
    ): BaseResp<Any?>

    // --- 360 互评 / 周会复盘 ---

    @GET("mobile/okr/peer-eval/summary")
    suspend fun getPeerEvalSummary(
        @Query("period") period: String
    ): BaseResp<PeerEvalSummary>

    @GET("mobile/okr/peer-eval/review-prep")
    suspend fun getReviewPrep(
        @Query("period") period: String
    ): BaseResp<OkrReviewPrep>

    @PUT("mobile/okr/peer-eval/review-prep")
    suspend fun saveReviewPrep(
        @Body body: OkrReviewPrepRequest
    ): BaseResp<OkrReviewPrep>

    @GET("mobile/okr/peer-eval/tasks")
    suspend fun getPeerEvalTasks(
        @Query("period") period: String
    ): BaseResp<List<PeerEvalTask>>

    @POST("mobile/okr/peer-eval/submit")
    suspend fun submitPeerEval(
        @Body body: PeerEvalSubmitRequest
    ): BaseResp<Long>

    @GET("mobile/okr/peer-eval/submission")
    suspend fun getPeerEvalSubmission(
        @Query("period") period: String,
        @Query("targetUserId") targetUserId: Long
    ): BaseResp<PeerEvalSubmissionDetail>

    @GET("mobile/okr/peer-eval/received")
    suspend fun getPeerEvalReceived(
        @Query("period") period: String
    ): BaseResp<PeerEvalReceivedResponse>

    @POST("mobile/okr/peer-eval/add-collaborator")
    suspend fun addPeerCollaborator(
        @Body body: AddCollaboratorRequest
    ): BaseResp<Any?>

    @GET("mobile/okr/peer-eval/colleagues")
    suspend fun getPeerEvalColleagues(): BaseResp<List<PeerEvalColleague>>
}
