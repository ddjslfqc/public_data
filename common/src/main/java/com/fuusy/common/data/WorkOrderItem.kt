package com.fuusy.common.data

import java.io.Serializable

// 工单主数据

data class WorkOrderItem(
    var id: String = "",
    val workOrderNo: String? = null,
    val hiddenDangerName: String? = "", // 隐患名称
    val hiddenDangerDescription: String? = "", // 隐患简述
    val reasonAnalysis: String? = "", // 原因解析
    val treatmentRequirement: String? = "", // 隐患治理要求
    val hiddenDangerCategory: String? = "", // 隐患类别
    val typeCode: String? = null,
    val hiddenDangerLevel: String? = "", // 隐患等级
    val profession: String? = "", // 所属专业
    val controlLevel: String? = "", // 管控等级
    val unitSystem: String? = "", // 机组/系统
    val hazardConsequence: String? = "", // 危害后果
    val possibility: String? = "", // 可能性
    val treatmentDifficulty: String? = "", // 治理难度
    val responsibleDepartment: String? = "", // 负责部门
    val responsibleDeptId: String? = null,
    val responsiblePerson: String? = "", // 负责人
    val attachments: List<Attachment?>? = emptyList(),
    val affiliatedMajor: String? = "",// 生产
    val submitUser: String = "",
    val submitTime: String = "",
    val status: WorkOrderStatus = WorkOrderStatus.PROCESSING,
    var rejectionReason: String? = null, // 驳回理由
    var rejectionUser: String? = null, // 驳回人
    var rejectionTime: String? = null, // 驳回时间
    val nodeName: String? = "", // 节点名称，用于显示工单状态
    val priority: String? = null, // P0 / P1 / P2 / P3
    val submitDepartment: String? = null,
    val projectName: String? = null,
    val expectedCompleteTime: String? = null,
    val stayDuration: String? = null,
    val workOrderType: String? = null, // 工单类型：软件研发 / 产品设计 等
    val assigner: String? = null // 派单人
) : Serializable

data class Attachment(
    val fileName: String? = "", val size: String? = "", val url: String? = ""
) : Serializable

enum class WorkOrderStatus(val displayName: String) : Serializable {
    DRAFT("待提交"),
    PENDING("待认领"),
    SUBMITTED("已提交"),
    REJECT("驳回"),
    PROCESSING("处理中"),
    EVAL("待评价"),
    COMPLETED("已完成"),
    CANCELLED("已取消")
} 