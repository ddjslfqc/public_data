package com.fuusy.hiddendanger.ui.model

data class GoalKrEditItem(
    /** 列表内本地标识；编辑已有 KR 时与 serverKrId 相同 */
    val id: Long = System.nanoTime(),
    /** 服务端 KR id；新建为空 */
    val serverKrId: Long? = null,
    var title: String = "",
    /** 目标进度 0–100，提交为 targetValue，单位固定 % */
    var targetPercent: Int = GoalKrEditItem.DEFAULT_TARGET_PERCENT,
    var weight: Int = GoalKrWeightHelper.TOTAL,
    var assigneeUserId: Long? = null,
    var assigneeName: String = "本人",
    var currentValue: Double = 0.0,
    var status: Int = 0
) {
    companion object {
        const val DEFAULT_TARGET_PERCENT = 100
        const val UNIT_PERCENT = "%"
    }
}

enum class GoalAlignType {
    /** 仅保留：对齐上级人员 OKR */
    SUPERVISOR
}
