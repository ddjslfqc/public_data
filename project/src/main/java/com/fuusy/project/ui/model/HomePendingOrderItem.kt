package com.fuusy.project.ui.model

import com.fuusy.common.data.WorkOrderItem

data class HomePendingOrderItem(
    val title: String,
    val time: String,
    val status: String,
    val workOrder: WorkOrderItem? = null
) {
    companion object {
        fun fromWorkOrder(order: WorkOrderItem): HomePendingOrderItem =
            HomePendingOrderItem(
                title = order.hiddenDangerName?.takeIf { it.isNotBlank() } ?: "工单",
                time = order.submitTime ?: "",
                status = order.nodeName?.takeIf { it.isNotBlank() } ?: order.status.displayName,
                workOrder = order
            )
    }
}

data class HomeLeaderItem(
    val name: String,
    val initial: String,
    val taskCount: Int,
    val avatarBgRes: Int,
    val rank: Int
)
