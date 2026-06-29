package com.fuusy.project.workorder

import com.fuusy.common.data.Attachment
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus

object WorkOrderMapper {

    fun apiStatusToLocal(status: String?): WorkOrderStatus = when (status?.uppercase()) {
        "DRAFT" -> WorkOrderStatus.DRAFT
        "PENDING" -> WorkOrderStatus.PENDING
        "PROCESSING" -> WorkOrderStatus.PROCESSING
        "PENDING_EVALUATION" -> WorkOrderStatus.EVAL
        "COMPLETED" -> WorkOrderStatus.COMPLETED
        "REJECTED" -> WorkOrderStatus.REJECT
        else -> WorkOrderStatus.PENDING
    }

    fun localStatusToQuery(status: WorkOrderStatus?): String? = when (status) {
        WorkOrderStatus.DRAFT -> "draft"
        WorkOrderStatus.PENDING, WorkOrderStatus.SUBMITTED -> "pending"
        WorkOrderStatus.PROCESSING -> "processing"
        WorkOrderStatus.EVAL -> "pending_evaluation"
        WorkOrderStatus.COMPLETED -> "completed"
        WorkOrderStatus.REJECT -> "rejected"
        else -> null
    }

    fun listDtoToItem(dto: WorkOrderListDto): WorkOrderItem = WorkOrderItem(
        id = dto.id,
        workOrderNo = dto.no,
        hiddenDangerName = dto.title,
        hiddenDangerDescription = dto.brief,
        workOrderType = dto.typeName,
        hiddenDangerCategory = dto.typeName,
        typeCode = dto.typeCode,
        priority = dto.priority,
        projectName = dto.project,
        expectedCompleteTime = dto.expectedCompletionTime,
        status = apiStatusToLocal(dto.status),
        nodeName = dto.statusLabel,
        submitUser = dto.recordCreatorName.orEmpty(),
        submitTime = dto.recordTime.orEmpty(),
        responsibleDepartment = dto.responsibleDeptName,
        responsibleDeptId = dto.responsibleDept,
        responsiblePerson = dto.rectificationPersonName ?: dto.rectificationPerson
    )

    fun detailDtoToItem(dto: WorkOrderDetailDto): WorkOrderItem {
        val rejectRecord = dto.approvalRecords
            ?.filterIsInstance<Map<*, *>>()
            ?.lastOrNull { (it["approvalResult"] as? String)?.uppercase() == "REJECT" }
        return WorkOrderItem(
            id = dto.id,
            workOrderNo = dto.no,
            hiddenDangerName = dto.title,
            hiddenDangerDescription = dto.brief,
            workOrderType = dto.typeName,
            hiddenDangerCategory = dto.typeName,
            typeCode = dto.typeCode,
            priority = dto.priority,
            projectName = dto.project,
            expectedCompleteTime = dto.expectedCompletionTime,
            status = apiStatusToLocal(dto.status),
            nodeName = dto.statusLabel,
            submitUser = dto.recordCreatorName.orEmpty(),
            submitTime = dto.recordTime.orEmpty(),
            responsibleDepartment = dto.responsibleDeptName,
            responsibleDeptId = dto.responsibleDept,
            responsiblePerson = dto.rectificationPersonName ?: dto.rectificationPerson,
            reasonAnalysis = dto.reason,
            hazardConsequence = dto.consequences,
            controlLevel = dto.controlLevel,
            treatmentRequirement = dto.rectificationScheme,
            unitSystem = dto.device,
            attachments = dto.attachments?.map { att ->
                Attachment(
                    fileName = att.fileName,
                    size = att.fileType,
                    url = att.filePath
                )
            },
            rejectionReason = rejectRecord?.get("approvalOpinion") as? String
        )
    }
}
