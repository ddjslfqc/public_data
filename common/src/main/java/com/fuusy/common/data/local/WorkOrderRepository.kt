package com.fuusy.common.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkOrderRepository(private val db: AppDatabase) {
    suspend fun saveWorkOrder(
        formJson: String, attachments: List<Pair<String, String>> // Pair<localPath, type>
    ): Long = withContext(Dispatchers.IO) {
        val orderId = db.workOrderDao().insert(WorkOrderEntity(formJson = formJson))
        val attachList = attachments.map { (path, type) ->
            AttachmentEntity(workOrderId = orderId, localPath = path, type = type)
        }
        db.attachmentDao().insertAll(attachList)
        orderId
    }

    suspend fun saveWorkOrderWithStatus(
        formJson: String,
        attachments: List<Pair<String, String>>,
        status: com.fuusy.common.data.WorkOrderStatus
    ): Long = withContext(Dispatchers.IO) {
        val orderId = db.workOrderDao().insert(
            WorkOrderEntity(formJson = formJson, status = status.toString().toLowerCase())
        )
        val attachList = attachments.map { (path, type) ->
            AttachmentEntity(workOrderId = orderId, localPath = path, type = type)
        }
        db.attachmentDao().insertAll(attachList)
        orderId
    }

    suspend fun getAllWorkOrders(): List<Pair<WorkOrderEntity, List<AttachmentEntity>>> =
        withContext(Dispatchers.IO) {
            db.workOrderDao().getAll().map { order ->
                order to db.attachmentDao().getByWorkOrderId(order.id)
            }
        }

    suspend fun getWorkOrderDetail(orderId: Long): Pair<WorkOrderEntity?, List<AttachmentEntity>> =
        withContext(Dispatchers.IO) {
            val order = db.workOrderDao().get(orderId)
            val attachments = db.attachmentDao().getByWorkOrderId(orderId)
            order to attachments
        }

    suspend fun deleteWorkOrder(order: WorkOrderEntity) = withContext(Dispatchers.IO) {
        db.workOrderDao().delete(order)
    }

    suspend fun submitWorkOrder(orderId: Long) = withContext(Dispatchers.IO) {
        val order = db.workOrderDao().get(orderId)
        if (order != null) {
            db.workOrderDao().update(order.copy(status = "submitted"))
        }
    }

    suspend fun deleteWorkOrderById(id: String) {
        db.workOrderDao().deleteById(id)
    }

    suspend fun getWorkOrdersByStatus(status: com.fuusy.common.data.WorkOrderStatus): List<Pair<WorkOrderEntity, List<AttachmentEntity>>> =
        withContext(Dispatchers.IO) {
            db.workOrderDao().getByStatus(status.toString().toLowerCase()).map { order ->
                order to db.attachmentDao().getByWorkOrderId(order.id)
            }
        }

    suspend fun getWorkOrdersByStatusList(statusList: List<com.fuusy.common.data.WorkOrderStatus>): List<Pair<WorkOrderEntity, List<AttachmentEntity>>> =
        withContext(Dispatchers.IO) {
            db.workOrderDao().getByStatusList(statusList.map { it.toString().toLowerCase() })
                .map { order ->
                    order to db.attachmentDao().getByWorkOrderId(order.id)
                }
        }
} 