package com.fuusy.hiddendanger.data

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class QianwenChatRequest(
    val question: String,
    @SerializedName("top_k") val topK: Int? = null,
    /** 与 Web 一致：默认 local */
    @SerializedName("source_scope") val sourceScope: String? = "local",
    /** 开启联网搜索时传 "on" */
    @SerializedName("web_search_mode") val webSearchMode: String? = null,
    val history: List<QianwenHistoryItem>? = null
)

data class QianwenHistoryItem(
    val role: String,
    val content: String
)

data class QianwenChatResponse(
    val answer: String? = null,
    val sources: List<QianwenSource>? = null,
    @SerializedName("web_search_used") val webSearchUsed: Boolean? = null,
    /** 各阶段耗时（ms），与 Web 流式 done/timing 事件一致 */
    val timings: Map<String, Double>? = null,
    @SerializedName("total_ms") val totalMs: Double? = null
)

data class QianwenSource(
    val rank: Int? = null,
    val title: String? = null,
    val text: String? = null,
    @SerializedName("source_url") val sourceUrl: String? = null,
    @SerializedName("document_id") val documentId: String? = null,
    @SerializedName("source_type") val sourceType: String? = null,
    @SerializedName("space_name") val spaceName: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class QianwenStatementRequest(
    val statement: String
)

/** 周报草稿：聚合本人本周日报（字段与 Web / 对接文档一致） */
data class WeeklyReportDraftRequest(
    @SerializedName("user_id") val userId: Long?,
    @SerializedName("week_start") val weekStart: String,
    @SerializedName("week_end") val weekEnd: String
)

/** 月报草稿：聚合本人本月周报 */
data class MonthlyReportDraftRequest(
    @SerializedName("user_id") val userId: Long?,
    val month: String
)

data class QianwenDraftResponse(
    @SerializedName("draft_id") val draftId: String? = null,
    val payload: JsonObject? = null,
    @SerializedName("missing_labels") val missingLabels: List<String>? = null,
    val options: JsonObject? = null,
    @SerializedName(
        value = "okrProgressSuggestions",
        alternate = ["okr_progress_suggestions"]
    )
    val okrProgressSuggestions: OkrProgressSuggestions? = null,
    val timings: Map<String, Double>? = null,
    @SerializedName("total_ms") val totalMs: Double? = null
)

/** 周报 OKR 异步轮询响应（与 Web weeklyReportOkrProgress 一致） */
data class WeeklyOkrProgressResponse(
    @SerializedName(
        value = "okrProgressSuggestions",
        alternate = ["okr_progress_suggestions"]
    )
    val okrProgressSuggestions: OkrProgressSuggestions? = null,
    val timings: Map<String, Double>? = null,
    @SerializedName("total_ms") val totalMs: Double? = null
)

data class OkrProgressSuggestions(
    val status: String? = null,
    val items: List<OkrProgressSuggestionItem>? = null
)

data class OkrProgressSuggestionItem(
    @SerializedName(value = "krId", alternate = ["kr_id"])
    val krId: Long? = null,
    @SerializedName(value = "objectiveId", alternate = ["objective_id"])
    val objectiveId: Long? = null,
    @SerializedName(value = "objectiveTitle", alternate = ["objective_title"])
    val objectiveTitle: String? = null,
    @SerializedName(value = "krTitle", alternate = ["kr_title"])
    val krTitle: String? = null,
    @SerializedName(value = "targetValue", alternate = ["target_value"])
    val targetValue: Double? = null,
    @SerializedName(value = "currentValue", alternate = ["current_value"])
    val currentValue: Double? = null,
    val unit: String? = null,
    @SerializedName(value = "suggestedValue", alternate = ["suggested_value"])
    val suggestedValue: Double? = null,
    val confidence: Double? = null,
    val evidence: String? = null,
    val reason: String? = null
)

/** 周报草稿页可编辑的 OKR 进度行 */
data class WeeklyOkrRow(
    val krId: Long,
    val krTitle: String,
    val objectiveTitle: String,
    val currentValue: Double,
    val suggestedValue: Double,
    val unit: String,
    val reason: String,
    val evidence: String = "",
    val confidence: Double? = null,
    val checked: Boolean = false,
    val confirmValue: String = "",
    val remark: String = ""
)

data class QianwenSubmitRequest(
    @SerializedName("draft_id") val draftId: String,
    val payload: JsonObject
)

data class QianwenSubmitResponse(
    val result: QianwenSubmitResult? = null,
    val id: Any? = null,
    val ids: List<Any>? = null,
    val detail: String? = null
)

data class QianwenSubmitResult(
    val id: Any? = null,
    val ids: List<Any>? = null
)

data class QianwenUsersResponse(
    val users: List<QianwenOption>? = null
)

data class QianwenOption(
    val value: String? = null,
    val label: String? = null
)

data class QianwenHealthResponse(
    val status: String? = null
)

/** 日报逻辑审查（仅提示，不阻断保存） */
data class DailyReportReviewRequest(
    val content: String,
    val reportDate: String
)

data class DailyReportReviewResponse(
    val hasSeriousIssue: Boolean? = null,
    val issues: List<DailyReportReviewIssue>? = null
)

data class DailyReportReviewIssue(
    val title: String? = null,
    val description: String? = null
)

/** 聊天列表 UI 模型 */
data class AiChatMessage(
    val id: String,
    val role: String, // user | assistant | thinking
    val content: String,
    val time: String = "",
    val sources: List<QianwenSource> = emptyList(),
    val sourcesExpanded: Boolean = false,
    /** 点击正文【资料 N】后高亮对应来源行（与 Web jumpToSource 对齐） */
    val highlightSourceRank: Int? = null,
    /** 打字机：完整正文；为空表示 content 已是最终内容 */
    val fullContent: String = "",
    val isTyping: Boolean = false,
    val webSearchUsed: Boolean = false,
    /** 本轮请求总耗时（毫秒），与 Web msg.totalMs 对齐 */
    val totalMs: Double? = null,
    /** 各阶段耗时明细（可选） */
    val timings: Map<String, Double> = emptyMap()
) {
    /** 寒暄/短答不展示引用，减少噪音；跳转高亮或已展开时始终展示 */
    fun shouldShowSources(): Boolean {
        if (sources.isEmpty() || isTyping || role == "thinking") return false
        if (sourcesExpanded || highlightSourceRank != null) return true
        val text = fullContent.ifBlank { content }
        if (text.length < 60) return false
        return true
    }
}

enum class AiAssistantMode {
    CHAT, WORKORDER, DAILY, KNOWLEDGE
}

/** 「写日报」主 Tab 内的二级：日报 / 周报 / 月报（不新增主模块） */
enum class ReportSubTab {
    DAILY, WEEKLY, MONTHLY
}
