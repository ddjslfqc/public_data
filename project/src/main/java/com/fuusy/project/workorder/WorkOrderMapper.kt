package com.fuusy.project.workorder

import com.fuusy.common.data.WorkOrderOptions
import com.fuusy.common.data.Attachment
import com.fuusy.common.data.WorkOrderOperationRecord
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus

object WorkOrderMapper {

    /** 将表单字段映射为文档定义的 create 请求体 */
    fun buildCreateRequest(
        form: Map<String, String>,
        projectName: String?
    ): CreateWorkOrderRequest {
        fun opt(key: String): String? = form[key]?.trim()?.takeIf { it.isNotBlank() }

        val handlerId = resolveRectificationPerson(form)

        return CreateWorkOrderRequest(
            title = opt("title") ?: form["hiddenDangerName"].orEmpty(),
            brief = opt("brief") ?: form["hiddenDangerDescription"].orEmpty(),
            typeCode = opt("typeCode") ?: opt("workOrderType").orEmpty(),
            responsibleDept = opt("responsibleDept") ?: opt("responsibleDepartment").orEmpty(),
            rectificationPerson = handlerId,
            priority = opt("priority"),
            project = opt("project") ?: projectName?.trim()?.takeIf { it.isNotBlank() },
            expectedCompletionTime = normalizeDateTime(
                opt("expectedCompletionTime") ?: opt("expectedCompleteTime")
            )
        )
    }

    /** 未选处理人时传公开抢单占位 ID（文档 §3.4 rectificationPerson） */
    private fun resolveRectificationPerson(form: Map<String, String>): String {
        val person = opt(form, "rectificationPerson")
        return if (WorkOrderOptions.isPublicGrabPerson(person)) {
            WorkOrderOptions.PUBLIC_GRAB_PERSON_ID
        } else {
            person!!
        }
    }

    private fun opt(form: Map<String, String>, key: String): String? =
        form[key]?.trim()?.takeIf { it.isNotBlank() }

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
        responsiblePerson = dto.rectificationPersonName ?: dto.rectificationPerson,
        rectificationPersonId = dto.rectificationPerson
    )

    fun detailDtoToItem(dto: WorkOrderDetailDto): WorkOrderItem {
        val records = dto.rejectRecords.orEmpty().map { it.toOperationRecord() }
        val latestReject = records.lastOrNull { it.isReject }
            ?: dto.approvalRecords
                ?.filterIsInstance<Map<*, *>>()
                ?.lastOrNull { (it["approvalResult"] as? String)?.uppercase() == "REJECT" }
                ?.let { map ->
                    WorkOrderOperationRecord(
                        operationType = "REJECT",
                        operatorName = map["approvalUserName"] as? String ?: "",
                        operationTime = map["approvalTime"] as? String ?: "",
                        content = map["approvalOpinion"] as? String ?: ""
                    )
                }
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
            responsiblePerson = dto.rectificationPersonName,
            rectificationPersonId = dto.rectificationPerson,
            attachments = dto.attachments?.map { att ->
                Attachment(
                    id = att.id,
                    fileName = att.fileName,
                    size = att.fileType,
                    url = att.filePath
                )
            },
            rejectionReason = latestReject?.content?.takeIf { it.isNotBlank() },
            rejectionUser = latestReject?.operatorName?.takeIf { it.isNotBlank() },
            rejectionTime = latestReject?.operationTime?.takeIf { it.isNotBlank() },
            operationRecords = records
        )
    }

    private fun WorkOrderOperationRecordDto.toOperationRecord() = WorkOrderOperationRecord(
        id = id ?: 0L,
        operationType = operationType.orEmpty(),
        operatorName = rejectByName.orEmpty(),
        operationTime = rejectTime.orEmpty(),
        content = rejectReason.orEmpty()
    )
}
