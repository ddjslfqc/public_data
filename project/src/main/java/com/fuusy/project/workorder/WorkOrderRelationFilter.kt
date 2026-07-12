package com.fuusy.project.workorder

import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderOptions
import com.fuusy.common.data.WorkOrderStatus

/** 判断工单是否与当前用户相关（我提报的 / 指派我处理的） */
object WorkOrderRelationFilter {

    fun isRelated(order: WorkOrderItem, userId: String): Boolean {
        if (userId.isBlank()) return false

        val creatorId = order.recordCreatorId?.takeIf { it.isNotBlank() }
        val handlerId = order.rectificationPersonId
        val isCreator = creatorId == userId
        val isAssignedHandler =
            !WorkOrderOptions.isPublicGrabPerson(handlerId) && handlerId == userId

        if (isAssignedHandler) return true

        if (isCreator) {
            // 我提交的工单：若已指派他人处理中，不在「我相关」里展示
            return order.status != WorkOrderStatus.PROCESSING
        }
        return false
    }

    fun filter(orders: List<WorkOrderItem>, userId: String?): List<WorkOrderItem> {
        if (userId.isNullOrBlank()) return orders
        return orders.filter { isRelated(it, userId) }
    }
}
