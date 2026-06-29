package com.fuusy.hiddendanger.ui.model

data class GoalKrEditItem(
    val id: Long = System.nanoTime(),
    var title: String = "",
    var targetValue: String = "",
    var unit: String = "",
    var assigneeUserId: Long? = null,
    var assigneeName: String = "本人"
)

enum class GoalAlignType {
    DEPARTMENT,
    SUPERVISOR
}
