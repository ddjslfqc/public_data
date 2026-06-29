package com.fuusy.hiddendanger.ui.model

import com.fuusy.common.data.WorkOrderItem

data class EvaluationRecordSummary(
    val averageRating: String,
    val positiveRate: String,
    val totalCount: Int
)

data class EvaluationRecordItem(
    val id: String,
    val reviewerName: String,
    val reviewerInitial: String,
    val avatarColor: Int,
    val department: String,
    val date: String,
    val rating: Float,
    val content: String,
    val workOrderTitle: String,
    val workOrder: WorkOrderItem? = null
)
