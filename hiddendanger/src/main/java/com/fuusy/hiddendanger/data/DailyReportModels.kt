package com.fuusy.hiddendanger.data

import com.google.gson.annotations.SerializedName

/** POST /mobile/daily-report 保存请求（与 Web saveDailyReport 一致） */
data class SaveDailyReportRequest(
    val reportDate: String,
    val content: String
)

/** POST /mobile/daily-report/normalize-site-names（与 Web 整理日报一致） */
data class NormalizeSiteNamesRequest(
    val texts: List<String>
)

data class NormalizeSiteNamesData(
    val texts: List<String>? = null
)

data class SaveDailyReportResult(
    val id: Long? = null
)

/** PUT /mobile/daily-report/{id} */
data class UpdateDailyReportRequest(
    val reportDate: String,
    val content: String
)

/** POST /mobile/period-report/weekly */
data class SaveWeeklyPeriodRequest(
    val weekStart: String,
    val weekEnd: String,
    val summary: String,
    val issues: String? = null
)

/** POST /mobile/period-report/monthly */
data class SaveMonthlyPeriodRequest(
    val month: String,
    val achievements: String,
    val issues: String? = null,
    val summary: String? = null
)

data class SavePeriodReportResult(
    val id: Long? = null,
    val status: String? = null,
    val userId: Long? = null,
    val userName: String? = null
)

data class DailyReportDetail(
    val id: Long = 0,
    @SerializedName("workDesc", alternate = ["workDescription"])
    val workDesc: String? = null,
    val plantName: String? = null,
    val projectName: String? = null,
    val reportDate: String? = null,
    val dateLabel: String? = null,
    val userName: String? = null,
    val userId: Long? = null
)

/** 与 Web 人员档案 /mobile/report-archive 对齐 */
data class ReportArchiveData(
    val list: List<ReportArchiveItem>? = null,
    val summary: ReportArchiveSummary? = null
)

data class ReportArchiveSummary(
    val totalCount: Int = 0,
    val weeklyCount: Int = 0,
    val monthlyCount: Int = 0,
    val userCount: Int = 0,
    val dailyCountMine: Int = 0
)

data class ReportArchiveItem(
    val id: Long = 0,
    val userId: Long = 0,
    val userName: String? = null,
    /** daily / weekly / monthly */
    val type: String? = null,
    @SerializedName(value = "reportDate", alternate = ["report_date"])
    val reportDate: String? = null,
    val period: String? = null,
    @SerializedName(value = "weekStart", alternate = ["week_start"])
    val weekStart: String? = null,
    @SerializedName(value = "weekEnd", alternate = ["week_end"])
    val weekEnd: String? = null,
    @SerializedName("summary", alternate = ["workDesc", "workDescription", "content"])
    val summary: String? = null,
    val issues: String? = null,
    val submittedAt: String? = null,
    /** 周报提交时关联的 OKR 进度（后端就绪后由 report-archive 返回） */
    @SerializedName(
        value = "okrUpdates",
        alternate = ["okrProgressUpdates", "okr_updates", "okr_progress_updates"]
    )
    val okrUpdates: List<ReportOkrUpdateItem>? = null
)

/** 周报详情 · 只读 OKR 更新记录 */
data class ReportOkrUpdateItem(
    @SerializedName(value = "krId", alternate = ["kr_id"])
    val krId: Long? = null,
    @SerializedName(value = "krTitle", alternate = ["kr_title", "title"])
    val krTitle: String? = null,
    @SerializedName(value = "objectiveTitle", alternate = ["objective_title", "oTitle"])
    val objectiveTitle: String? = null,
    @SerializedName(
        value = "fromValue",
        alternate = ["from_value", "previousValue", "previous_value", "beforeValue"]
    )
    val fromValue: Double? = null,
    @SerializedName(
        value = "toValue",
        alternate = ["to_value", "currentValue", "current_value", "afterValue"]
    )
    val toValue: Double? = null,
    val unit: String? = null,
    @SerializedName(value = "remark", alternate = ["progressRemark", "progress_remark"])
    val remark: String? = null
)

enum class MyReportType(val apiValue: String, val label: String) {
    DAILY("daily", "日报"),
    WEEKLY("weekly", "周报"),
    MONTHLY("monthly", "月报");

    companion object {
        fun fromApi(raw: String?): MyReportType? = when (raw?.lowercase()) {
            "daily" -> DAILY
            "weekly" -> WEEKLY
            "monthly" -> MONTHLY
            else -> null
        }
    }
}
