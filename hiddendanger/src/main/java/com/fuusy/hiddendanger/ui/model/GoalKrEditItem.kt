package com.fuusy.hiddendanger.ui.model

data class GoalKrEditItem(
    val id: Long = System.nanoTime(),
    var title: String = "",
    /** 目标进度 0–100，提交为 targetValue，单位固定 % */
    var targetPercent: Int = GoalKrEditItem.DEFAULT_TARGET_PERCENT,
    var weight: Int = GoalKrWeightHelper.TOTAL,
    var assigneeUserId: Long? = null,
    var assigneeName: String = "本人"
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
