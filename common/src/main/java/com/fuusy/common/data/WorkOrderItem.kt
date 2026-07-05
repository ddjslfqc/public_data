package com.fuusy.common.data

import java.io.Serializable

/** 工单主数据，字段与 workorder-api.md 列表/详情响应对齐 */
data class WorkOrderItem(
    var id: String = "",
    val workOrderNo: String? = null,
    val hiddenDangerName: String? = "", // title
    val hiddenDangerDescription: String? = "", // brief
    val hiddenDangerCategory: String? = "", // typeName
    val typeCode: String? = null,
    val responsibleDepartment: String? = "", // responsibleDeptName
    val responsibleDeptId: String? = null, // responsibleDept
    val responsiblePerson: String? = "", // rectificationPersonName
    val rectificationPersonId: String? = null, // rectificationPerson
    val attachments: List<Attachment?>? = emptyList(),
    val submitUser: String = "", // recordCreatorName
    val submitTime: String = "", // recordTime
    val status: WorkOrderStatus = WorkOrderStatus.PROCESSING,
    var rejectionReason: String? = null,
    var rejectionUser: String? = null,
    var rejectionTime: String? = null,
    val nodeName: String? = "", // statusLabel
    val priority: String? = null,
    val projectName: String? = null, // project
    val expectedCompleteTime: String? = null, // expectedCompletionTime
    val workOrderType: String? = null, // typeName
    val operationRecords: List<WorkOrderOperationRecord> = emptyList()
) : Serializable

/** 工单操作记录（认领 / 驳回），见 workorder-operation-api.md */
data class WorkOrderOperationRecord(
    val id: Long = 0,
    val operationType: String = "",
    val operatorName: String = "",
    val operationTime: String = "",
    val content: String = ""
) : Serializable {
    val isClaim: Boolean get() = operationType.equals("CLAIM", ignoreCase = true)
    val isReject: Boolean get() = operationType.equals("REJECT", ignoreCase = true)
    val typeLabel: String get() = when {
        isClaim -> "认领"
        isReject -> "驳回"
        else -> operationType
    }
}

data class Attachment(
    val id: String? = null,
    val fileName: String? = "",
    val size: String? = "",
    val url: String? = ""
) : Serializable

/** 与 workorder-api.md 第四节状态码一致 */
enum class WorkOrderStatus(val displayName: String) : Serializable {
    DRAFT("待提交"),
    PENDING("待认领"),
    REJECT("驳回"),
    PROCESSING("处理中"),
    EVAL("待评价"),
    COMPLETED("已完成");

    companion object {
        /** 兼容本地旧数据 / 历史枚举名 */
        fun fromStored(name: String?): WorkOrderStatus = when (name?.uppercase()) {
            "DRAFT" -> DRAFT
            "PENDING", "SUBMITTED" -> PENDING
            "PROCESSING" -> PROCESSING
            "PENDING_EVALUATION", "EVAL" -> EVAL
            "COMPLETED" -> COMPLETED
            "REJECTED", "REJECT" -> REJECT
            else -> PENDING
        }
    }
}
