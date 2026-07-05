package com.fuusy.project.workorder

import com.google.gson.annotations.SerializedName

data class WorkOrderListDto(
    val id: String,
    val no: String? = null,
    val title: String? = null,
    val brief: String? = null,
    val typeCode: String? = null,
    val typeName: String? = null,
    val priority: String? = null,
    val project: String? = null,
    val expectedCompletionTime: String? = null,
    val status: String? = null,
    val statusLabel: String? = null,
    val recordCreator: String? = null,
    val recordCreatorName: String? = null,
    val recordTime: String? = null,
    val responsibleDept: String? = null,
    val responsibleDeptName: String? = null,
    val rectificationPerson: String? = null,
    val rectificationPersonName: String? = null
)

data class WorkOrderDetailDto(
    val id: String,
    val no: String? = null,
    val title: String? = null,
    val brief: String? = null,
    val typeCode: String? = null,
    val typeName: String? = null,
    val priority: String? = null,
    val project: String? = null,
    val expectedCompletionTime: String? = null,
    val status: String? = null,
    val statusLabel: String? = null,
    val recordCreator: String? = null,
    val recordCreatorName: String? = null,
    val recordTime: String? = null,
    val responsibleDept: String? = null,
    val responsibleDeptName: String? = null,
    val rectificationPerson: String? = null,
    val rectificationPersonName: String? = null,
    val attachments: List<WorkOrderAttachmentDto>? = null,
    val approvalRecords: List<Any>? = null,
    val rejectRecords: List<WorkOrderOperationRecordDto>? = null
)

/** 操作记录（驳回 / 认领），见 workorder-operation-api.md §2.6 */
data class WorkOrderOperationRecordDto(
    val id: Long? = null,
    val workOrderNo: String? = null,
    val operationType: String? = null,
    val rejectBy: String? = null,
    val rejectByName: String? = null,
    val rejectTime: String? = null,
    val rejectReason: String? = null
)

data class RejectWorkOrderRequest(
    val workOrderId: String,
    val rejectReason: String
)

data class WorkOrderAttachmentDto(
    val id: String? = null,
    val fileName: String? = null,
    val filePath: String? = null,
    val fileType: String? = null
)

data class CreateWorkOrderRequest(
    val title: String,
    val brief: String,
    val typeCode: String,
    val responsibleDept: String,
    val rectificationPerson: String,
    val priority: String? = null,
    val project: String? = null,
    val expectedCompletionTime: String? = null
)

data class CreateWorkOrderResult(
    val id: String,
    val no: String? = null
)

data class ApproveWorkOrderRequest(
    val workOrderId: String,
    val approvalResult: String,
    val approvalOpinion: String? = null
)

data class OptionItemDto(
    val value: String,
    val label: String
)

data class WorkOrderOptionsDto(
    @SerializedName("types")
    val types: List<OptionItemDto>? = null,
    val priorities: List<OptionItemDto>? = null,
    val departments: List<OptionItemDto>? = null
)
