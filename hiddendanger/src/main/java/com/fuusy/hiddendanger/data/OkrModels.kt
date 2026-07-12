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
    @SerializedName("userId") val userId: Long? = null,
    val ownerName: String? = null,
    val status: Int? = null,
    val progress: Int? = null,
    val periodType: String? = null,
    val endDate: String? = null,
    val startDate: String? = null
)

data class OkrParentKr(
    val id: Long,
    val title: String,
    val objectiveId: Long? = null,
    val objective: OkrObjectiveBrief? = null
)

data class OkrKeyResult(
    val id: Long = 0,
    val objectiveId: Long? = null,
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
    val pendingProgressValue: Double? = null,
    val pendingApproverHint: String? = null,
    val pendingProgressApproverName: String? = null,
    val pendingProgressApproverRoleLabel: String? = null,
    val nextProgressApproverName: String? = null,
    val nextProgressApproverRoleLabel: String? = null,
    val attachments: List<OkrKrAttachment>? = null,
    val comments: List<OkrKrComment>? = null
)

data class OkrObjective(
    val id: Long,
    val title: String,
    val userId: Long? = null,
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
    val parentObjective: OkrObjectiveBrief? = null,
    val deptId: Long? = null,
    val ownerName: String? = null,
    val keyResults: List<OkrKeyResult>? = null
)

data class MyGoalResponse(
    val periods: List<OkrPeriodOption>? = null,
    val currentObjective: OkrObjective? = null,
    val objectives: List<OkrObjective>? = null
)

/** GET /mobile/okr/alignment-tree Web 对齐树 */
data class OkrAlignmentTreeResponse(
    val periodType: String,
    val periodLabel: String? = null,
    val departments: List<OkrDepartment>? = null,
    val objectives: List<OkrAlignmentObjective> = emptyList(),
    val stats: OkrAlignmentTreeStats? = null
)

/** 与 OkrObjective 一致，补充负责人/部门展示名 */
data class OkrAlignmentObjective(
    val id: Long,
    val title: String,
    val userId: Long? = null,
    val ownerName: String? = null,
    val deptId: Long? = null,
    val deptName: String? = null,
    val periodType: String? = null,
    val periodLabel: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: Int = 0,
    val statusLabel: String? = null,
    val progress: Int = 0,
    val progressText: String? = null,
    val parentKrId: Long? = null,
    val parentKr: OkrParentKr? = null,
    val keyResults: List<OkrKeyResult>? = null
)

data class OkrAlignmentTreeStats(
    val objectiveCount: Int = 0,
    val krCount: Int = 0,
    val rootChainCount: Int = 0,
    val orphanObjectiveCount: Int = 0
)

data class OkrDepartment(
    val id: Long,
    val name: String
)

data class OkrUser(
    val id: Long,
    /** align-options 接口返回的展示名 */
    val name: String? = null,
    @SerializedName("nickName") val nickName: String? = null,
    @SerializedName("userName") val userName: String? = null,
    val deptId: Long? = null
) {
    val displayName: String
        get() = nickName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
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
    val createTime: String? = null,
    val krOwnerName: String? = null,
    val krOwnerDeptName: String? = null,
    val objectiveOwnerName: String? = null,
    val approvalRole: String? = null,
    val approvalRoleLabel: String? = null,
    val contextLine: String? = null
)

data class KrApproveRequest(
    val id: Long,
    val approvalStatus: Int,
    val approvalRemark: String? = null
)

data class KrUpdateProgressRequest(
    val krId: Long,
    val currentValue: Double,
    val progressDescription: String? = null
)

data class OkrUpdateRecordAttachment(
    val id: Long? = null,
    val recordId: Long? = null,
    val type: String? = null,
    val name: String? = null,
    val url: String? = null,
    val createTime: String? = null
)

data class CreateUpdateRecordRequest(
    val okrType: String = "kr",
    val okrId: Long,
    val title: String? = null,
    val description: String? = null,
    val periodType: String? = null,
    val currentValue: Double? = null,
    val content: String? = null,
    val attachments: List<OkrUpdateRecordAttachment>? = null
)

