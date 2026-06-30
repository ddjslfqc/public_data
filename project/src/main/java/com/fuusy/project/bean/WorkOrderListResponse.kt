package com.fuusy.project.bean

// 网络工单单条数据

data class WorkOrderNetItem(
    val id: String,
    val hidden_danger_name: String,
    val hidden_danger_description: String,
    val reason_analysis: String,
    val treatment_requirement: String,
    val hidden_danger_category: String,
    val hidden_danger_level: String,
    val affiliated_major: String,
    val control_level: String,
    val unit_system: String,
    val hazard_consequence: String,
    val possibility: String,
    val treatment_difficulty: String,
    val responsible_department: String,
    val responsible_person: String,
    val create_time: String,
    val update_time: String,
    val attachments: List<String>,
    val status: String,
    val isbh: Boolean,
    val node_name: String,
    var rejectionReason: String?, // 驳回理由
    var rejectionUser: String?, // 驳回人
    var rejectionTime: String?, // 驳回时间
)

// 网络工单列表响应

data class WorkOrderListResponse(
    val code: Int,
    val status: String,
    val data: List<WorkOrderNetItem>?
)

fun WorkOrderNetItem.toWorkOrderItem(): com.fuusy.common.data.WorkOrderItem {
    return com.fuusy.common.data.WorkOrderItem(
        id = id,
        hiddenDangerName = hidden_danger_name,
        hiddenDangerDescription = hidden_danger_description,
        reasonAnalysis = reason_analysis,
        treatmentRequirement = treatment_requirement,
        hiddenDangerCategory = hidden_danger_category,
        hiddenDangerLevel = hidden_danger_level,
        profession = affiliated_major,
        controlLevel = control_level,
        unitSystem = unit_system,
        hazardConsequence = hazard_consequence,
        possibility = possibility,
        treatmentDifficulty = treatment_difficulty,
        responsibleDepartment = responsible_department,
        responsiblePerson = responsible_person,
        submitTime = create_time,
        status = if (!isbh) {
            com.fuusy.common.data.WorkOrderStatus.REJECT
        } else {
            when (status) {
                "0" -> com.fuusy.common.data.WorkOrderStatus.PROCESSING
                "1" -> com.fuusy.common.data.WorkOrderStatus.COMPLETED
                "2" -> com.fuusy.common.data.WorkOrderStatus.REJECT
                else -> com.fuusy.common.data.WorkOrderStatus.PROCESSING
            }
        },
        nodeName = node_name, // 映射节点名称字段
        attachments = attachments.map {
            com.fuusy.common.data.Attachment(
                fileName = it.substringAfterLast('/'),
                size = "",
                url = it
            )
        },
        rejectionReason = rejectionReason,
        rejectionUser = rejectionUser,
        rejectionTime = rejectionTime,
        )
} 