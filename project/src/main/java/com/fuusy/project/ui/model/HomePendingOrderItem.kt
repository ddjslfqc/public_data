package com.fuusy.project.ui.model

import com.fuusy.common.data.WorkOrderItem

data class HomePendingOrderItem(
    val title: String,
    val time: String,
    val status: String,
    val workOrder: WorkOrderItem? = null
)

data class HomeLeaderItem(
    val name: String,
    val initial: String,
    val taskCount: Int,
    val avatarBgRes: Int,
    val rank: Int
)
