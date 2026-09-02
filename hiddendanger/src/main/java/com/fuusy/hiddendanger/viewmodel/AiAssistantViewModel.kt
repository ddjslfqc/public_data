package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.network.UserIdProvider
import com.fuusy.common.support.Constants
import com.fuusy.common.utils.SpUtils
import com.fuusy.hiddendanger.data.AiAssistantMode
import com.fuusy.hiddendanger.data.AiChatMessage
import com.fuusy.hiddendanger.data.AiDraftField
import com.fuusy.hiddendanger.data.AiDraftFieldDefs
import com.fuusy.hiddendanger.data.MyReportType
import com.fuusy.hiddendanger.data.QianwenDraftResponse
import com.fuusy.hiddendanger.data.QianwenHistoryItem
import com.fuusy.hiddendanger.data.QianwenOption
import com.fuusy.hiddendanger.data.QianwenSubmitResponse
import com.fuusy.hiddendanger.data.KnowledgeUploadItem
import com.fuusy.hiddendanger.data.KnowledgeUploadStatus
import com.fuusy.hiddendanger.data.ReportOkrUpdateItem
import com.fuusy.hiddendanger.data.ReportSubTab
import com.fuusy.hiddendanger.data.WeeklyOkrRow
import com.fuusy.hiddendanger.repository.DailyReportRepository
import com.fuusy.hiddendanger.repository.KnowledgeRepository
import com.fuusy.hiddendanger.repository.QianwenRepository
import com.fuusy.hiddendanger.repository.WeeklyReportOkrCache
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AiAssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QianwenRepository()
    private val dailyRepo = DailyReportRepository()
    private val knowledgeRepo = KnowledgeRepository()
    private val gson = Gson()
    private val prefs = app.getSharedPreferences("qianwen_chat", 0)

    private val _mode = MutableLiveData(AiAssistantMode.CHAT)
    val mode: LiveData<AiAssistantMode> = _mode

    /** 「写日报」主 Tab 内：日报 / 周报 / 月报 */
    private val _reportSubTab = MutableLiveData(ReportSubTab.DAILY)
    val reportSubTab: LiveData<ReportSubTab> = _reportSubTab

    private val _messages = MutableLiveData<List<AiChatMessage>>(emptyList())
    val messages: LiveData<List<AiChatMessage>> = _messages

    private val _sending = MutableLiveData(false)
    val sending: LiveData<Boolean> = _sending

    private val _serviceOk = MutableLiveData<Boolean?>(null)
    val serviceOk: LiveData<Boolean?> = _serviceOk

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    private val _skillLoading = MutableLiveData(false)
    val skillLoading: LiveData<Boolean> = _skillLoading

    private val _skillSubmitting = MutableLiveData(false)
    val skillSubmitting: LiveData<Boolean> = _skillSubmitting

    private val _skillReviewing = MutableLiveData(false)
    val skillReviewing: LiveData<Boolean> = _skillReviewing

    private val _skillError = MutableLiveData<String?>()
    val skillError: LiveData<String?> = _skillError

    private val _missingHint = MutableLiveData<String?>()
    val missingHint: LiveData<String?> = _missingHint

    private val _draftId = MutableLiveData<String?>()
    val draftId: LiveData<String?> = _draftId

    private val _draftFields = MutableLiveData<List<AiDraftField>>(emptyList())
    val draftFields: LiveData<List<AiDraftField>> = _draftFields

    private val _submitSuccess = MutableLiveData<String?>()
    val submitSuccess: LiveData<String?> = _submitSuccess

    /** 同日已有日报时弹出覆盖确认，值为 reportDate（yyyy-MM-dd） */
    private val _dailyOverwritePrompt = MutableLiveData<String?>()
    val dailyOverwritePrompt: LiveData<String?> = _dailyOverwritePrompt

    private val _lastSubmitId = MutableLiveData<String?>()
    val lastSubmitId: LiveData<String?> = _lastSubmitId

    /** 千文已成功、业务库归档失败时，允许只重试归档 */
    private val _needArchiveRetry = MutableLiveData(false)
    val needArchiveRetry: LiveData<Boolean> = _needArchiveRetry

    private val _woOptions = MutableLiveData<Map<String, List<QianwenOption>>>(emptyMap())
    val woOptions: LiveData<Map<String, List<QianwenOption>>> = _woOptions

    private val _knowledgeUploads = MutableLiveData<List<KnowledgeUploadItem>>(emptyList())
    val knowledgeUploads: LiveData<List<KnowledgeUploadItem>> = _knowledgeUploads

    private val _knowledgeBusy = MutableLiveData(false)
    val knowledgeBusy: LiveData<Boolean> = _knowledgeBusy

    private val _okrRows = MutableLiveData<List<WeeklyOkrRow>>(emptyList())
    val okrRows: LiveData<List<WeeklyOkrRow>> = _okrRows

    private val _okrStatus = MutableLiveData<String?>()
    val okrStatus: LiveData<String?> = _okrStatus

    private val _okrHint = MutableLiveData<String?>()
    val okrHint: LiveData<String?> = _okrHint

    private val _webSearchOn = MutableLiveData(false)
    val webSearchOn: LiveData<Boolean> = _webSearchOn

    /** 写日报入口选定的日期（yyyy-MM-dd），默认今天；生成草稿 / 重新生成都沿用 */
    private val _dailyReportDate = MutableLiveData(todayStr())
    val dailyReportDate: LiveData<String> = _dailyReportDate

    /** 周报入口选定的周界（周一～周日，yyyy-MM-dd） */
    private val _weeklyWeekStart = MutableLiveData(mondayStr())
    private val _weeklyWeekEnd = MutableLiveData(sundayStr())
    val weeklyWeekStart: LiveData<String> = _weeklyWeekStart
    val weeklyWeekEnd: LiveData<String> = _weeklyWeekEnd

    /** 月报入口选定的月份（yyyy-MM） */
    private val _monthlyMonth = MutableLiveData(currentMonthStr())
    val monthlyMonth: LiveData<String> = _monthlyMonth

    private var draftPayload: JsonObject? = null
    private var skillStatement: String = ""
    private var thinkingId: String? = null
    private var thinkingJob: Job? = null
    private var typewriterJob: Job? = null
    /** 打字过程中暂存引用，跳过/完成时再挂上 */
    private var pendingSources: List<com.fuusy.hiddendanger.data.QianwenSource> = emptyList()
    private var pendingWebSearchUsed: Boolean = false
    private var pendingTotalMs: Double? = null
    private var pendingTimings: Map<String, Double> = emptyMap()

    /** 日报/周报/月报各自保留草稿，切 Tab 不互相清空（对齐 Web） */
    private data class ReportDraftBucket(
        val draftId: String? = null,
        val payload: JsonObject? = null,
        val fields: List<AiDraftField> = emptyList(),
        val options: Map<String, List<QianwenOption>> = emptyMap(),
        val submitSuccess: String? = null,
        val lastSubmitId: String? = null,
        val skillError: String? = null,
        val missingHint: String? = null,
        val okrRows: List<WeeklyOkrRow> = emptyList(),
        val okrStatus: String? = null,
        val okrHint: String? = null,
        val needArchiveRetry: Boolean = false
    )

    private val reportDrafts = mutableMapOf<ReportSubTab, ReportDraftBucket>()
    /** 每个子 Tab 独立 loading，避免日/周/月互相卡住 */
    private val reportLoading = mutableMapOf<ReportSubTab, Boolean>()
    /** 每个子 Tab 独立请求序号，避免慢请求回写覆盖新结果 / 写到错误 Tab */
    private val reportGenSeq = mutableMapOf<ReportSubTab, Int>()
    private var reportGenJobs = mutableMapOf<ReportSubTab, Job>()
    /** 周报 OKR 异步轮询（与 Web pollOkrProgress 对齐） */
    private var okrPollJob: Job? = null
    private var okrPollGeneration: Int = 0
    private var sessionInitialized = false

    fun init() {
        if (sessionInitialized) {
            checkHealth()
            return
        }
        sessionInitialized = true
        loadHistory()
        loadKnowledgeHistory()
        // 每次进入助手页不恢复本地汇报草稿，保持空白可写
        clearPersistedReportDrafts()
        checkHealth()
    }

    fun toggleWebSearch() {
        _webSearchOn.value = !(_webSearchOn.value == true)
    }

    fun setMode(mode: AiAssistantMode) {
        if (_mode.value == mode) return
        // 切主 Tab 只切换界面，保留日报/工单草稿与输入；清空仅在「再写一条」等主动操作
        if (_mode.value == AiAssistantMode.DAILY) {
            stashCurrentReportDraft()
        }
        _mode.value = mode
        if (mode == AiAssistantMode.DAILY) {
            applyReportDraftBucket(
                reportDrafts[_reportSubTab.value ?: ReportSubTab.DAILY] ?: ReportDraftBucket()
            )
            refreshSkillLoadingForCurrentTab()
        }
    }

    fun setReportSubTab(tab: ReportSubTab) {
        if (_reportSubTab.value == tab) return
        stashCurrentReportDraft()
        // 必须先改当前 Tab，再恢复草稿：否则异步回调仍认为在旧 Tab，会把新数据写进错误界面并污染缓存
        _reportSubTab.value = tab
        applyReportDraftBucket(reportDrafts[tab] ?: ReportDraftBucket())
        refreshSkillLoadingForCurrentTab()
    }

    fun currentReportFieldDefs(): List<AiDraftField> = when (_reportSubTab.value) {
        ReportSubTab.WEEKLY -> AiDraftFieldDefs.weekly
        ReportSubTab.MONTHLY -> AiDraftFieldDefs.monthly
        else -> AiDraftFieldDefs.daily
    }

    /** 切回技能页时恢复输入框文案 */
    fun skillStatementText(): String = skillStatement

    fun rememberSkillStatement(text: String) {
        skillStatement = text.trim()
    }

    fun checkHealth() {
        viewModelScope.launch {
            _serviceOk.value = repo.health().getOrDefault(false)
        }
    }

    fun sendChat(text: String) {
        val q = text.trim()
        if (q.isEmpty() || _sending.value == true) return
        cancelChatAnimations()

        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = q,
            time = nowTime()
        )
        val thinkId = UUID.randomUUID().toString()
        thinkingId = thinkId
        val thinkMsg = AiChatMessage(
            id = thinkId,
            role = "thinking",
            content = "正在思考",
            time = nowTime()
        )
        val list = _messages.value.orEmpty() + userMsg + thinkMsg
        _messages.value = list
        persistHistory(list.filter { it.role != "thinking" && !it.isTyping })
        _sending.value = true
        val requestStartedMs = System.currentTimeMillis()
        startThinkingDots(thinkId, requestStartedMs)

        viewModelScope.launch {
            val history = list
                .filter { it.role == "user" || it.role == "assistant" }
                .dropLastWhile { it.role == "thinking" || it.isTyping }
                .let { msgs -> msgs.dropLast(1).takeLast(20) }
                .map { QianwenHistoryItem(it.role, it.fullContent.ifBlank { it.content }) }
            val webOn = _webSearchOn.value == true
            repo.chat(q, history, webSearchOn = webOn)
                .onSuccess { res ->
                    thinkingJob?.cancel()
                    val clientMs = (System.currentTimeMillis() - requestStartedMs).toDouble()
                    val timings = res.timings.orEmpty()
                    val totalMs = timings["total_ms"]
                        ?: res.totalMs
                        ?: clientMs
                    val full = res.answer?.ifBlank { "（服务未返回回答）" } ?: "（服务未返回回答）"
                    startTypewriter(
                        id = thinkId,
                        full = full,
                        sources = res.sources.orEmpty(),
                        webSearchUsed = res.webSearchUsed == true,
                        totalMs = totalMs,
                        timings = timings.ifEmpty { mapOf("total_ms" to totalMs) }
                    )
                }
                .onFailure { e ->
                    thinkingJob?.cancel()
                    val clientMs = (System.currentTimeMillis() - requestStartedMs).toDouble()
                    replaceMessage(
                        thinkId,
                        AiChatMessage(
                            id = thinkId,
                            role = "assistant",
                            content = "请求失败：${e.message ?: "请稍后重试"}\n可修改问题后重新发送。",
                            time = nowTime(),
                            totalMs = clientMs,
                            timings = mapOf("total_ms" to clientMs)
                        )
                    )
                    thinkingId = null
                    _sending.value = false
                }
        }
    }

    private fun startThinkingDots(id: String, requestStartedMs: Long) {
        thinkingJob?.cancel()
        thinkingJob = viewModelScope.launch {
            var step = 0
            while (isActive) {
                val dots = ".".repeat((step % 3) + 1)
                val elapsedSec = (System.currentTimeMillis() - requestStartedMs) / 1000.0
                val elapsedLabel = String.format(Locale.getDefault(), "%.1f", elapsedSec)
                patchMessage(id) {
                    it.copy(content = "正在思考$dots\n${elapsedLabel}s")
                }
                step++
                delay(420)
            }
        }
    }

    private fun startTypewriter(
        id: String,
        full: String,
        sources: List<com.fuusy.hiddendanger.data.QianwenSource>,
        webSearchUsed: Boolean = false,
        totalMs: Double? = null,
        timings: Map<String, Double> = emptyMap()
    ) {
        typewriterJob?.cancel()
        pendingSources = sources
        pendingWebSearchUsed = webSearchUsed
        pendingTotalMs = totalMs
        pendingTimings = timings
        replaceMessage(
            id,
            AiChatMessage(
                id = id,
                role = "assistant",
                content = "",
                fullContent = full,
                time = nowTime(),
                sources = emptyList(),
                isTyping = true,
                webSearchUsed = webSearchUsed,
                totalMs = totalMs,
                timings = timings
            )
        )
        typewriterJob = viewModelScope.launch {
            // 正文不含光标，避免空格/光标切换导致气泡宽度跳动；光标由 Adapter 追加
            val len = full.length.coerceAtLeast(1)
            val targetMs = when {
                len > 800 -> 3600L
                len > 400 -> 2800L
                len > 150 -> 2000L
                else -> 1100L
            }
            val chunk = when {
                len > 600 -> 10
                len > 250 -> 6
                else -> 3
            }
            val steps = ((len + chunk - 1) / chunk).coerceAtLeast(1)
            val delayMs = (targetMs / steps).coerceIn(10L, 28L)
            var i = 0
            while (i < full.length) {
                if (!isActive) return@launch
                i = (i + chunk).coerceAtMost(full.length)
                val shown = full.substring(0, i)
                val last = shown.lastOrNull()
                patchMessage(id) {
                    it.copy(
                        content = shown,
                        fullContent = full,
                        isTyping = true,
                        sources = emptyList(),
                        webSearchUsed = webSearchUsed,
                        totalMs = totalMs,
                        timings = timings
                    )
                }
                val extra = when (last) {
                    '。', '！', '？', '\n' -> 36L
                    '，', '、', '；', '：' -> 12L
                    else -> 0L
                }
                delay(delayMs + extra)
            }
            if (!isActive) return@launch
            finishTypewriter(
                id,
                full,
                pendingSources,
                pendingWebSearchUsed,
                pendingTotalMs,
                pendingTimings
            )
        }
    }

    /** 点击气泡跳过打字机，立刻展示全文 */
    fun skipTypewriter() {
        val typing = _messages.value.orEmpty().firstOrNull { it.isTyping } ?: return
        typewriterJob?.cancel()
        typewriterJob = null
        val full = typing.fullContent.ifBlank { typing.content }
            .trimEnd('▍', ' ')
        finishTypewriter(
            typing.id,
            full,
            pendingSources.ifEmpty { typing.sources },
            pendingWebSearchUsed || typing.webSearchUsed,
            pendingTotalMs ?: typing.totalMs,
            pendingTimings.ifEmpty { typing.timings }
        )
    }

    private fun finishTypewriter(
        id: String,
        full: String,
        sources: List<com.fuusy.hiddendanger.data.QianwenSource>,
        webSearchUsed: Boolean = false,
        totalMs: Double? = null,
        timings: Map<String, Double> = emptyMap()
    ) {
        pendingSources = emptyList()
        pendingWebSearchUsed = false
        pendingTotalMs = null
        pendingTimings = emptyMap()
        replaceMessage(
            id,
            AiChatMessage(
                id = id,
                role = "assistant",
                content = full,
                fullContent = full,
                time = nowTime(),
                sources = sources,
                isTyping = false,
                webSearchUsed = webSearchUsed,
                totalMs = totalMs,
                timings = timings
            )
        )
        thinkingId = null
        _sending.value = false
    }

    private fun patchMessage(id: String, transform: (AiChatMessage) -> AiChatMessage) {
        val cur = _messages.value.orEmpty().toMutableList()
        val idx = cur.indexOfFirst { it.id == id }
        if (idx < 0) return
        cur[idx] = transform(cur[idx])
        _messages.value = cur
    }

    private fun replaceMessage(id: String, with: AiChatMessage) {
        val cur = _messages.value.orEmpty().toMutableList()
        val idx = cur.indexOfFirst { it.id == id || it.role == "thinking" }
        if (idx >= 0) cur[idx] = with else cur.add(with)
        _messages.value = cur
        if (!with.isTyping && with.role != "thinking") {
            persistHistory(cur)
        }
    }

    private fun cancelChatAnimations() {
        thinkingJob?.cancel()
        typewriterJob?.cancel()
        thinkingJob = null
        typewriterJob = null
        // 若有未完成打字，直接展平为全文
        val flattened = _messages.value.orEmpty().map { m ->
            when {
                m.role == "thinking" -> null
                m.isTyping -> m.copy(
                    content = m.fullContent.ifBlank { m.content },
                    isTyping = false
                )
                else -> m
            }
        }.filterNotNull()
        _messages.value = flattened
        persistHistory(flattened)
        thinkingId = null
    }

    fun deleteMessage(id: String) {
        val next = _messages.value.orEmpty().filterNot { it.id == id || it.role == "thinking" }
        _messages.value = next
        persistHistory(next)
    }

    fun toggleSources(id: String) {
        _messages.value = _messages.value.orEmpty().map {
            if (it.id == id) {
                it.copy(
                    sourcesExpanded = !it.sourcesExpanded,
                    highlightSourceRank = if (it.sourcesExpanded) null else it.highlightSourceRank
                )
            } else it
        }
    }

    /** 对齐 Web：展开引用来源并高亮指定 rank */
    fun jumpToSource(messageId: String, rank: Int) {
        _messages.value = _messages.value.orEmpty().map {
            if (it.id == messageId) {
                it.copy(sourcesExpanded = true, highlightSourceRank = rank)
            } else {
                it.copy(highlightSourceRank = null)
            }
        }
    }

    fun newChat() {
        if (_sending.value == true) {
            // 允许强制开新对话：打断打字
            cancelChatAnimations()
            _sending.value = false
        }
        cancelChatAnimations()
        _messages.value = emptyList()
        persistHistory(emptyList())
    }

    fun generateWorkOrder(statement: String) {
        skillStatement = statement.trim()
        if (skillStatement.isEmpty()) {
            _toast.value = "请先输入一句话描述"
            return
        }
        if (_serviceOk.value == false) {
            _toast.value = "悦城服务不可用，请稍后重试"
        }
        _skillLoading.value = true
        _skillError.value = null
        _missingHint.value = null
        _submitSuccess.value = null
        _lastSubmitId.value = null
        viewModelScope.launch {
            repo.workOrderDraft(skillStatement)
                .onSuccess { res ->
                    applySkillDraft(res, AiDraftFieldDefs.workOrder, emptySet())
                    parseWoOptions(res.options)
                    val dept = jsonString(draftPayload, "responsibleDept")
                    if (dept.isNotBlank()) loadWoUsers(dept)
                }
                .onFailure { e ->
                    _skillError.value = e.message ?: "生成失败"
                    clearDraftOnly()
                }
            _skillLoading.value = false
        }
    }

    fun setDailyReportDate(date: String) {
        val trimmed = date.trim()
        if (trimmed.isBlank()) return
        if (_dailyReportDate.value == trimmed) return
        _dailyReportDate.value = trimmed
        // 已有草稿时同步改 payload 日期，避免入口与草稿不一致
        val payload = draftPayload ?: return
        if (jsonString(payload, "reportDate") == trimmed) return
        payload.addProperty("reportDate", trimmed)
        draftPayload = payload
        _draftFields.value = mergeFields(currentReportFieldDefs(), payload)
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    /** 选任意一天，自动对齐所在自然周（周一～周日） */
    fun setWeeklyPeriodFromDay(dayIso: String) {
        val (start, end) = weekRangeContaining(dayIso.trim())
        if (start.isBlank() || end.isBlank()) return
        if (_weeklyWeekStart.value == start && _weeklyWeekEnd.value == end) return
        _weeklyWeekStart.value = start
        _weeklyWeekEnd.value = end
        syncWeeklyPeriodToDraft(start, end)
    }

    fun setWeeklyPeriod(start: String, end: String) {
        val s = start.trim()
        val e = end.trim()
        if (s.isBlank() || e.isBlank()) return
        _weeklyWeekStart.value = s
        _weeklyWeekEnd.value = e
        syncWeeklyPeriodToDraft(s, e)
    }

    fun setMonthlyMonth(month: String) {
        val trimmed = month.trim()
        if (trimmed.isBlank()) return
        if (_monthlyMonth.value == trimmed) return
        _monthlyMonth.value = trimmed
        val payload = draftPayload ?: return
        if (jsonString(payload, "month") == trimmed) return
        payload.addProperty("month", trimmed)
        draftPayload = payload
        _draftFields.value = mergeFields(currentReportFieldDefs(), payload)
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    private fun syncWeeklyPeriodToDraft(start: String, end: String) {
        val payload = draftPayload ?: return
        payload.addProperty("weekStart", start)
        payload.addProperty("weekEnd", end)
        draftPayload = payload
        _draftFields.value = mergeFields(AiDraftFieldDefs.weekly, payload)
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    fun generateDaily(statement: String) {
        skillStatement = statement.trim()
        if (skillStatement.isEmpty()) {
            _toast.value = "请先输入今天的工作描述"
            return
        }
        val targetTab = ReportSubTab.DAILY
        if (reportLoading[targetTab] == true) return
        val seq = nextReportGenSeq(targetTab)
        // 与 Web 一致：先 normalize-site-names，成功后再出可编辑草稿；审查后台跑
        setReportLoading(targetTab, true)
        _skillReviewing.value = false
        if (isViewingReportTab(targetTab)) {
            _skillError.value = null
            _missingHint.value = null
            _submitSuccess.value = null
            _lastSubmitId.value = null
            _needArchiveRetry.value = false
            pendingArchiveKind = null
            clearOkrState()
        }

        // 沿用入口已选日期；重新生成也不打回今天
        val reportDate = _dailyReportDate.value?.trim().orEmpty().ifBlank { todayStr() }
        _dailyReportDate.value = reportDate
        val sourceContent = skillStatement

        reportGenJobs[targetTab]?.cancel()
        reportGenJobs[targetTab] = viewModelScope.launch {
            val normalized = dailyRepo.normalizeSiteNames(sourceContent)
            if (!isReportGenCurrent(targetTab, seq)) return@launch
            normalized
                .onSuccess { content ->
                    skillStatement = content
                    val payload = JsonObject().apply {
                        addProperty("reportDate", reportDate)
                        addProperty("content", content)
                    }
                    putReportDraft(
                        targetTab,
                        ReportDraftBucket(
                            draftId = "local-${System.currentTimeMillis()}",
                            payload = payload,
                            fields = mergeFields(AiDraftFieldDefs.daily, payload),
                            skillError = null
                        )
                    )
                    setReportLoading(targetTab, false)
                    reviewDailyInBackground(targetTab, seq, content, reportDate)
                }
                .onFailure { e ->
                    val msg = "日报草稿生成失败：${e.message ?: "项目名称转换失败"}"
                    clearReportDraft(targetTab, error = msg)
                    setReportLoading(targetTab, false)
                }
        }
    }

    /**
     * 后台审查（对齐 Web useDailyReport）：意见仅提示，不改草稿、不挡提交。
     * 按钮/预览不依赖 reviewing 状态。
     */
    private suspend fun reviewDailyInBackground(
        targetTab: ReportSubTab,
        seq: Int,
        content: String,
        reportDate: String
    ) {
        if (!isReportGenCurrent(targetTab, seq)) return
        _skillReviewing.value = true
        var hint: String? = null
        repo.dailyReportReview(content, reportDate)
            .onSuccess { review ->
                if (review.hasSeriousIssue == true) {
                    val issues = review.issues.orEmpty()
                        .mapNotNull { it.description?.takeIf { d -> d.isNotBlank() } ?: it.title }
                        .joinToString("；")
                    hint =
                        "AI 审查提示：${issues.ifBlank { "日报可能存在严重逻辑问题" }}（不影响提交）"
                }
            }
            .onFailure { e ->
                hint = "AI 审查暂时不可用：${e.message ?: "请求失败"}（不影响提交）"
            }
        if (!isReportGenCurrent(targetTab, seq)) {
            _skillReviewing.value = false
            return
        }
        if (!hint.isNullOrBlank()) {
            reportDrafts[targetTab]?.let { cur ->
                reportDrafts[targetTab] = cur.copy(missingHint = hint)
            }
            if (isViewingReportTab(targetTab) && _submitSuccess.value.isNullOrBlank()) {
                _missingHint.value = hint
            }
        }
        _skillReviewing.value = false
    }

    private fun reviewDaily(content: String, reportDate: String) {
        viewModelScope.launch {
            // 提交时后台再扫一眼最终正文；已有审查进行中则不重复请求
            if (_skillReviewing.value == true) return@launch
            _skillReviewing.value = true
            repo.dailyReportReview(content, reportDate)
                .onSuccess { review ->
                    if (review.hasSeriousIssue == true && _submitSuccess.value.isNullOrBlank()) {
                        val issues = review.issues.orEmpty()
                            .mapNotNull { it.description?.takeIf { d -> d.isNotBlank() } ?: it.title }
                            .joinToString("；")
                        _missingHint.value =
                            "AI 审查提示：${issues.ifBlank { "日报可能存在严重逻辑问题" }}（不影响提交）"
                    }
                }
                .onFailure { }
            _skillReviewing.value = false
        }
    }

    /** 写日报 Tab 内统一入口：按子 Tab 生成日报/周报/月报 */
    fun generateReportSkill(statement: String) {
        when (_reportSubTab.value) {
            ReportSubTab.WEEKLY -> generateWeekly()
            ReportSubTab.MONTHLY -> generateMonthly()
            else -> generateDaily(statement)
        }
    }

    fun generateWeekly() {
        val targetTab = ReportSubTab.WEEKLY
        val seq = nextReportGenSeq(targetTab)
        stopOkrProgressPoll()
        setReportLoading(targetTab, true)
        if (isViewingReportTab(targetTab)) {
            _skillError.value = null
            _missingHint.value = null
            _submitSuccess.value = null
            _lastSubmitId.value = null
            _needArchiveRetry.value = false
            pendingArchiveKind = null
            clearOkrState()
        }
        val weekStart = _weeklyWeekStart.value?.trim().orEmpty().ifBlank { mondayStr() }
        val weekEnd = _weeklyWeekEnd.value?.trim().orEmpty().ifBlank { sundayStr() }
        _weeklyWeekStart.value = weekStart
        _weeklyWeekEnd.value = weekEnd
        reportGenJobs[targetTab]?.cancel()
        reportGenJobs[targetTab] = viewModelScope.launch {
            repo.weeklyReportDraft(weekStart, weekEnd)
                .onSuccess { res ->
                    if (!isReportGenCurrent(targetTab, seq)) return@onSuccess
                    val payload = res.payload?.deepCopy() ?: JsonObject()
                    if (jsonString(payload, "weekStart").isBlank()) {
                        payload.addProperty("weekStart", weekStart)
                    }
                    if (jsonString(payload, "weekEnd").isBlank()) {
                        payload.addProperty("weekEnd", weekEnd)
                    }
                    fillUser(payload)
                    val labels = res.missingLabels.orEmpty().filter { it.isNotBlank() }
                    val hint =
                        if (labels.isNotEmpty()) "以下字段需补充：${labels.joinToString("、")}" else null
                    val (okrRows, okrStatus, okrHint) = buildOkrBucket(res.okrProgressSuggestions)
                    val draftId = res.draftId
                    putReportDraft(
                        targetTab,
                        ReportDraftBucket(
                            draftId = draftId,
                            payload = payload,
                            fields = mergeFields(AiDraftFieldDefs.weekly, payload),
                            missingHint = hint,
                            okrRows = okrRows,
                            okrStatus = okrStatus,
                            okrHint = okrHint
                        )
                    )
                    // 后端 OKR 异步：pending 时轮询，不阻塞正文草稿
                    if (!draftId.isNullOrBlank() &&
                        okrStatus.equals("pending", ignoreCase = true)
                    ) {
                        startOkrProgressPoll(draftId, seq)
                    }
                }
                .onFailure { e ->
                    if (!isReportGenCurrent(targetTab, seq)) return@onFailure
                    val msg = e.message ?: "生成周报失败（请确认千文已部署周报接口）"
                    clearReportDraft(targetTab, error = msg)
                }
            if (isReportGenCurrent(targetTab, seq)) {
                setReportLoading(targetTab, false)
            }
        }
    }

    fun generateMonthly() {
        val targetTab = ReportSubTab.MONTHLY
        val seq = nextReportGenSeq(targetTab)
        setReportLoading(targetTab, true)
        if (isViewingReportTab(targetTab)) {
            _skillError.value = null
            _missingHint.value = null
            _submitSuccess.value = null
            _lastSubmitId.value = null
            _needArchiveRetry.value = false
            pendingArchiveKind = null
            clearOkrState()
        }
        val month = _monthlyMonth.value?.trim().orEmpty().ifBlank { currentMonthStr() }
        _monthlyMonth.value = month
        reportGenJobs[targetTab]?.cancel()
        reportGenJobs[targetTab] = viewModelScope.launch {
            repo.monthlyReportDraft(month)
                .onSuccess { res ->
                    if (!isReportGenCurrent(targetTab, seq)) return@onSuccess
                    val payload = res.payload?.deepCopy() ?: JsonObject()
                    if (jsonString(payload, "month").isBlank()) {
                        payload.addProperty("month", month)
                    }
                    fillUser(payload)
                    val labels = res.missingLabels.orEmpty().filter { it.isNotBlank() }
                    val hint =
                        if (labels.isNotEmpty()) "以下字段需补充：${labels.joinToString("、")}" else null
                    putReportDraft(
                        targetTab,
                        ReportDraftBucket(
                            draftId = res.draftId,
                            payload = payload,
                            fields = mergeFields(AiDraftFieldDefs.monthly, payload),
                            missingHint = hint
                        )
                    )
                }
                .onFailure { e ->
                    if (!isReportGenCurrent(targetTab, seq)) return@onFailure
                    val msg = e.message ?: "生成月报失败（请确认千文已部署月报接口）"
                    clearReportDraft(targetTab, error = msg)
                }
            if (isReportGenCurrent(targetTab, seq)) {
                setReportLoading(targetTab, false)
            }
        }
    }

    /** 仅改 payload，不重建整个列表（避免输入时光标跳动） */
    fun updatePayloadField(key: String, value: String, rebuildUi: Boolean = false) {
        val obj = draftPayload ?: JsonObject().also { draftPayload = it }
        obj.addProperty(key, value)
        draftPayload = obj
        if (rebuildUi) {
            val defs = when {
                _mode.value == AiAssistantMode.DAILY -> currentReportFieldDefs()
                else -> AiDraftFieldDefs.workOrder
            }
            _draftFields.value = mergeFields(defs, obj)
        } else {
            _draftFields.value = _draftFields.value.orEmpty().map {
                if (it.key == key) it.copy(value = value) else it
            }
        }
        if (_mode.value == AiAssistantMode.DAILY) {
            // 编辑后立刻同步到当前 Tab 缓存，避免切走时用脏 live 覆盖
            stashCurrentReportDraft(allowEmptyOverwrite = true)
            when (key) {
                "reportDate" -> if (value.trim().isNotBlank()) {
                    _dailyReportDate.value = value.trim()
                }
                "weekStart" -> {
                    val end = jsonString(draftPayload, "weekEnd").trim()
                    if (value.trim().isNotBlank() && end.isNotBlank()) {
                        _weeklyWeekStart.value = value.trim()
                        _weeklyWeekEnd.value = end
                    }
                }
                "weekEnd" -> {
                    val start = jsonString(draftPayload, "weekStart").trim()
                    if (value.trim().isNotBlank() && start.isNotBlank()) {
                        _weeklyWeekStart.value = start
                        _weeklyWeekEnd.value = value.trim()
                    }
                }
                "month" -> if (value.trim().isNotBlank()) {
                    _monthlyMonth.value = value.trim()
                }
            }
        }
        if (key == "responsibleDept") {
            loadWoUsers(value)
        }
    }

    fun submitWorkOrder() {
        val id = _draftId.value
        if (id.isNullOrBlank()) {
            _toast.value = "请先生成草稿"
            return
        }
        val payload = draftPayload ?: return
        val err = validateRequired(AiDraftFieldDefs.workOrder, payload)
        if (err != null) {
            _skillError.value = err
            return
        }
        normalizeWoTime(payload)
        _skillSubmitting.value = true
        _skillError.value = null
        viewModelScope.launch {
            repo.workOrderSubmit(id, payload)
                .onSuccess { res ->
                    val rid = extractId(res)
                    _lastSubmitId.value = rid.ifBlank { null }
                    _submitSuccess.value =
                        if (rid.isNotBlank()) "工单创建成功（ID：$rid）" else "工单创建成功"
                    _missingHint.value = null
                }
                .onFailure { e ->
                    _skillError.value = e.message ?: "提交失败"
                }
            _skillSubmitting.value = false
        }
    }

    fun submitDaily(confirmOverwrite: Boolean = false) {
        val payload = draftPayload?.deepCopy() ?: run {
            _toast.value = "请先整理日报"
            return
        }
        val reportDate = jsonString(payload, "reportDate").trim()
        val content = jsonString(payload, "content").trim()
        if (reportDate.isBlank() || content.isBlank()) {
            val err = "请填写日期和日报正文"
            _skillError.value = err
            return
        }
        if (_skillSubmitting.value == true) return
        _skillSubmitting.value = true
        _skillError.value = null
        viewModelScope.launch {
            if (!confirmOverwrite) {
                val exists = hasExistingDailyOnDate(reportDate)
                if (exists) {
                    _skillSubmitting.value = false
                    _dailyOverwritePrompt.value = reportDate
                    return@launch
                }
            }
            reviewDaily(content, reportDate)
            dailyRepo.saveDailyReport(reportDate, content)
                .onSuccess { id ->
                    val rid = id?.toString().orEmpty()
                    _lastSubmitId.value = rid.ifBlank { null }
                    _submitSuccess.value =
                        if (rid.isNotBlank()) "日报提交成功（ID：$rid）" else "日报提交成功"
                    _missingHint.value = null
                    stashCurrentReportDraft()
                }
                .onFailure { e ->
                    _skillError.value = e.message ?: "日报保存失败"
                }
            _skillSubmitting.value = false
        }
    }

    fun consumeDailyOverwritePrompt() {
        _dailyOverwritePrompt.value = null
    }

    /** 查本人归档里是否已有同日日报（用于覆盖提示） */
    private suspend fun hasExistingDailyOnDate(reportDate: String): Boolean {
        val parts = reportDate.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        val me = UserIdProvider.current()
        val result = dailyRepo.getReportArchive(
            type = MyReportType.DAILY.apiValue,
            userId = me,
            year = year,
            month = month
        )
        val list = result.getOrNull()?.list.orEmpty()
        return list.any { item ->
            val d = item.reportDate?.trim().orEmpty()
            if (d == reportDate) return@any true
            // 兜底：用 period 文案匹配「2026年9月1日」
            val period = item.period.orEmpty()
            val expect = runCatching {
                val y = parts[0].toInt()
                val m = parts[1].toInt()
                val day = parts[2].toInt()
                "${y}年${m}月${day}日"
            }.getOrNull()
            expect != null && period.contains(expect)
        }
    }

    fun submitReportSkill() {
        when (_reportSubTab.value) {
            ReportSubTab.WEEKLY -> submitWeekly()
            ReportSubTab.MONTHLY -> submitMonthly()
            else -> submitDaily()
        }
    }

    fun submitWeekly() {
        val id = _draftId.value
        if (id.isNullOrBlank()) {
            _toast.value = "请先生成周报草稿"
            return
        }
        val payload = draftPayload?.deepCopy() ?: return
        val err = validateRequired(AiDraftFieldDefs.weekly, payload)
        if (err != null) {
            _skillError.value = err
            return
        }
        val checked = _okrRows.value.orEmpty().filter { it.checked }
        for (row in checked) {
            val n = row.confirmValue.trim().toDoubleOrNull()
            if (n == null || n < 0 || n > 100) {
                val msg = "「${row.krTitle.ifBlank { "KR" }}」的确认进度需为 0～100 之间的数字"
                _skillError.value = msg
                return
            }
            if (n + 1e-6 < row.currentValue) {
                val msg = "「${row.krTitle.ifBlank { "KR" }}」进度只能上调，不能低于当前 ${formatProgress(row.currentValue)}${row.unit}"
                _skillError.value = msg
                return
            }
        }
        val okrSnapshot = checked.map { row ->
            ReportOkrUpdateItem(
                krId = row.krId,
                krTitle = row.krTitle,
                objectiveTitle = row.objectiveTitle,
                fromValue = row.currentValue,
                toValue = clampProgressForward(row.confirmValue, row.currentValue),
                unit = row.unit.ifBlank { "%" },
                remark = row.remark.trim().ifBlank { row.reason.trim() }.ifBlank { null }
            )
        }
        fillUser(payload)
        normalizeUserId(payload)
        val updates = com.google.gson.JsonArray()
        checked.forEach { row ->
            val item = JsonObject()
            item.addProperty("krId", row.krId)
            item.addProperty("currentValue", clampProgressForward(row.confirmValue, row.currentValue))
            val remark = row.remark.trim().ifBlank { row.reason.trim() }
            if (remark.isNotBlank()) item.addProperty("progressRemark", remark)
            updates.add(item)
        }
        payload.add("okrProgressUpdates", updates)
        draftPayload = payload
        _skillSubmitting.value = true
        _skillError.value = null
        stopOkrProgressPoll()
        viewModelScope.launch {
            // 千文：OKR 进度等；业务库：我的报告归档（report-archive 读 mobile_period_report）
            val qianwenRes = repo.weeklyReportSubmit(id, payload).getOrElse { e ->
                _skillError.value = e.message ?: "周报提交失败"
                _skillSubmitting.value = false
                return@launch
            }
            val weekStart = jsonString(payload, "weekStart")
            val weekEnd = jsonString(payload, "weekEnd")
            val summary = jsonString(payload, "summary")
            val issues = jsonString(payload, "issues").ifBlank { null }
            dailyRepo.saveWeeklyPeriod(weekStart, weekEnd, summary, issues)
                .onSuccess { archived ->
                    val qid = extractId(qianwenRes)
                    val aid = archived.id?.toString().orEmpty()
                    val rid = aid.ifBlank { qid }
                    archived.id?.let { archiveId ->
                        if (okrSnapshot.isNotEmpty()) {
                            WeeklyReportOkrCache.save(
                                getApplication(),
                                archiveId,
                                weekStart,
                                weekEnd,
                                okrSnapshot
                            )
                        }
                    }
                    _lastSubmitId.value = rid.ifBlank { null }
                    _needArchiveRetry.value = false
                    pendingArchiveKind = null
                    _submitSuccess.value =
                        if (rid.isNotBlank()) "周报提交成功（ID：$rid）" else "周报提交成功"
                    _missingHint.value = null
                    stashCurrentReportDraft(allowEmptyOverwrite = true)
                    persistReportDraftsToDisk()
                }
                .onFailure { e ->
                    markArchiveFailed(
                        ArchiveKind.WEEKLY,
                        extractId(qianwenRes),
                        "周报已提交，但归档失败：${e.message ?: "请点「重试归档」"}"
                    )
                }
            _skillSubmitting.value = false
        }
    }

    fun updateOkrChecked(krId: Long, checked: Boolean) {
        _okrRows.value = _okrRows.value.orEmpty().map {
            if (it.krId == krId) it.copy(checked = checked) else it
        }
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    fun updateOkrConfirmValue(krId: Long, value: String) {
        _okrRows.value = _okrRows.value.orEmpty().map {
            if (it.krId != krId) it
            else it.copy(confirmValue = formatProgress(clampProgressForward(value, it.currentValue)))
        }
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    fun updateOkrRemark(krId: Long, remark: String) {
        _okrRows.value = _okrRows.value.orEmpty().map {
            if (it.krId == krId) it.copy(remark = remark) else it
        }
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    fun applyOkrSuggested(krId: Long) {
        _okrRows.value = _okrRows.value.orEmpty().map {
            if (it.krId != krId) it
            else {
                val next = maxOf(it.suggestedValue, it.currentValue)
                it.copy(confirmValue = formatProgress(next), checked = true)
            }
        }
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    fun applyOkrCurrent(krId: Long) {
        _okrRows.value = _okrRows.value.orEmpty().map {
            if (it.krId != krId) it
            else it.copy(confirmValue = formatProgress(it.currentValue), checked = true)
        }
        stashCurrentReportDraft(allowEmptyOverwrite = true)
    }

    private fun applyOkrSuggestions(sugg: com.fuusy.hiddendanger.data.OkrProgressSuggestions?) {
        val (rows, status, hint) = buildOkrBucket(sugg)
        _okrRows.value = rows
        _okrStatus.value = status
        _okrHint.value = hint
    }

    private fun buildOkrBucket(
        sugg: com.fuusy.hiddendanger.data.OkrProgressSuggestions?
    ): Triple<List<WeeklyOkrRow>, String?, String?> {
        val rows = buildOkrRows(sugg)
        var status = sugg?.status.orEmpty().trim().lowercase()
        // 有条目但没带 status 时按 success 展示（兼容旧/异常响应）
        if (status.isBlank() && rows.isNotEmpty()) {
            status = "success"
        }
        val hint = when {
            sugg == null || status.isBlank() ->
                "未获取到 OKR 进度建议。请确认已登录且有审批通过的关键结果；周报仍可正常提交，也可稍后在 OKR 页手动更新。"
            status == "pending" ->
                "OKR 关联待计算，请稍候……"
            status == "skipped" ->
                "本周没有可关联的 OKR：需要已有审批通过的关键结果，且与周报内容相关。"
            status == "disabled" ->
                "管理员已关闭周报的 OKR 进度建议功能。"
            status == "failed" ->
                "OKR 进度建议生成失败，周报仍可正常提交，可稍后在 OKR 页面手动更新进度。"
            status == "success" && rows.isEmpty() ->
                "AI 未匹配到可更新的关键结果，周报仍可提交。"
            status == "success" -> null
            else -> "OKR 建议状态：$status"
        }
        val normalized = status.ifBlank { "missing" }
        return Triple(rows, normalized, hint)
    }

    private fun buildOkrRows(
        sugg: com.fuusy.hiddendanger.data.OkrProgressSuggestions?
    ): List<WeeklyOkrRow> {
        return sugg?.items.orEmpty().mapNotNull { item ->
            val krId = item.krId ?: return@mapNotNull null
            val suggested = item.suggestedValue ?: item.currentValue ?: 0.0
            val current = item.currentValue ?: 0.0
            WeeklyOkrRow(
                krId = krId,
                krTitle = item.krTitle.orEmpty(),
                objectiveTitle = item.objectiveTitle.orEmpty(),
                currentValue = current,
                suggestedValue = suggested,
                unit = item.unit?.ifBlank { "%" } ?: "%",
                reason = item.reason.orEmpty(),
                evidence = item.evidence.orEmpty(),
                confidence = item.confidence,
                checked = false,
                // 进度只能上调：建议值若低于当前，初始确认值取当前
                confirmValue = formatProgress(maxOf(suggested, current)),
                remark = ""
            )
        }
    }

    private fun clampProgress(raw: String): Double {
        val n = raw.trim().toDoubleOrNull() ?: 0.0
        return (n.coerceIn(0.0, 100.0) * 100).toLong() / 100.0
    }

    /** 进度只能往前：不低于当前值，不超过 100 */
    private fun clampProgressForward(raw: String, current: Double): Double {
        val n = raw.trim().toDoubleOrNull() ?: current
        val floor = current.coerceIn(0.0, 100.0)
        return (n.coerceIn(floor, 100.0) * 100).toLong() / 100.0
    }

    private fun formatProgress(v: Double): String {
        val rounded = (v * 100).toLong() / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    private fun clearOkrState() {
        stopOkrProgressPoll()
        _okrRows.value = emptyList()
        _okrStatus.value = null
        _okrHint.value = null
    }

    private fun stopOkrProgressPoll() {
        okrPollGeneration += 1
        okrPollJob?.cancel()
        okrPollJob = null
    }

    /**
     * 与 Web useWeeklyReport.pollOkrProgress 对齐：
     * draft 可能先返回 status=pending，再每 2s 拉 okr-progress，直到非 pending。
     */
    private fun startOkrProgressPoll(draftId: String, genSeq: Int) {
        val generation = ++okrPollGeneration
        okrPollJob?.cancel()
        okrPollJob = viewModelScope.launch {
            var failures = 0
            repeat(150) {
                delay(2000)
                if (generation != okrPollGeneration) return@launch
                if (!isReportGenCurrent(ReportSubTab.WEEKLY, genSeq)) return@launch
                val bucket = reportDrafts[ReportSubTab.WEEKLY]
                if (bucket?.draftId != draftId) return@launch
                if (!bucket.submitSuccess.isNullOrBlank()) return@launch

                repo.weeklyReportOkrProgress(draftId)
                    .onSuccess { res ->
                        if (generation != okrPollGeneration) return@onSuccess
                        failures = 0
                        val (rows, status, hint) = buildOkrBucket(res.okrProgressSuggestions)
                        val latest = reportDrafts[ReportSubTab.WEEKLY] ?: return@onSuccess
                        if (latest.draftId != draftId) return@onSuccess
                        // 只更新 OKR LiveData，勿整页回写草稿字段（否则会抖动/抢焦点）
                        patchWeeklyOkr(draftId, rows, status, hint)
                        if (!status.equals("pending", ignoreCase = true)) {
                            stopOkrProgressPoll()
                        }
                    }
                    .onFailure {
                        if (generation != okrPollGeneration) return@onFailure
                        failures += 1
                        if (failures < 3) return@onFailure
                        val latest = reportDrafts[ReportSubTab.WEEKLY] ?: return@onFailure
                        if (latest.draftId != draftId) return@onFailure
                        patchWeeklyOkr(
                            draftId,
                            emptyList(),
                            "failed",
                            "OKR 关联状态查询失败，周报仍可正常编辑和提交。"
                        )
                        stopOkrProgressPoll()
                    }
                val stillPending = reportDrafts[ReportSubTab.WEEKLY]
                    ?.okrStatus.equals("pending", ignoreCase = true)
                if (!stillPending) return@launch
            }
            if (generation != okrPollGeneration) return@launch
            val latest = reportDrafts[ReportSubTab.WEEKLY] ?: return@launch
            if (latest.draftId != draftId) return@launch
            if (latest.okrStatus.equals("pending", ignoreCase = true)) {
                patchWeeklyOkr(
                    draftId,
                    emptyList(),
                    "failed",
                    "OKR 关联计算超时，周报仍可正常编辑和提交。"
                )
            }
            stopOkrProgressPoll()
        }
    }

    /** 轮询过程中原地更新 OKR，不触发草稿字段重建 */
    private fun patchWeeklyOkr(
        draftId: String,
        rows: List<WeeklyOkrRow>,
        status: String?,
        hint: String?
    ) {
        val latest = reportDrafts[ReportSubTab.WEEKLY] ?: return
        if (latest.draftId != draftId) return
        val same =
            latest.okrStatus == status &&
                latest.okrHint == hint &&
                latest.okrRows == rows
        if (same) return
        reportDrafts[ReportSubTab.WEEKLY] = latest.copy(
            okrRows = rows,
            okrStatus = status,
            okrHint = hint
        )
        if (isViewingReportTab(ReportSubTab.WEEKLY) && _draftId.value == draftId) {
            _okrRows.value = rows
            _okrStatus.value = status
            _okrHint.value = hint
        }
    }

    fun submitMonthly() {
        val id = _draftId.value
        if (id.isNullOrBlank()) {
            _toast.value = "请先生成月报草稿"
            return
        }
        val payload = draftPayload?.deepCopy() ?: return
        val err = validateRequired(AiDraftFieldDefs.monthly, payload)
        if (err != null) {
            _skillError.value = err
            return
        }
        fillUser(payload)
        normalizeUserId(payload)
        draftPayload = payload
        _skillSubmitting.value = true
        _skillError.value = null
        viewModelScope.launch {
            val qianwenRes = repo.monthlyReportSubmit(id, payload).getOrElse { e ->
                _skillError.value = e.message ?: "月报提交失败"
                _skillSubmitting.value = false
                return@launch
            }
            val month = jsonString(payload, "month")
            val achievements = jsonString(payload, "achievements")
                .ifBlank { jsonString(payload, "summary") }
            val issues = jsonString(payload, "issues").ifBlank { null }
            dailyRepo.saveMonthlyPeriod(month, achievements, issues)
                .onSuccess { archived ->
                    val qid = extractId(qianwenRes)
                    val aid = archived.id?.toString().orEmpty()
                    val rid = aid.ifBlank { qid }
                    _lastSubmitId.value = rid.ifBlank { null }
                    _needArchiveRetry.value = false
                    pendingArchiveKind = null
                    _submitSuccess.value =
                        if (rid.isNotBlank()) "月报提交成功（ID：$rid）" else "月报提交成功"
                    _missingHint.value = null
                    stashCurrentReportDraft(allowEmptyOverwrite = true)
                    persistReportDraftsToDisk()
                }
                .onFailure { e ->
                    markArchiveFailed(
                        ArchiveKind.MONTHLY,
                        extractId(qianwenRes),
                        "月报已提交，但归档失败：${e.message ?: "请点「重试归档」"}"
                    )
                }
            _skillSubmitting.value = false
        }
    }

    /** 提交成功后再开一条 */
    fun resetSkillForNext() {
        skillStatement = ""
        _submitSuccess.value = null
        _lastSubmitId.value = null
        _skillError.value = null
        _missingHint.value = null
        _needArchiveRetry.value = false
        pendingArchiveKind = null
        _dailyReportDate.value = todayStr()
        _weeklyWeekStart.value = mondayStr()
        _weeklyWeekEnd.value = sundayStr()
        _monthlyMonth.value = currentMonthStr()
        clearDraftOnly()
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** 选文件后立刻上传，无需填表 */
    fun uploadKnowledgeFile(file: java.io.File) {
        if (_knowledgeBusy.value == true) {
            _toast.value = "正在上传中，请稍候"
            return
        }
        val id = UUID.randomUUID().toString()
        appendKnowledge(
            KnowledgeUploadItem(
                id = id,
                displayName = file.name,
                sourceUrl = "",
                status = KnowledgeUploadStatus.UPLOADING,
                time = nowTime()
            )
        )
        _knowledgeBusy.value = true
        viewModelScope.launch {
            patchKnowledge(id) { it.copy(status = KnowledgeUploadStatus.UPLOADING) }
            knowledgeRepo.uploadLocalFile(file)
                .onSuccess { resp -> finishKnowledgeUpload(id, file.name, resp) }
                .onFailure { e ->
                    patchKnowledge(id) {
                        it.copy(
                            status = KnowledgeUploadStatus.FAILED,
                            error = friendlyKnowledgeError(e, upload = true)
                        )
                    }
                    _toast.value = "上传失败，请稍后重试"
                }
            _knowledgeBusy.value = false
            persistKnowledgeHistory()
        }
    }

    /** 粘贴链接后直接识别入库 */
    fun uploadKnowledgeUrl(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            _toast.value = "请输入链接"
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _toast.value = "链接需以 http:// 或 https:// 开头"
            return
        }
        if (_knowledgeBusy.value == true) {
            _toast.value = "正在上传中，请稍候"
            return
        }
        val name = url.substringAfterLast('/').ifBlank { "链接资料" }
        val id = UUID.randomUUID().toString()
        appendKnowledge(
            KnowledgeUploadItem(
                id = id,
                displayName = name,
                sourceUrl = url,
                status = KnowledgeUploadStatus.INGESTING,
                time = nowTime()
            )
        )
        _knowledgeBusy.value = true
        viewModelScope.launch {
            knowledgeRepo.ingestExternalUrl(url, name)
                .onSuccess { resp -> finishKnowledgeUpload(id, name, resp, fallbackUrl = url) }
                .onFailure { e ->
                    patchKnowledge(id) {
                        it.copy(
                            status = KnowledgeUploadStatus.FAILED,
                            error = friendlyKnowledgeError(e, upload = false)
                        )
                    }
                    _toast.value = "入库失败，请稍后重试"
                }
            _knowledgeBusy.value = false
            persistKnowledgeHistory()
        }
    }

    private fun finishKnowledgeUpload(
        id: String,
        displayName: String,
        resp: com.fuusy.hiddendanger.data.KnowledgeIngestResponse,
        fallbackUrl: String = ""
    ) {
        val url = resp.sourceUrl?.trim().orEmpty().ifBlank { fallbackUrl }
        val (status, note) = when (resp.status?.lowercase()) {
            "uploaded_only" -> KnowledgeUploadStatus.CARRIER_DONE to resp.message
            "indexed", "success", "done" -> KnowledgeUploadStatus.INDEXED to null
            else -> {
                if (resp.message?.contains("待开通") == true) {
                    KnowledgeUploadStatus.CARRIER_DONE to resp.message
                } else {
                    KnowledgeUploadStatus.INDEXED to resp.message
                }
            }
        }
        patchKnowledge(id) {
            it.copy(
                displayName = displayName,
                sourceUrl = url,
                status = status,
                error = note
            )
        }
        _toast.value = when (status) {
            KnowledgeUploadStatus.INDEXED -> "已提交识别入库"
            KnowledgeUploadStatus.CARRIER_DONE -> "已上传，系统将自动识别"
            else -> "完成"
        }
    }

    private fun friendlyKnowledgeError(e: Throwable, upload: Boolean): String {
        val raw = e.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            "timeout" in lower || "timed out" in lower ->
                if (upload) "网络超时，请检查网络后重新选择文件上传"
                else "网络超时，请稍后重试"
            "unable to resolve" in lower || "failed to connect" in lower || "network" in lower ->
                "网络不可用，请稍后重试"
            "401" in lower || "403" in lower || "鉴权" in raw ->
                "上传权限校验失败，请联系管理员"
            raw.isBlank() -> if (upload) "上传失败，请稍后重试" else "入库失败，请稍后重试"
            else -> raw
        }
    }

    private fun appendKnowledge(item: KnowledgeUploadItem) {
        _knowledgeUploads.value = listOf(item) + _knowledgeUploads.value.orEmpty()
        persistKnowledgeHistory()
    }

    private fun patchKnowledge(id: String, transform: (KnowledgeUploadItem) -> KnowledgeUploadItem) {
        _knowledgeUploads.value = _knowledgeUploads.value.orEmpty().map {
            if (it.id == id) transform(it) else it
        }
    }

    private fun loadKnowledgeHistory() {
        try {
            val raw = prefs.getString(KNOWLEDGE_KEY, null) ?: return
            val type = object : TypeToken<List<KnowledgeUploadItem>>() {}.type
            _knowledgeUploads.value = gson.fromJson(raw, type) ?: emptyList()
        } catch (_: Exception) {
            _knowledgeUploads.value = emptyList()
        }
    }

    private fun persistKnowledgeHistory() {
        try {
            prefs.edit()
                .putString(KNOWLEDGE_KEY, gson.toJson(_knowledgeUploads.value.orEmpty().take(50)))
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun applySkillDraft(
        res: QianwenDraftResponse,
        defs: List<AiDraftField>,
        skipLabels: Set<String>
    ) {
        val payload = res.payload?.deepCopy() ?: JsonObject()
        _draftId.value = res.draftId
        draftPayload = payload
        _draftFields.value = mergeFields(defs, payload)
        val labels = res.missingLabels.orEmpty()
            .filter { it.isNotBlank() && it !in skipLabels }
        _missingHint.value =
            if (labels.isNotEmpty()) "以下字段需补充：${labels.joinToString("、")}" else null
    }

    private fun mergeFields(defs: List<AiDraftField>, payload: JsonObject): List<AiDraftField> {
        val known = defs.map { def ->
            def.copy(value = jsonString(payload, def.key))
        }.toMutableList()
        val knownKeys = defs.map { it.key }.toSet()
        val hidden = setOf("rawText", "source", "userId")
        payload.entrySet().forEach { (k, _) ->
            if (k !in knownKeys && k !in hidden) {
                known.add(AiDraftField(k, k, value = jsonString(payload, k)))
            }
        }
        return known
    }

    private fun validateRequired(defs: List<AiDraftField>, payload: JsonObject): String? {
        val miss = defs.filter { it.required && jsonString(payload, it.key).isBlank() }
        if (miss.isEmpty()) return null
        return "请补充必填项：${miss.joinToString("、") { it.label }}"
    }

    private fun parseWoOptions(options: JsonObject?) {
        if (options == null) {
            _woOptions.value = emptyMap()
            return
        }
        val map = mutableMapOf<String, List<QianwenOption>>()
        listOf("types", "departments", "users", "priorities").forEach { key ->
            if (options.has(key) && options.get(key).isJsonArray) {
                val list = gson.fromJson<List<QianwenOption>>(
                    options.get(key),
                    object : TypeToken<List<QianwenOption>>() {}.type
                )
                map[key] = list.orEmpty()
            }
        }
        _woOptions.value = map
    }

    fun loadWoUsers(departmentId: String) {
        if (departmentId.isBlank()) return
        viewModelScope.launch {
            repo.workOrderUsers(departmentId).onSuccess { res ->
                val cur = _woOptions.value.orEmpty().toMutableMap()
                cur["users"] = res.users.orEmpty()
                _woOptions.value = cur
                // 刷新负责人字段选项展示
                draftPayload?.let { _draftFields.value = mergeFields(AiDraftFieldDefs.workOrder, it) }
            }
        }
    }

    private fun fillUser(payload: JsonObject) {
        val uid = com.fuusy.common.network.UserIdProvider.current()
        val name = SpUtils.getString(Constants.SP_KEY_USER_INFO_NAME).orEmpty()
            .ifBlank { SpUtils.getString("user_name").orEmpty() }
        if (uid != null && uid > 0 && jsonString(payload, "userId").isBlank()) {
            payload.addProperty("userId", uid)
        }
        if (name.isNotBlank() && jsonString(payload, "userName").isBlank()) {
            payload.addProperty("userName", name)
        }
    }

    private fun normalizeUserId(payload: JsonObject) {
        if (!payload.has("userId")) return
        val raw = payload.get("userId")
        when {
            raw == null || raw.isJsonNull -> payload.remove("userId")
            raw.isJsonPrimitive && jsonElemString(raw).isBlank() -> payload.remove("userId")
            raw.isJsonPrimitive && jsonElemString(raw).toLongOrNull() != null ->
                payload.addProperty("userId", jsonElemString(raw).toLong())
        }
    }

    private fun normalizeWoTime(payload: JsonObject) {
        val key = "expectedCompletionTime"
        val v = jsonString(payload, key)
        if (v.isBlank()) return
        // yyyy-MM-ddTHH:mm -> yyyy-MM-ddTHH:mm:00
        if (v.length == 16 && Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}""").matches(v.replace(' ', 'T'))) {
            payload.addProperty(key, v.replace(' ', 'T') + ":00")
        }
    }

    private fun extractId(res: QianwenSubmitResponse): String {
        val raw = res.result?.id ?: res.result?.ids?.firstOrNull() ?: res.id ?: res.ids?.firstOrNull()
        return raw?.toString().orEmpty().removeSuffix(".0")
    }

    private fun jsonString(obj: JsonObject?, key: String): String {
        if (obj == null || !obj.has(key)) return ""
        return jsonElemString(obj.get(key))
    }

    private fun jsonElemString(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return ""
        if (!el.isJsonPrimitive) return el.toString()
        val p = el.asJsonPrimitive
        return when {
            p.isString -> p.asString
            p.isNumber -> p.asNumber.toString()
            p.isBoolean -> p.asBoolean.toString()
            else -> p.toString()
        }
    }

    private fun clearSkillState() {
        _skillError.value = null
        _missingHint.value = null
        _submitSuccess.value = null
        _lastSubmitId.value = null
        skillStatement = ""
        clearDraftOnly()
    }

    private fun clearDraftOnly() {
        _draftId.value = null
        draftPayload = null
        _draftFields.value = emptyList()
        _woOptions.value = emptyMap()
        _needArchiveRetry.value = false
        pendingArchiveKind = null
        clearOkrState()
        if (_mode.value == AiAssistantMode.DAILY) {
            _reportSubTab.value?.let { reportDrafts.remove(it) }
            persistReportDraftsToDisk()
        }
    }

    private fun isViewingReportTab(tab: ReportSubTab): Boolean =
        _mode.value == AiAssistantMode.DAILY && _reportSubTab.value == tab

    private fun nextReportGenSeq(tab: ReportSubTab): Int {
        val next = (reportGenSeq[tab] ?: 0) + 1
        reportGenSeq[tab] = next
        return next
    }

    private fun isReportGenCurrent(tab: ReportSubTab, seq: Int): Boolean =
        reportGenSeq[tab] == seq

    /**
     * @param allowEmptyOverwrite 为 false 时：不拿空 live 覆盖已有缓存
     * （防止「请求尚未返回就切走」把刚写入缓存的结果冲掉）
     */
    private fun stashCurrentReportDraft(allowEmptyOverwrite: Boolean = false) {
        val tab = _reportSubTab.value ?: return
        val snap = snapshotCurrentReportDraft()
        val existing = reportDrafts[tab]
        if (!allowEmptyOverwrite &&
            snap.draftId.isNullOrBlank() &&
            existing?.draftId?.isNotBlank() == true
        ) {
            return
        }
        reportDrafts[tab] = snap
        persistReportDraftsToDisk()
    }

    private fun snapshotCurrentReportDraft(): ReportDraftBucket = ReportDraftBucket(
        draftId = _draftId.value,
        payload = draftPayload?.deepCopy(),
        fields = _draftFields.value.orEmpty(),
        options = _woOptions.value.orEmpty(),
        submitSuccess = _submitSuccess.value,
        lastSubmitId = _lastSubmitId.value,
        skillError = _skillError.value,
        missingHint = _missingHint.value,
        okrRows = _okrRows.value.orEmpty(),
        okrStatus = _okrStatus.value,
        okrHint = _okrHint.value,
        needArchiveRetry = _needArchiveRetry.value == true
    )

    private fun putReportDraft(tab: ReportSubTab, bucket: ReportDraftBucket) {
        reportDrafts[tab] = bucket
        persistReportDraftsToDisk()
        if (isViewingReportTab(tab)) {
            applyReportDraftBucket(bucket)
        }
    }

    private fun clearReportDraft(tab: ReportSubTab, error: String? = null) {
        val empty = ReportDraftBucket(skillError = error)
        reportDrafts[tab] = empty
        persistReportDraftsToDisk()
        if (isViewingReportTab(tab)) {
            applyReportDraftBucket(empty)
        }
        // 失败只走页面内红条，避免再弹一层 Toast 重复打扰
    }

    private fun applyReportDraftBucket(bucket: ReportDraftBucket) {
        draftPayload = bucket.payload?.deepCopy()
        if (bucket.draftId.isNullOrBlank()) {
            // 先藏卡片再清字段，避免空草稿闪一下
            _draftId.value = null
            _draftFields.value = emptyList()
            _woOptions.value = emptyMap()
            _okrRows.value = emptyList()
            _okrStatus.value = null
            _okrHint.value = null
            _submitSuccess.value = null
            _lastSubmitId.value = null
            _missingHint.value = null
            _skillError.value = bucket.skillError
            _needArchiveRetry.value = false
            pendingArchiveKind = null
        } else {
            // 必须先设 draftId：OKR 区显示依赖「有草稿」；若后设，okrStatus 观察者会误判无草稿而隐藏
            _draftId.value = bucket.draftId
            _draftFields.value = bucket.fields
            _woOptions.value = bucket.options
            _submitSuccess.value = bucket.submitSuccess
            _lastSubmitId.value = bucket.lastSubmitId
            _skillError.value = bucket.skillError
            _missingHint.value = bucket.missingHint
            _okrRows.value = bucket.okrRows
            _okrStatus.value = bucket.okrStatus
            _okrHint.value = bucket.okrHint
            _needArchiveRetry.value = bucket.needArchiveRetry
            pendingArchiveKind = when {
                !bucket.needArchiveRetry -> null
                _reportSubTab.value == ReportSubTab.MONTHLY -> ArchiveKind.MONTHLY
                else -> ArchiveKind.WEEKLY
            }
            // 入口日期/周期与草稿同步
            val d = jsonString(bucket.payload, "reportDate").trim()
            if (d.isNotBlank()) _dailyReportDate.value = d
            val ws = jsonString(bucket.payload, "weekStart").trim()
            val we = jsonString(bucket.payload, "weekEnd").trim()
            if (ws.isNotBlank() && we.isNotBlank()) {
                _weeklyWeekStart.value = ws
                _weeklyWeekEnd.value = we
            }
            val mo = jsonString(bucket.payload, "month").trim()
            if (mo.isNotBlank()) _monthlyMonth.value = mo
        }
        refreshSkillLoadingForCurrentTab()
    }

    /** 每个子 Tab 独立 loading，界面只看当前 Tab */
    private fun setReportLoading(tab: ReportSubTab, loading: Boolean) {
        reportLoading[tab] = loading
        if (isViewingReportTab(tab) || _reportSubTab.value == tab) {
            _skillLoading.value = loading
        }
    }

    private fun refreshSkillLoadingForCurrentTab() {
        val tab = _reportSubTab.value ?: ReportSubTab.DAILY
        _skillLoading.value = reportLoading[tab] == true
    }

    /**
     * 千文已成功、业务归档失败时：只重试归档，不再打千文/OKR。
     */
    fun retryArchive() {
        if (_needArchiveRetry.value != true) {
            submitReportSkill()
            return
        }
        val payload = draftPayload?.deepCopy() ?: run {
            _toast.value = "没有可归档的内容"
            return
        }
        val kind = pendingArchiveKind ?: when (_reportSubTab.value) {
            ReportSubTab.MONTHLY -> ArchiveKind.MONTHLY
            else -> ArchiveKind.WEEKLY
        }
        _skillSubmitting.value = true
        _skillError.value = null
        viewModelScope.launch {
            val result = when (kind) {
                ArchiveKind.WEEKLY -> dailyRepo.saveWeeklyPeriod(
                    jsonString(payload, "weekStart"),
                    jsonString(payload, "weekEnd"),
                    jsonString(payload, "summary"),
                    jsonString(payload, "issues").ifBlank { null }
                )
                ArchiveKind.MONTHLY -> {
                    val achievements = jsonString(payload, "achievements")
                        .ifBlank { jsonString(payload, "summary") }
                    dailyRepo.saveMonthlyPeriod(
                        jsonString(payload, "month"),
                        achievements,
                        jsonString(payload, "issues").ifBlank { null }
                    )
                }
            }
            result.onSuccess { archived ->
                val aid = archived.id?.toString().orEmpty()
                val rid = aid.ifBlank { _lastSubmitId.value.orEmpty() }
                _lastSubmitId.value = rid.ifBlank { null }
                _needArchiveRetry.value = false
                pendingArchiveKind = null
                _submitSuccess.value = when (kind) {
                    ArchiveKind.WEEKLY ->
                        if (rid.isNotBlank()) "周报归档成功（ID：$rid）" else "周报归档成功"
                    ArchiveKind.MONTHLY ->
                        if (rid.isNotBlank()) "月报归档成功（ID：$rid）" else "月报归档成功"
                }
                _skillError.value = null
                stashCurrentReportDraft(allowEmptyOverwrite = true)
                persistReportDraftsToDisk()
            }.onFailure { e ->
                _skillError.value = "归档仍失败：${e.message ?: "请稍后重试"}"
                _needArchiveRetry.value = true
            }
            _skillSubmitting.value = false
        }
    }

    private fun markArchiveFailed(kind: ArchiveKind, qianwenId: String, message: String) {
        pendingArchiveKind = kind
        _needArchiveRetry.value = true
        if (qianwenId.isNotBlank()) {
            _lastSubmitId.value = qianwenId
        }
        // 不设 submitSuccess，按钮显示「重试归档」
        _skillError.value = message
        stashCurrentReportDraft(allowEmptyOverwrite = true)
        persistReportDraftsToDisk()
    }

    private fun loadReportDraftsFromDisk() {
        try {
            // 旧版全局 key 不按用户隔离，直接丢弃，避免串号
            if (prefs.contains(REPORT_DRAFTS_KEY)) {
                prefs.edit().remove(REPORT_DRAFTS_KEY).apply()
            }
            val me = UserIdProvider.current()
            val raw = prefs.getString(reportDraftsKey(me), null) ?: return
            val type = object : TypeToken<Map<String, ReportDraftBucketDisk>>() {}.type
            val map: Map<String, ReportDraftBucketDisk> = gson.fromJson(raw, type) ?: return
            reportDrafts.clear()
            map.forEach { (key, disk) ->
                val bucket = disk.toBucket()
                if (!isDraftOwnedBy(bucket, me)) return@forEach
                val tab = when (key) {
                    "weekly" -> ReportSubTab.WEEKLY
                    "monthly" -> ReportSubTab.MONTHLY
                    else -> ReportSubTab.DAILY
                }
                reportDrafts[tab] = bucket
            }
            if (_mode.value == AiAssistantMode.DAILY) {
                applyReportDraftBucket(
                    reportDrafts[_reportSubTab.value ?: ReportSubTab.DAILY] ?: ReportDraftBucket()
                )
            }
        } catch (_: Exception) {
        }
    }

    /** 进入页面时清空本地汇报草稿（内存 + 磁盘），避免带回上次内容 */
    private fun clearPersistedReportDrafts() {
        stopOkrProgressPoll()
        reportDrafts.clear()
        reportLoading.clear()
        try {
            val me = UserIdProvider.current()
            prefs.edit()
                .remove(REPORT_DRAFTS_KEY)
                .remove(reportDraftsKey(me))
                .apply()
        } catch (_: Exception) {
        }
        if (_mode.value == AiAssistantMode.DAILY) {
            applyReportDraftBucket(ReportDraftBucket())
        }
    }

    private fun persistReportDraftsToDisk() {
        try {
            val me = UserIdProvider.current()
            val out = linkedMapOf<String, ReportDraftBucketDisk>()
            reportDrafts.forEach { (tab, bucket) ->
                if (bucket.draftId.isNullOrBlank() && bucket.payload == null) return@forEach
                if (!isDraftOwnedBy(bucket, me)) return@forEach
                val key = when (tab) {
                    ReportSubTab.WEEKLY -> "weekly"
                    ReportSubTab.MONTHLY -> "monthly"
                    else -> "daily"
                }
                out[key] = ReportDraftBucketDisk.from(bucket)
            }
            val editor = prefs.edit().remove(REPORT_DRAFTS_KEY)
            if (out.isEmpty()) {
                editor.remove(reportDraftsKey(me)).apply()
            } else {
                editor.putString(reportDraftsKey(me), gson.toJson(out)).apply()
            }
        } catch (_: Exception) {
        }
    }

    private fun reportDraftsKey(userId: Long?): String =
        if (userId != null && userId > 0) "${REPORT_DRAFTS_KEY}_u$userId" else REPORT_DRAFTS_KEY

    /** 草稿归属校验：payload.userId 与当前登录用户一致才保留 */
    private fun isDraftOwnedBy(bucket: ReportDraftBucket, userId: Long?): Boolean {
        if (userId == null || userId <= 0) return false
        val payload = bucket.payload ?: return true
        if (!payload.has("userId")) return true
        val raw = runCatching { payload.get("userId").asLong }.getOrNull()
            ?: runCatching { payload.get("userId").asString.toLongOrNull() }.getOrNull()
            ?: return true
        return raw == userId
    }

    private data class ReportDraftBucketDisk(
        val draftId: String? = null,
        val payloadJson: String? = null,
        val fields: List<AiDraftField> = emptyList(),
        val submitSuccess: String? = null,
        val lastSubmitId: String? = null,
        val skillError: String? = null,
        val missingHint: String? = null,
        val okrRows: List<WeeklyOkrRow> = emptyList(),
        val okrStatus: String? = null,
        val okrHint: String? = null,
        val needArchiveRetry: Boolean = false
    ) {
        fun toBucket(): ReportDraftBucket {
            val payload = payloadJson?.let {
                runCatching { Gson().fromJson(it, JsonObject::class.java) }.getOrNull()
            }
            return ReportDraftBucket(
                draftId = draftId,
                payload = payload,
                fields = fields,
                submitSuccess = submitSuccess,
                lastSubmitId = lastSubmitId,
                skillError = skillError,
                missingHint = missingHint,
                okrRows = okrRows,
                okrStatus = okrStatus,
                okrHint = okrHint,
                needArchiveRetry = needArchiveRetry
            )
        }

        companion object {
            fun from(bucket: ReportDraftBucket) = ReportDraftBucketDisk(
                draftId = bucket.draftId,
                payloadJson = bucket.payload?.toString(),
                fields = bucket.fields,
                submitSuccess = bucket.submitSuccess,
                lastSubmitId = bucket.lastSubmitId,
                skillError = bucket.skillError,
                missingHint = bucket.missingHint,
                okrRows = bucket.okrRows,
                okrStatus = bucket.okrStatus,
                okrHint = bucket.okrHint,
                needArchiveRetry = bucket.needArchiveRetry
            )
        }
    }

    private enum class ArchiveKind { WEEKLY, MONTHLY }

    private var pendingArchiveKind: ArchiveKind? = null

    private fun loadHistory() {
        try {
            val raw = prefs.getString(CHAT_KEY, null) ?: return
            val type = object : TypeToken<List<AiChatMessage>>() {}.type
            val list: List<AiChatMessage> = gson.fromJson(raw, type) ?: emptyList()
            _messages.value = list
                .filter { it.role != "thinking" }
                .map {
                    // 异常退出时可能残留打字态 / 光标字符
                    val body = it.fullContent.ifBlank { it.content }
                        .trimEnd('▍', ' ')
                    it.copy(content = body, fullContent = body, isTyping = false)
                }
        } catch (_: Exception) {
            _messages.value = emptyList()
        }
    }

    private fun persistHistory(list: List<AiChatMessage>) {
        try {
            val clean = list
                .filter { it.role != "thinking" }
                .map {
                    if (it.isTyping) it.copy(
                        content = it.fullContent.ifBlank { it.content },
                        isTyping = false
                    ) else it
                }
            prefs.edit()
                .putString(CHAT_KEY, gson.toJson(clean))
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun nowTime(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun currentMonthStr(): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    /** 本周一 yyyy-MM-dd（与 Web mondayStr 一致） */
    private fun mondayStr(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val offset = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
        cal.add(Calendar.DAY_OF_MONTH, offset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    /** 本周日 yyyy-MM-dd */
    private fun sundayStr(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val offset = if (day == Calendar.SUNDAY) 0 else Calendar.SATURDAY - day + 1
        cal.add(Calendar.DAY_OF_MONTH, offset)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    /** 含指定日期所在自然周（周一～周日） */
    private fun weekRangeContaining(dayIso: String): Pair<String, String> {
        val cal = Calendar.getInstance()
        val parts = dayIso.split("-")
        if (parts.size >= 3) {
            val y = parts[0].toIntOrNull() ?: return mondayStr() to sundayStr()
            val m = parts[1].toIntOrNull() ?: return mondayStr() to sundayStr()
            val d = parts[2].toIntOrNull() ?: return mondayStr() to sundayStr()
            cal.set(y, m - 1, d, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        val toMonday = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
        cal.add(Calendar.DAY_OF_MONTH, toMonday)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val start = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val end = fmt.format(cal.time)
        return start to end
    }

    companion object {
        private const val CHAT_KEY = "qianwen_chat_history"
        private const val KNOWLEDGE_KEY = "yuecheng_knowledge_uploads"
        private const val REPORT_DRAFTS_KEY = "yuecheng_report_drafts_v1"
    }
}
