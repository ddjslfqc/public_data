package com.fuusy.hiddendanger.data

import com.google.gson.annotations.SerializedName

data class OkrPeriodOption(
    val label: String,
    val value: String,
    val active: Boolean = false
)

data class OkrObjectiveBrief(
    val id: Long,
    val title: String,
    @SerializedName("userId") val userId: Long? = null
)

data class OkrParentKr(
    val id: Long,
    val title: String,
    val objectiveId: Long? = null,
    val objective: OkrObjectiveBrief? = null
)

data class OkrKeyResult(
    val id: Long = 0,
    val title: String,
    val targetValue: Double = 0.0,
    val weight: Int? = null,
    val currentValue: Double = 0.0,
    val unit: String? = null,
    val status: Int = 0,
    val approvalStatus: Int? = null,
    val achieved: Boolean = false,
    val sortOrder: Int? = null,
    val userId: Long? = null,
    val progressApprovalStatus: Int? = null,
    val pendingProgressValue: Double? = null
)

data class OkrObjective(
    val id: Long,
    val title: String,
    val createTime: String? = null,
    val periodType: String? = null,
    val periodLabel: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: Int = 0,
    val statusLabel: String? = null,
    val progress: Int = 0,
    val approvalStatus: Int? = null,
    val completedKrCount: Int? = null,
    val totalKrCount: Int? = null,
    val progressText: String? = null,
    val parentId: Long? = null,
    val parentKrId: Long? = null,
    val parentKr: OkrParentKr? = null,
    val keyResults: List<OkrKeyResult>? = null
)

data class MyGoalResponse(
    val periods: List<OkrPeriodOption>? = null,
    val currentObjective: OkrObjective? = null,
    val objectives: List<OkrObjective>? = null
)

data class OkrDepartment(
    val id: Long,
    val name: String
)

data class OkrUser(
    val id: Long,
    @SerializedName("nickName") val nickName: String? = null,
    @SerializedName("userName") val userName: String? = null,
    val deptId: Long? = null
) {
    val displayName: String
        get() = nickName?.takeIf { it.isNotBlank() }
            ?: userName?.takeIf { it.isNotBlank() }
            ?: "用户$id"
}

data class AlignOptionsResponse(
    val departments: List<OkrDepartment>? = null,
    val users: List<OkrUser>? = null
)

data class AlignableKr(
    val id: Long,
    val title: String,
    val targetValue: Double? = null,
    val currentValue: Double? = null,
    val unit: String? = null,
    val status: Int? = null,
    val objective: OkrObjectiveBrief? = null
)

data class CreateKrRequest(
    val title: String,
    val targetValue: Double,
    val weight: Int? = null,
    val unit: String? = null,
    val sortOrder: Int? = null,
    val userId: Long? = null
)

data class CreateObjectiveRequest(
    val title: String,
    val description: String? = null,
    val periodType: String,
    val startDate: String,
    val endDate: String,
    val deptId: Long,
    val parentKrId: Long? = null,
    val objectiveType: Int? = null,
    val keyResults: List<CreateKrRequest>? = null
)

data class PendingKrItem(
    val id: Long,
    val objectiveId: Long,
    val title: String,
    val targetValue: Double? = null,
    val weight: Int? = null,
    val currentValue: Double? = null,
    val unit: String? = null,
    val status: Int? = null,
    val approvalStatus: Int? = null,
    val userId: Long? = null,
    val objectiveTitle: String? = null,
    val objectiveUserId: Long? = null,
    val deptId: Long? = null,
    val createTime: String? = null
)

data class KrApproveRequest(
    val krId: Long,
    val approvalStatus: Int,
    val approvalRemark: String? = null
)

data class KrUpdateProgressRequest(
    val krId: Long,
    val currentValue: Double,
    val progressDescription: String? = null
)

data class CreateUpdateRecordRequest(
    val okrType: String = "kr",
    val okrId: Long,
    val updateContent: String? = null,
    val oldValue: Double? = null,
    val newValue: Double? = null,
    val attachments: List<Long>? = null
)

data class UpdateRecordApproveRequest(
    val recordId: Long,
    val approvalRemark: String? = null
)

/** GET /update-record/pending 返回项 */
data class PendingUpdateRecordItem(
    val id: Long,
    val krId: Long? = null,
    val objectiveId: Long? = null,
    val title: String? = null,
    val currentValue: Double? = null,
    val newValue: Double? = null,
    val targetValue: Double? = null,
    val unit: String? = null,
    val updateContent: String? = null,
    val objectiveTitle: String? = null,
    val userId: Long? = null,
    val createTime: String? = null
) {
    val remark: String?
        get() = updateContent
}

data class OkrAttachmentDto(
    val id: Long? = null,
    val fileName: String? = null,
    @SerializedName("fileUrl") val fileUrl: String? = null,
    @SerializedName("filePath") val filePath: String? = null,
    val fileSize: Long? = null,
    val fileType: String? = null
)

/** 将 align-objectives 返回的目标列表展平为可对齐 KR 列表 */
fun List<OkrObjective>.toAlignableKrs(): List<AlignableKr> =
    flatMap { objective ->
        objective.keyResults.orEmpty().map { kr ->
            AlignableKr(
                id = kr.id,
                title = kr.title,
                targetValue = kr.targetValue,
                currentValue = kr.currentValue,
                unit = kr.unit,
                status = kr.status,
                objective = OkrObjectiveBrief(
                    id = objective.id,
                    title = objective.title
                )
            )
        }
    }
