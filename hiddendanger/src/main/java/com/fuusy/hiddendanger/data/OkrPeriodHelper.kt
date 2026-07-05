package com.fuusy.hiddendanger.data

import java.util.Calendar

object OkrPeriodHelper {

    /** create 接口用的 periodType */
    fun createPeriodType(queryValue: String): String = when {
        queryValue == "year" -> "year"
        queryValue.startsWith("quarter") -> "quarter"
        else -> "quarter"
    }

    fun dateRange(queryValue: String): Pair<String, String> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return when (queryValue) {
            "quarter-1" -> "$year-01-01" to "$year-03-31"
            "quarter-2" -> "$year-04-01" to "$year-06-30"
            "quarter-3" -> "$year-07-01" to "$year-09-30"
            "quarter-4" -> "$year-10-01" to "$year-12-31"
            "year" -> "$year-01-01" to "$year-12-31"
            else -> {
                val (start, end) = dateRange("quarter-2")
                start to end
            }
        }
    }

    fun formatPeriodRange(objective: OkrObjective?): String {
        if (objective == null) return ""
        return formatShortDateRange(objective.startDate, objective.endDate)
            .ifBlank { objective.periodLabel.orEmpty() }
    }

    private fun formatShortDateRange(startDate: String?, endDate: String?): String {
        val start = toShortDate(startDate) ?: return ""
        val end = toShortDate(endDate) ?: return ""
        return "$start—$end"
    }

    private fun toShortDate(iso: String?): String? {
        if (iso.isNullOrBlank() || iso.length < 10) return null
        val parts = iso.substring(0, 10).split("-")
        if (parts.size != 3) return null
        val month = parts[1].trimStart('0').toIntOrNull() ?: return null
        val day = parts[2].trimStart('0').toIntOrNull() ?: return null
        return "${month}月${day}日"
    }

    fun krProgressPercent(kr: OkrKeyResult): Int {
        if (kr.achieved || kr.status == 1) return 100
        val target = kr.targetValue
        if (target <= 0) return 0
        return ((kr.currentValue / target) * 100).toInt().coerceIn(0, 100)
    }

    fun krValueLabel(kr: OkrKeyResult): String {
        val unit = kr.unit.orEmpty()
        return if (unit.isNotBlank()) {
            "${kr.currentValue.toDisplay()}/${kr.targetValue.toDisplay()}$unit"
        } else {
            "${kr.currentValue.toDisplay()}/${kr.targetValue.toDisplay()}"
        }
    }

    fun approvalLabel(status: Int?): String? = when (status) {
        0 -> "待审批"
        1 -> "已通过"
        2 -> "已拒绝"
        else -> null
    }

    fun progressApprovalLabel(status: Int?): String? = when (status) {
        0 -> "进度待审批"
        1 -> "进度已通过"
        2 -> "进度已拒绝"
        else -> null
    }

    private fun Double.toDisplay(): String =
        if (this == toLong().toDouble()) toLong().toString() else toString()
}
