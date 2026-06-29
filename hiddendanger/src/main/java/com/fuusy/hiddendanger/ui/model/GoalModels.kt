package com.fuusy.hiddendanger.ui.model

data class GoalKrItem(
    val title: String,
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
