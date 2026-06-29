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
    val userId: Long? = null
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
    val name: String
)

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
    val id: Long,
    val approvalStatus: Int,
    val approvalRemark: String? = null
)
