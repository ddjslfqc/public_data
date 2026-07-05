package com.fuusy.hiddendanger.ui.model

data class GoalKrItem(
    val id: Long = 0,
    val objectiveId: Long = 0,
    val objectiveTitle: String = "",
    val title: String,
    val targetValue: Double = 0.0,
    val currentValue: Double = 0.0,
    val unit: String? = null,
    val weight: Int? = null,
    val status: Int = 0,
    val approvalStatus: Int? = null,
    val userId: Long? = null,
    val progressApprovalStatus: Int? = null,
    val pendingProgressValue: Double? = null,
    val valueLabel: String,
    val progressPercent: Int,
    val achieved: Boolean = false,
    val approvalLabel: String? = null
)

data class GoalPeerItem(
    val name: String,
    val initial: String,
    val avatarColor: Int,
    val date: String,
    val rating: Float
)

data class GoalPeriodData(
    val id: String,
    val summary: String,
    val periodRange: String,
    val progressDetail: String,
    val progressPercent: Int,
    val objectiveTitle: String,
    val createdAt: String,
    val periodLabel: String,
    val currentProgress: String,
    val keyResults: List<GoalKrItem>
)
