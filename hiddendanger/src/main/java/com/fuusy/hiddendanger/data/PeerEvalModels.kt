package com.fuusy.hiddendanger.data

import com.google.gson.annotations.SerializedName

/** 周会复盘 / Q2 产出（话题 1） */
data class OkrReviewPrep(
    val period: String,
    val projectOutput: String? = null,
    val skillGrowth: String? = null,
    val collaborators: List<OkrPeerUser>? = null,
    /** PRE_MEETING / POST_MEETING / CLOSED */
    val phase: String? = null,
    val deadline: String? = null
)

data class OkrReviewPrepRequest(
    val period: String,
    val projectOutput: String,
    val skillGrowth: String,
    val collaboratorUserIds: List<Long>
)

data class OkrPeerUser(
    val userId: Long,
    val nickName: String? = null,
    val deptName: String? = null
) {
    val displayName: String
        get() = nickName?.takeIf { it.isNotBlank() } ?: "用户$userId"
}

/** GET /mobile/okr/peer-eval/colleagues 返回的同事项 */
data class PeerEvalColleague(
    val id: Long,
    val userName: String? = null,
    val nickName: String? = null,
    val deptId: Long? = null,
    val deptName: String? = null
) {
    fun toPeerUser(): OkrPeerUser = OkrPeerUser(
        userId = id,
        nickName = nickName?.takeIf { it.isNotBlank() } ?: userName,
        deptName = deptName
    )
}

data class PeerEvalTask(
    val taskId: Long? = null,
    val targetUserId: Long,
    val targetUserName: String? = null,
    val deptName: String? = null,
    val period: String,
    /** PENDING / DONE */
    val status: String = "PENDING",
    val submittedAt: String? = null
) {
    val isDone: Boolean get() = status.equals("DONE", ignoreCase = true)
}

data class PeerEvalScoreItem(
    val itemId: String,
    val score: Int
)

data class PeerEvalSubmitRequest(
    val period: String,
    val targetUserId: Long,
    val scores: List<PeerEvalScoreItem>,
    val highlight: String? = null,
    val suggestion: String? = null
) {
    val averageScore: Double
        get() {
            if (scores.isEmpty()) return 0.0
            return scores.map { it.score }.average()
        }
}

data class PeerEvalSummary(
    val period: String,
    val phase: String? = null,
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val reviewPrepCompleted: Boolean = false,
    /** 收到同事评价的人数（匿名汇总） */
    val receivedEvaluatorCount: Int = 0,
    /** 收到同事评价的综合均分 */
    val receivedAverageScore: Double? = null
)

/** GET /mobile/okr/peer-eval/submission 我发出的评价详情 */
data class PeerEvalSubmissionDetail(
    val submissionId: Long? = null,
    val period: String,
    val targetUserId: Long,
    val targetUserName: String? = null,
    val deptName: String? = null,
    val scores: List<PeerEvalScoreItem> = emptyList(),
    val highlight: String? = null,
    val suggestion: String? = null,
    val averageScore: Double? = null,
    val submittedAt: String? = null
)

/** GET /mobile/okr/peer-eval/received 我收到的同事评价（匿名汇总） */
data class PeerEvalReceivedResponse(
    val period: String,
    val evaluatorCount: Int = 0,
    val averageScore: Double = 0.0,
    val publishedAt: String? = null,
    val scoreBreakdown: List<PeerEvalScoreBreakdownItem> = emptyList(),
    val highlights: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)

data class PeerEvalScoreBreakdownItem(
    val itemId: String,
    val itemTitle: String? = null,
    val averageScore: Double
)

data class AddCollaboratorRequest(
    val period: String,
    @SerializedName("userId") val userId: Long
)
