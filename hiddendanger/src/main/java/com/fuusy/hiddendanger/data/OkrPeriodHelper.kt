package com.fuusy.hiddendanger.data

import java.util.Calendar

object OkrPeriodHelper {

    /** 按当前日期计算所在季度 value，如 7 月 → quarter-3 */
    fun currentQuarterValue(): String = quarterValueForMonth(currentMonth())

    /**
     * 360 互评周期：仅针对**已结束的上一季度**。
     * 例：7 月（Q3 进行中）→ 互评 quarter-2。
     */
    fun peerEvalPeriod(): String = quarterValueForMonth(
        when (val m = currentMonth()) {
            in 1..3 -> 12 // 上一季度为去年 Q4，映射到 quarter-4
            else -> m - 3
        }
    )

    fun quarterLabel(queryValue: String): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return when (queryValue) {
            "quarter-1" -> "Q1 $year"
            "quarter-2" -> "Q2 $year"
            "quarter-3" -> "Q3 $year"
            "quarter-4" -> "Q4 $year"
            "year" -> "${year}年度"
            else -> queryValue
        }
    }

    /** 目标周期是否已结束（endDate 为 yyyy-MM-dd） */
    fun isPeriodEnded(endDate: String?): Boolean {
        if (endDate.isNullOrBlank() || endDate.length < 10) return false
        val parts = endDate.substring(0, 10).split("-")
        if (parts.size != 3) return false
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        val end = Calendar.getInstance().apply {
            set(year, month - 1, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Calendar.getInstance().after(end)
    }

    /** 按 Tab value（quarter-2 等）判断该季度是否已结束 */
    fun isPeriodEndedByValue(queryValue: String): Boolean =
        isPeriodEnded(dateRange(queryValue).second)

    fun isCurrentQuarter(queryValue: String): Boolean =
        queryValue == currentQuarterValue()

    /** 我的目标页 Tab 列表（按当前日期标记 active） */
    fun defaultPeriodTabs(): List<OkrPeriodOption> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val current = currentQuarterValue()
        return listOf(
            OkrPeriodOption("Q1 $year", "quarter-1", current == "quarter-1"),
            OkrPeriodOption("Q2 $year", "quarter-2", current == "quarter-2"),
            OkrPeriodOption("Q3 $year", "quarter-3", current == "quarter-3"),
            OkrPeriodOption("Q4 $year", "quarter-4", current == "quarter-4"),
            OkrPeriodOption("年度目标", "year", current == "year")
        )
    }

    fun peerEvalQuarterLabel(): String = quarterLabel(peerEvalPeriod())

    /** 列表展示：周期已结束时优先显示「已结束」，而非服务端 stale 的「进行中」 */
    fun objectiveStatusLabel(objective: OkrObjective): String {
        if (isPeriodEnded(objective.endDate)) return "已结束"
        return objective.statusLabel?.takeIf { it.isNotBlank() }
            ?: when (objective.status) {
                1 -> "已完成"
                else -> "进行中"
            }
    }

    fun objectivePeriodDisplay(objective: OkrObjective): String {
        val range = formatPeriodRange(objective)
        if (range.isNotBlank()) return range
        return objective.periodLabel.orEmpty().ifBlank { "—" }
    }

    private fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    private fun quarterValueForMonth(month: Int): String = when {
        month <= 3 -> "quarter-1"
        month <= 6 -> "quarter-2"
        month <= 9 -> "quarter-3"
        else -> "quarter-4"
    }

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

    /** 从 keyResults 统计 KR 完成数，与列表「已达成」展示保持一致 */
    fun krCompletionStats(objective: OkrObjective): Pair<Int, Int> {
        val krs = objective.keyResults.orEmpty()
        if (krs.isNotEmpty()) {
            val completed = krs.count { it.achieved || it.status == 1 }
            return completed to krs.size
        }
        val total = objective.totalKrCount ?: 0
        val completed = objective.completedKrCount ?: 0
        return completed to total
    }

    private fun Double.toDisplay(): String =
        if (this == toLong().toDouble()) toLong().toString() else toString()
}
