package com.fuusy.hiddendanger.ui.model

import androidx.annotation.DrawableRes

data class ArchiveEvaluationItem(
    val reviewerName: String,
    val reviewerInitial: String,
    val avatarColor: Int,
    val rating: Float,
    val tag: String?,
    val tagTextColor: Int,
    @DrawableRes val tagBackgroundRes: Int,
    val content: String,
    val meta: String,
    val workOrderId: String? = null
)

data class ArchiveDistributionItem(
    val label: String,
    val count: Int
)
