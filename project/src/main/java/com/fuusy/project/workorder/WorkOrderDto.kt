package com.fuusy.project.workorder

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
    val reason: String? = null,
    val consequences: String? = null,
    val controlLevel: String? = null,
    val rectificationScheme: String? = null,
    val device: String? = null,
    val attachments: List<WorkOrderAttachmentDto>? = null,
    val approvalRecords: List<Any>? = null
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
    val rectificationPerson: String? = null,
    val priority: String? = null,
    val project: String? = null,
    val expectedCompletionTime: String? = null,
    val reason: String? = null,
    val consequences: String? = null,
    val controlLevel: String? = null,
    val rectificationScheme: String? = null,
    val device: String? = null
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
    val hazardLevels: List<OptionItemDto>? = null,
    val hazardTypes: List<OptionItemDto>? = null,
    val priorities: List<OptionItemDto>? = null,
    val departments: List<OptionItemDto>? = null
)
