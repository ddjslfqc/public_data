package com.fuusy.project.workorder

import com.fuusy.common.data.Attachment
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus

object WorkOrderMapper {

    /** 将表单字段映射为文档定义的 create 请求体 */
    fun buildCreateRequest(
        form: Map<String, String>,
        projectName: String?,
        projectDevice: String?
    ): CreateWorkOrderRequest {
        fun opt(key: String): String? = form[key]?.trim()?.takeIf { it.isNotBlank() }

        return CreateWorkOrderRequest(
            title = form["hiddenDangerName"].orEmpty(),
            brief = form["hiddenDangerDescription"].orEmpty(),
            typeCode = opt("workOrderType") ?: opt("typeCode").orEmpty(),
            responsibleDept = opt("responsibleDepartment") ?: opt("responsibleDept").orEmpty(),
            rectificationPerson = opt("responsiblePerson") ?: opt("rectificationPerson"),
            priority = opt("priority"),
            project = projectName?.trim()?.takeIf { it.isNotBlank() },
            expectedCompletionTime = normalizeDateTime(
                opt("expectedCompletionTime") ?: opt("expectedCompleteTime")
            ),
            reason = opt("reasonAnalysis") ?: opt("reason"),
            consequences = opt("hazardConsequence") ?: opt("consequences"),
            controlLevel = opt("controlLevel") ?: opt("hiddenDangerLevel") ?: opt("hazardLevel"),
            rectificationScheme = opt("treatmentRequirement") ?: opt("rectificationScheme"),
            device = opt("unitSystem") ?: opt("device") ?: projectDevice?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun normalizeDateTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when {
            raw.contains('T') -> raw
            raw.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) ->
                raw.replace(' ', 'T')
            raw.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}")) ->
                raw.replace('.', '-').replace(' ', 'T') + ":00"
            else -> raw
        }
    }

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
        WorkOrderStatus.PENDING -> "pending"
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
                    id = att.id,
                    fileName = att.fileName,
                    size = att.fileType,
                    url = att.filePath
                )
            },
            rejectionReason = rejectRecord?.get("approvalOpinion") as? String
        )
    }
}
