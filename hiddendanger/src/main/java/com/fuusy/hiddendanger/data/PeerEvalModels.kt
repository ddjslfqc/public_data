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
    val reviewPrepCompleted: Boolean = false
)

data class AddCollaboratorRequest(
    val period: String,
    @SerializedName("userId") val userId: Long
)