data class UpdateRecordApproveRequest(
    val recordId: Long,
    val remark: String? = null
)

/** GET /update-record/pending 返回项 */
data class PendingUpdateRecordItem(
    val id: Long,
    val okrType: String? = null,
    val okrId: Long? = null,
    val krId: Long? = null,
    val objectiveId: Long? = null,
    val title: String? = null,
    val okrTitle: String? = null,
    val currentValue: Double? = null,
    val newValue: Double? = null,
    val targetValue: Double? = null,
    val unit: String? = null,
    val content: String? = null,
    val updateContent: String? = null,
    val objectiveTitle: String? = null,
    val status: Int? = null,
    val userId: Long? = null,
    val createTime: String? = null,
    val attachments: List<OkrUpdateRecordAttachment>? = null,
    val submitterName: String? = null,
    val submitterDeptName: String? = null,
    val approvalRoleLabel: String? = null,
    val contextLine: String? = null
) {
    val krTitle: String?
        get() = okrTitle ?: title

    val remark: String?
        get() = content ?: updateContent

    val submittedValue: Double?
        get() = newValue ?: currentValue
}

/** GET /update-record/list 及 KR 详情内嵌的更新记录 */
data class OkrUpdateRecordItem(
    val id: Long,
    val okrType: String? = null,
    val okrId: Long? = null,
    val userId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val periodType: String? = null,
    val currentValue: Double? = null,
    val content: String? = null,
    val status: Int? = null,
    val approvalUserId: Long? = null,
    val approvalRemark: String? = null,
    val approvalTime: String? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val attachments: List<OkrUpdateRecordAttachment>? = null
)

data class OkrKrAttachment(
    val id: Long? = null,
    val krId: Long? = null,
    val userId: Long? = null,
    val type: String? = null,
    @SerializedName(value = "name", alternate = ["fileName"])
    val fileName: String? = null,
    @SerializedName(value = "url", alternate = ["fileUrl", "filePath"])
    val fileUrl: String? = null,
    val fileSize: Long? = null,
    val createTime: String? = null
)

/** GET /mobile/okr/kr/detail/{id} 聚合响应 */
data class OkrKrDetailResponse(
    val id: Long,
    val objectiveId: Long,
    val title: String,
    val targetValue: Double = 0.0,
    val weight: Int? = null,
    val currentValue: Double = 0.0,
    val unit: String? = null,
    val status: Int = 0,
    val sortOrder: Int? = null,
    val approvalStatus: Int? = null,
    val approvalUserId: Long? = null,
    val approvalRemark: String? = null,
    val approvalTime: String? = null,
    val userId: Long? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val progressApprovalStatus: Int? = null,
    val pendingProgressValue: Double? = null,
    val pendingApproverHint: String? = null,
    val pendingProgressApproverName: String? = null,
    val pendingProgressApproverRoleLabel: String? = null,
    val nextProgressApproverName: String? = null,
    val nextProgressApproverRoleLabel: String? = null,
    val objective: OkrObjectiveBrief? = null,
    val attachments: List<OkrKrAttachment>? = null,
    val comments: List<OkrKrComment>? = null,
    val updateRecords: List<OkrUpdateRecordItem>? = null
)

data class OkrKrComment(
    val id: Long,
    val krId: Long,
    val userId: Long,
    val content: String,
    val createTime: String? = null,
    val updateTime: String? = null,
    val userName: String? = null,
    val nickName: String? = null,
    val deptId: Long? = null,
    val deptName: String? = null,
    val krTitle: String? = null,
    val krUserId: Long? = null
) {
    val displayName: String
        get() = nickName?.takeIf { it.isNotBlank() }
            ?: userName?.takeIf { it.isNotBlank() }
            ?: "用户$userId"
}

data class KrCommentCreateRequest(
    val krId: Long,
    val content: String
)

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
                    title = objective.title,
                    userId = objective.userId,
                    ownerName = objective.ownerName
                )
            )
        }
    }
