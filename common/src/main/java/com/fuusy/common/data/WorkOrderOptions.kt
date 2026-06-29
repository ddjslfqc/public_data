package com.fuusy.common.data

object WorkOrderOptions {

    /** 工单类型（创建页） */
    val workOrderTypes = listOf(
        "营销推广", "产品设计", "UI建模", "软件研发", "硬件研发", "项目交付", "售后运维"
    )

    /** 处理人部门 */
    val handlerDepartments = listOf(
        "市场部", "产品部", "软件研发部", "硬件研发部", "交付部", "售后部"
    )

    /** 处理人 */
    val handlers = listOf("张家豪", "王健", "李阳", "熊洋", "公共")

    /** 优先级 */
    val priorities = listOf("非常紧急", "较紧急", "一般")

    /** 类型 → 默认部门 */
    val typeToDepartment = mapOf(
        "营销推广" to "市场部",
        "产品设计" to "产品部",
        "UI建模" to "产品部",
        "软件研发" to "软件研发部",
        "硬件研发" to "硬件研发部",
        "项目交付" to "交付部",
        "售后运维" to "售后部"
    )

    fun priorityLabelToCode(label: String): String = when (label) {
        "非常紧急" -> "P0"
        "较紧急" -> "P1"
        "一般" -> "P2"
        else -> when {
            label.startsWith("P") -> label
            else -> "P3"
        }
    }

    fun priorityCodeToLabel(code: String?): String = when (code) {
        "P0" -> "非常紧急"
        "P1" -> "较紧急"
        "P2" -> "一般"
        "P3" -> "较低"
        else -> code.orEmpty()
    }

    // 兼容旧表单字段
    val hiddenDangerCategories = workOrderTypes
    val hiddenDangerLevels = priorities
    val professions = listOf("电气专业", "机械专业", "热工专业", "化学专业", "土建专业", "其他专业")
    val controlLevels = listOf("一级管控", "二级管控", "三级管控", "四级管控")
    val unitSystems = listOf("1号机组", "2号机组", "3号机组", "4号机组", "公用系统", "其他系统")
    val harmConsequences = listOf("人身伤害", "设备损坏", "环境污染", "经济损失", "其他后果")
    val possibilities = listOf("极不可能", "不太可能", "可能", "很可能", "几乎肯定")
    val treatmentDifficulties = listOf("容易", "一般", "困难", "很困难")
    val responsibleDepartments = handlerDepartments
}
