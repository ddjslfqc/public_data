package com.fuusy.hiddendanger.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.AiAssistantMode
import com.fuusy.hiddendanger.data.AiDraftField
import com.fuusy.hiddendanger.data.MyReportType
import com.fuusy.hiddendanger.data.QianwenOption
import com.fuusy.hiddendanger.data.ReportSubTab
import com.fuusy.hiddendanger.data.WeeklyOkrRow
import com.fuusy.hiddendanger.databinding.ActivityAiAssistantBinding
import com.fuusy.hiddendanger.ui.adapter.AiChatAdapter
import com.fuusy.hiddendanger.ui.adapter.KnowledgeUploadAdapter
import com.fuusy.hiddendanger.ui.widget.SegmentTabUi
import com.fuusy.hiddendanger.ui.widget.YuechengDatePicker
import com.fuusy.hiddendanger.util.OkrFileHelper
import com.fuusy.hiddendanger.viewmodel.AiAssistantViewModel
import java.util.Calendar
import java.util.Locale

@Route(path = "/hiddendanger/AiAssistantActivity")
class AiAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiAssistantBinding
    private val viewModel: AiAssistantViewModel by viewModels()
    private val chatAdapter = AiChatAdapter(
        onToggleSources = { viewModel.toggleSources(it) },
        onCopy = { text ->
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ai", text))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        },
        onDelete = { id ->
            AlertDialog.Builder(this)
                .setTitle("删除消息")
                .setMessage("确定删除这条消息吗？")
                .setPositiveButton("删除") { _, _ -> viewModel.deleteMessage(id) }
                .setNegativeButton("取消", null)
                .show()
        },
        onSkipTyping = { viewModel.skipTypewriter() },
        onShowTiming = { msg -> showTimingDetailDialog(msg) },
        onJumpToSource = { messageId, rank ->
            viewModel.jumpToSource(messageId, rank)
            // 展开后来源在气泡下方，略滚一点保证高亮行可见
            binding.rvMessages.post {
                val list = viewModel.messages.value.orEmpty()
                val index = list.indexOfFirst { it.id == messageId }
                if (index >= 0) {
                    binding.rvMessages.smoothScrollToPosition(index)
                }
            }
        },
        onOpenSourceUrl = { url ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
            }
        }
    )
    private val knowledgeAdapter = KnowledgeUploadAdapter()

    private val pickKnowledgeFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val file = OkrFileHelper.copyUriToTempFile(this, uri) ?: run {
            Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        if (file.length() > MAX_KNOWLEDGE_BYTES) {
            Toast.makeText(this, "文件不能超过 10MB", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        viewModel.uploadKnowledgeFile(file)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    /** 语音开始前输入框已有内容，用于 partial/final 结果拼接 */
    private var voiceInputBase = ""
    private var renderingFields = false
    private var voiceAvailable = false

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoice() else Toast.makeText(this, "需要麦克风权限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = Color.WHITE
        // 顶：状态栏；底：输入栏/技能页加导航条。键盘靠 adjustResize，不叠 IME inset
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            binding.toolbar.updatePadding(
                top = status.top,
                left = cutout.left,
                right = cutout.right
            )
            // 手势导航时 nav.bottom 可能很小，额外抬高输入区避免贴底切边
            val base = (20 * resources.displayMetrics.density).toInt()
            val minBottom = (28 * resources.displayMetrics.density).toInt()
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            // 键盘弹起时缩小底边距，避免输入区被顶上去后文字区域被裁切（华为等机型常见）
            val composerBottom = if (imeVisible) {
                (8 * resources.displayMetrics.density).toInt()
            } else {
                (base + nav.bottom).coerceAtLeast(minBottom)
            }
            binding.composer.updatePadding(bottom = composerBottom)
            binding.skillPanel.updatePadding(bottom = (nav.bottom + base).coerceAtLeast(minBottom))
            binding.knowledgePanel.updatePadding(bottom = (nav.bottom + base).coerceAtLeast(minBottom))
            insets
        }
        SegmentTabUi.ensureTrackClip(binding.mainSegmentTrack)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.itemAnimator = null // 打字机刷新不闪动画
        binding.rvMessages.setHasFixedSize(false)
        binding.rvMessages.adapter = chatAdapter
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMyReports.setOnClickListener {
            startActivity(Intent(this, MyReportsActivity::class.java))
        }
        binding.btnNewChat.setOnClickListener {
            val hasChat = chatAdapter.currentList.any { it.role != "thinking" }
            if (!hasChat) {
                Toast.makeText(this, "当前已是新对话", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("新对话")
                .setMessage("将清空当前会话记录，是否继续？")
                .setPositiveButton("清空") { _, _ ->
                    viewModel.newChat()
                    Toast.makeText(this, "已开启新对话", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        binding.tabChat.setOnClickListener {
            viewModel.rememberSkillStatement(binding.etSkillStatement.text?.toString().orEmpty())
            viewModel.setMode(AiAssistantMode.CHAT)
        }
        // 工单入口暂时隐藏，逻辑保留便于后续打开
        binding.tabWorkorder.isVisible = false
        binding.tabDaily.setOnClickListener {
            viewModel.rememberSkillStatement(binding.etSkillStatement.text?.toString().orEmpty())
            viewModel.setMode(AiAssistantMode.DAILY)
        }
        binding.tabKnowledge.setOnClickListener {
            viewModel.setMode(AiAssistantMode.KNOWLEDGE)
        }
        binding.tabReportDaily.setOnClickListener {
            viewModel.setReportSubTab(ReportSubTab.DAILY)
        }
        binding.tabReportWeekly.setOnClickListener {
            viewModel.setReportSubTab(ReportSubTab.WEEKLY)
        }
        binding.tabReportMonthly.setOnClickListener {
            viewModel.setReportSubTab(ReportSubTab.MONTHLY)
        }

        binding.rvKnowledgeUploads.layoutManager = LinearLayoutManager(this)
        binding.rvKnowledgeUploads.adapter = knowledgeAdapter
        binding.zoneUpload.setOnClickListener { openKnowledgeFilePicker() }

        binding.etSkillStatement.doAfterTextChanged { editable ->
            viewModel.rememberSkillStatement(editable?.toString().orEmpty())
            updateBusyUi()
        }
        binding.etSkillStatement.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN &&
                event.metaState and KeyEvent.META_SHIFT_ON == 0
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                if (viewModel.reportSubTab.value == ReportSubTab.DAILY) {
                    val text = binding.etSkillStatement.text?.toString().orEmpty()
                    if (text.isNotBlank()) viewModel.generateDaily(text)
                }
                true
            } else false
        }
        binding.btnSend.setOnClickListener { sendCurrent() }
        binding.etInput.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                sendCurrent()
                true
            } else false
        }
        setupChatInputField()

        binding.chipWebSearch.setOnClickListener {
            viewModel.toggleWebSearch()
        }
        listOf(
            binding.chipSuggest1 to "连江项目最近进度怎么样？",
            binding.chipSuggest2 to "海门巡检和连江有什么区别？",
            binding.chipSuggest3 to "帮我总结一下本周工作重点"
        ).forEach { (chip, q) ->
            chip.setOnClickListener {
                if (viewModel.mode.value != AiAssistantMode.CHAT) {
                    viewModel.setMode(AiAssistantMode.CHAT)
                }
                binding.etInput.setText(q)
                sendCurrent()
            }
        }
        binding.etInput.doAfterTextChanged { refreshSendButton() }
        refreshSendButton()
        binding.btnGenerate.setOnClickListener {
            if (viewModel.serviceOk.value == false) {
                Toast.makeText(this, "悦城服务暂不可用，请稍后重试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = binding.etSkillStatement.text?.toString().orEmpty()
            when (viewModel.mode.value) {
                AiAssistantMode.WORKORDER -> viewModel.generateWorkOrder(text)
                AiAssistantMode.DAILY -> viewModel.generateReportSkill(text)
                else -> {}
            }
        }
        binding.btnSubmit.setOnClickListener {
            when (viewModel.mode.value) {
                AiAssistantMode.WORKORDER -> viewModel.submitWorkOrder()
                AiAssistantMode.DAILY -> {
                    if (viewModel.needArchiveRetry.value == true) {
                        viewModel.retryArchive()
                    } else {
                        viewModel.submitReportSkill()
                    }
                }
                else -> {}
            }
        }
        binding.btnSkillNext.setOnClickListener {
            viewModel.resetSkillForNext()
            binding.etSkillStatement.setText("")
            binding.tvSubmitSuccess.isVisible = false
            binding.btnGotoWeekly.isVisible = false
            binding.btnViewMyReports.isVisible = false
            if (viewModel.reportSubTab.value == ReportSubTab.DAILY) {
                binding.etSkillStatement.requestFocus()
            }
            updateBusyUi()
            renderReportSubUi(viewModel.reportSubTab.value ?: ReportSubTab.DAILY)
        }
        binding.btnGotoWeekly.setOnClickListener {
            viewModel.resetSkillForNext()
            binding.etSkillStatement.setText("")
            binding.tvSubmitSuccess.isVisible = false
            binding.btnViewMyReports.isVisible = false
            viewModel.setReportSubTab(ReportSubTab.WEEKLY)
        }
        binding.btnViewMyReports.setOnClickListener {
            val type = when (viewModel.reportSubTab.value) {
                ReportSubTab.WEEKLY -> MyReportType.WEEKLY.apiValue
                ReportSubTab.MONTHLY -> MyReportType.MONTHLY.apiValue
                else -> MyReportType.DAILY.apiValue
            }
            startActivity(
                Intent(this, MyReportsActivity::class.java).apply {
                    putExtra(MyReportsActivity.EXTRA_TYPE, type)
                }
            )
        }
        binding.rowDailyDate.setOnClickListener {
            if (viewModel.submitSuccess.value?.isNotBlank() == true) return@setOnClickListener
            if (viewModel.skillSubmitting.value == true) return@setOnClickListener
            showEntryDailyDatePicker()
        }
        binding.rowWeeklyPeriod.setOnClickListener {
            if (viewModel.submitSuccess.value?.isNotBlank() == true) return@setOnClickListener
            if (viewModel.skillSubmitting.value == true) return@setOnClickListener
            showEntryWeeklyDatePicker()
        }
        binding.rowMonthlyPeriod.setOnClickListener {
            if (viewModel.submitSuccess.value?.isNotBlank() == true) return@setOnClickListener
            if (viewModel.skillSubmitting.value == true) return@setOnClickListener
            showEntryMonthlyMonthPicker()
        }
        binding.btnOpenOrders.setOnClickListener {
            ARouter.getInstance()
                .build("/project/HistoryOrderActivity")
                .withString(
                    com.fuusy.project.ui.activity.HistoryOrderActivity.EXTRA_LIST_MODE,
                    com.fuusy.project.ui.activity.HistoryOrderActivity.MODE_RELATED
                )
                .navigation()
        }

        setupVoice()
        observe()
        viewModel.init()
        applyOpenExtras()
    }

    private fun applyOpenExtras() {
        when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_DAILY -> {
                viewModel.setMode(AiAssistantMode.DAILY)
                when (intent.getStringExtra(EXTRA_REPORT_SUB)?.lowercase()) {
                    "weekly" -> viewModel.setReportSubTab(ReportSubTab.WEEKLY)
                    "monthly" -> viewModel.setReportSubTab(ReportSubTab.MONTHLY)
                    else -> viewModel.setReportSubTab(ReportSubTab.DAILY)
                }
            }
            TAB_KNOWLEDGE -> viewModel.setMode(AiAssistantMode.KNOWLEDGE)
            TAB_CHAT -> viewModel.setMode(AiAssistantMode.CHAT)
        }
    }

    private fun refreshSendButton() {
        val busy = viewModel.sending.value == true
        val hasText = !binding.etInput.text.isNullOrBlank()
        binding.btnSend.isEnabled = !busy && hasText
        binding.btnSend.alpha = if (binding.btnSend.isEnabled) 1f else 0.45f
        binding.btnSend.contentDescription = if (busy) "回答中" else "发送"
        binding.etInput.hint = if (busy) "正在回答，请稍候…" else "继续追问…"
    }

    private fun sendCurrent() {
        val text = binding.etInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        if (viewModel.serviceOk.value == false) {
            Toast.makeText(this, "悦城服务暂不可用，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.sending.value == true) {
            Toast.makeText(this, "正在回答中…", Toast.LENGTH_SHORT).show()
            return
        }
        binding.etInput.setText("")
        refreshSendButton()
        hideKeyboard()
        binding.etInput.clearFocus()
        viewModel.sendChat(text)
        scrollChatToBottom(force = true)
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
    }

    /** 生成/打字机过程中跟滚，避免最新内容被底部输入栏或键盘挡住 */
    private fun scrollChatToBottom(force: Boolean = false) {
        val list = viewModel.messages.value.orEmpty()
        if (list.isEmpty()) return
        val lm = binding.rvMessages.layoutManager as? LinearLayoutManager ?: return
        val lastIndex = list.lastIndex
        val last = list.lastOrNull()
        val generating = last?.isTyping == true || last?.role == "thinking"
        if (!force && !generating) {
            val lastVisible = lm.findLastVisibleItemPosition()
            if (lastVisible < lastIndex - 1) return
        }
        binding.rvMessages.post {
            binding.rvMessages.scrollToPosition(lastIndex)
            val lastChild = lm.findViewByPosition(lastIndex) ?: return@post
            val overflow = lastChild.bottom -
                (binding.rvMessages.height - binding.rvMessages.paddingBottom)
            if (overflow > 0) binding.rvMessages.scrollBy(0, overflow)
        }
    }

    /** 兼容系统键盘语音：勿用 setRawInputType，否则华为等 IME 无法把识别结果写回输入框 */
    private fun setupChatInputField() {
        binding.etInput.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        binding.etInput.imeOptions = EditorInfo.IME_ACTION_SEND
        binding.etInput.setHorizontallyScrolling(false)
    }

    private fun applyVoiceTextToInput(recognized: String, final: Boolean) {
        val text = recognized.trim()
        if (text.isBlank()) return
        val merged = listOf(voiceInputBase.trim(), text)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        when (viewModel.mode.value) {
            AiAssistantMode.CHAT -> {
                binding.etInput.setText(merged)
                binding.etInput.setSelection(merged.length)
                if (final) refreshSendButton()
            }
            else -> {
                binding.etSkillStatement.setText(merged)
                binding.etSkillStatement.setSelection(merged.length)
            }
        }
    }

    private fun captureVoiceInputBase() {
        voiceInputBase = when (viewModel.mode.value) {
            AiAssistantMode.CHAT -> binding.etInput.text?.toString().orEmpty()
            else -> binding.etSkillStatement.text?.toString().orEmpty()
        }
    }

    private fun setupVoice() {
        voiceAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        binding.btnVoice.isVisible = voiceAvailable
        binding.btnSkillVoice.isVisible = voiceAvailable
        if (!voiceAvailable) return
        val clickListener = android.view.View.OnClickListener {
            if (listening) {
                stopVoice()
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startVoice()
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        binding.btnVoice.setOnClickListener(clickListener)
        binding.btnSkillVoice.setOnClickListener(clickListener)
    }

    private fun startVoice() {
        stopVoice()
        captureVoiceInputBase()
        // 收起系统键盘，避免与 SpeechRecognizer 抢焦点导致识别结果写不进输入框
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                binding.btnVoice.alpha = 0.5f
                binding.btnSkillVoice.alpha = 0.5f
                Toast.makeText(this@AiAssistantActivity, "正在听…", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                listening = false
                binding.btnVoice.alpha = 1f
                binding.btnSkillVoice.alpha = 1f
            }

            override fun onError(error: Int) {
                listening = false
                binding.btnVoice.alpha = 1f
                binding.btnSkillVoice.alpha = 1f
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    Toast.makeText(
                        this@AiAssistantActivity,
                        voiceErrorMessage(error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                binding.btnVoice.alpha = 1f
                binding.btnSkillVoice.alpha = 1f
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                applyVoiceTextToInput(text, final = true)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                applyVoiceTextToInput(text, final = false)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.startListening(intent)
    }

    private fun voiceErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "无法录音，请检查麦克风"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
        SpeechRecognizer.ERROR_NETWORK -> "语音识别需要网络"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "未识别到内容，请重试"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音"
        else -> "语音识别失败($error)"
    }

    private fun stopVoice() {
        listening = false
        binding.btnVoice.alpha = 1f
        binding.btnSkillVoice.alpha = 1f
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private var lastMessageId: String? = null

    private fun observe() {
        viewModel.messages.observe(this) { list ->
            val lastId = list.lastOrNull()?.id
            val isNewBubble = lastId != null && lastId != lastMessageId
            lastMessageId = lastId
            val last = list.lastOrNull()
            val generating = last?.isTyping == true || last?.role == "thinking"
            chatAdapter.submitList(list) {
                if (list.isEmpty()) return@submitList
                scrollChatToBottom(force = isNewBubble || generating)
            }
            val empty = list.isEmpty() && viewModel.mode.value == AiAssistantMode.CHAT
            binding.tvEmpty.isVisible = empty
            binding.chipRow.isVisible = viewModel.mode.value == AiAssistantMode.CHAT
        }
        viewModel.sending.observe(this) { updateBusyUi() }
        viewModel.skillLoading.observe(this) { updateBusyUi() }
        viewModel.skillSubmitting.observe(this) { updateBusyUi() }
        viewModel.skillReviewing.observe(this) { updateBusyUi() }
        viewModel.serviceOk.observe(this) { ok ->
            binding.tvStatus.text = when (ok) {
                null -> "连接中"
                true -> "服务正常"
                false -> "服务不可用"
            }
            binding.tvStatus.setTextColor(
                when (ok) {
                    true -> 0xFF047857.toInt()
                    false -> 0xFFB91C1C.toInt()
                    else -> 0xFF686D79.toInt()
                }
            )
            binding.tvStatus.setBackgroundResource(
                when (ok) {
                    true -> R.drawable.bg_ai_alert_ok
                    false -> R.drawable.bg_ai_alert_error
                    else -> R.drawable.bg_ai_status_pill
                }
            )
        }
        viewModel.mode.observe(this) { mode -> renderMode(mode) }
        viewModel.reportSubTab.observe(this) { tab ->
            if (viewModel.mode.value == AiAssistantMode.DAILY) {
                renderReportSubUi(tab)
                renderOkrSection(force = true)
                updateBusyUi()
            }
        }
        viewModel.skillError.observe(this) { err ->
            binding.tvSkillError.isVisible = !err.isNullOrBlank()
            binding.tvSkillError.text = err
        }
        viewModel.missingHint.observe(this) { hint ->
            binding.tvMissingHint.isVisible = !hint.isNullOrBlank()
            binding.tvMissingHint.text = hint
        }
        viewModel.submitSuccess.observe(this) { msg ->
            val ok = !msg.isNullOrBlank()
            binding.tvSubmitSuccess.isVisible = ok
            binding.tvSubmitSuccess.text = msg
            binding.btnOpenOrders.isVisible = false
            // 提交成功后重新渲染字段为只读，并露出「再写一条」
            renderDraftFields(
                viewModel.draftFields.value.orEmpty(),
                viewModel.woOptions.value.orEmpty(),
                force = true
            )
            renderOkrSection(force = true)
            updateBusyUi()
        }
        viewModel.dailyOverwritePrompt.observe(this) { date ->
            if (date.isNullOrBlank()) return@observe
            viewModel.consumeDailyOverwritePrompt()
            val label = formatReportDateLabel(date)
            AlertDialog.Builder(this)
                .setTitle("覆盖已有日报")
                .setMessage("你在 $label 已提交过日报，继续提交将覆盖原内容。确定覆盖吗？")
                .setPositiveButton("覆盖提交") { _, _ ->
                    viewModel.submitDaily(confirmOverwrite = true)
                }
                .setNegativeButton("取消", null)
                .show()
        }
        viewModel.dailyReportDate.observe(this) { date ->
            val raw = date.orEmpty().trim()
            binding.tvDailyDate.text = if (raw.isBlank()) "选择日期" else formatReportDateLabel(raw)
        }
        viewModel.weeklyWeekStart.observe(this) { refreshWeeklyPeriodLabel() }
        viewModel.weeklyWeekEnd.observe(this) { refreshWeeklyPeriodLabel() }
        viewModel.monthlyMonth.observe(this) { month ->
            val raw = month.orEmpty().trim()
            binding.tvMonthlyPeriod.text = if (raw.isBlank()) "选择月份" else formatMonthLabel(raw)
        }
        viewModel.draftId.observe(this) { id ->
            val daily = viewModel.mode.value == AiAssistantMode.DAILY
            val loading = viewModel.skillLoading.value == true
            val show = !id.isNullOrBlank() && daily && !loading
            binding.draftCard.isVisible = show
            updateBusyUi()
            renderOkrSection(force = true)
            if (show) {
                // 仅新草稿首次出现时滚一次，避免 OKR 轮询反复抢滚动
                revealDraftCardIfNeeded(id)
            } else if (id.isNullOrBlank()) {
                lastRevealedDraftId = null
            }
        }
        viewModel.draftFields.observe(this) { fields ->
            applyDraftFields(fields, viewModel.woOptions.value.orEmpty())
        }
        viewModel.woOptions.observe(this) { opts ->
            applyDraftFields(viewModel.draftFields.value.orEmpty(), opts)
        }
        viewModel.needArchiveRetry.observe(this) { updateBusyUi() }
        viewModel.okrRows.observe(this) { renderOkrSection() }
        viewModel.okrStatus.observe(this) { status ->
            val prev = lastOkrStatus
            lastOkrStatus = status
            renderOkrSection()
            // OKR 从 pending 算出结果后，只滚一次到 OKR 区，让效果可见
            if (prev.equals("pending", ignoreCase = true) &&
                !status.isNullOrBlank() &&
                !status.equals("pending", ignoreCase = true) &&
                viewModel.reportSubTab.value == ReportSubTab.WEEKLY &&
                binding.okrSection.isVisible
            ) {
                revealOkrSectionOnce()
            }
        }
        viewModel.okrHint.observe(this) { renderOkrSection() }
        viewModel.webSearchOn.observe(this) { on ->
            val active = on == true
            binding.chipWebSearch.setBackgroundResource(
                if (active) R.drawable.bg_ai_chip_active else R.drawable.bg_ai_chip
            )
            binding.chipWebSearch.setTextColor(
                if (active) 0xFFFFFFFF.toInt() else 0xFF1465EB.toInt()
            )
            // 文案保持「联网搜索」，用底色区分开/关，避免换字后看起来像空白
            binding.chipWebSearch.text = "联网搜索"
            binding.chipWebSearch.alpha = 1f
        }
        viewModel.toast.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }
        viewModel.knowledgeUploads.observe(this) { list ->
            knowledgeAdapter.submitList(list)
            binding.tvKnowledgeEmpty.isVisible = list.isEmpty()
        }
        viewModel.knowledgeBusy.observe(this) { updateKnowledgeUi() }
    }

    private fun openKnowledgeFilePicker() {
        if (viewModel.knowledgeBusy.value == true) {
            Toast.makeText(this, "正在上传中…", Toast.LENGTH_SHORT).show()
            return
        }
        pickKnowledgeFile.launch(arrayOf("*/*"))
    }

    private fun updateKnowledgeUi() {
        val busy = viewModel.knowledgeBusy.value == true
        binding.zoneUpload.alpha = if (busy) 0.55f else 1f
        binding.zoneUpload.isEnabled = !busy
        binding.tvUploadZoneTitle.text = if (busy) "正在上传…" else "点击选择文件"
        binding.tvUploadZoneHint.text = if (busy) {
            "请稍候，完成后会出现在下方列表"
        } else {
            "PDF · Word · 图片 · Markdown 等"
        }
    }

    /** 对齐 Web：圆角卡片 + 分阶段列表（左标签、右数值） */
    private fun showTimingDetailDialog(msg: com.fuusy.hiddendanger.data.AiChatMessage) {
        val rows = AiChatAdapter.buildTimingRows(msg)
        val content = layoutInflater.inflate(R.layout.dialog_ai_timing_detail, null, false)
        val list = content.findViewById<LinearLayout>(R.id.ll_timing_rows)
        list.removeAllViews()
        // outline 圆角裁切，和 Web .timing-list 一致
        list.clipToOutline = true
        list.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                val r = 12 * resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        rows.forEachIndexed { index, row ->
            val rowView = layoutInflater.inflate(R.layout.item_ai_timing_row, list, false)
            rowView.findViewById<TextView>(R.id.tv_timing_label).text = row.label
            rowView.findViewById<TextView>(R.id.tv_timing_value).text = row.value
            list.addView(rowView)
            if (index < rows.lastIndex) {
                list.addView(View(this).apply {
                    setBackgroundColor(0xFFE4E9F1.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    )
                })
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()
        content.findViewById<TextView>(R.id.btn_timing_close).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val margin = (36 * resources.displayMetrics.density).toInt()
            val w = (resources.displayMetrics.widthPixels - margin * 2).coerceAtMost(
                (360 * resources.displayMetrics.density).toInt()
            )
            setLayout(w, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun updateBusyUi() {
        val sending = viewModel.sending.value == true
        val loading = viewModel.skillLoading.value == true
        val submitting = viewModel.skillSubmitting.value == true
        val success = !viewModel.submitSuccess.value.isNullOrBlank()
        val hasDraft = !viewModel.draftId.value.isNullOrBlank()
        val chat = viewModel.mode.value == AiAssistantMode.CHAT
        val daily = viewModel.mode.value == AiAssistantMode.DAILY
        // 聊天/日报都不用整屏转圈：聊天靠气泡，日报靠按钮文案 + 草稿区占位
        binding.progress.isVisible = false
        binding.btnSend.isEnabled = !sending && !binding.etInput.text.isNullOrBlank()
        binding.btnSend.alpha = if (binding.btnSend.isEnabled) 1f else 0.45f
        binding.btnSend.contentDescription = if (sending) "回答中" else "发送"
        binding.etInput.hint = if (sending) "正在回答，请稍候…" else "继续追问…"

        // 生成中占位
        binding.draftLoading.isVisible = daily && loading
        binding.tvDraftLoadingTitle.text = when (viewModel.reportSubTab.value) {
            ReportSubTab.WEEKLY -> "正在生成周报…"
            ReportSubTab.MONTHLY -> "正在生成月报…"
            else -> "正在生成草稿…"
        }
        // 生成中先收起旧草稿，避免「按钮在转、下面还是旧内容」的错觉
        binding.draftCard.isVisible = daily && hasDraft && !loading

        // 生成草稿：提交成功后锁定；有草稿未提交时可「重新生成」
        val sub = viewModel.reportSubTab.value ?: ReportSubTab.DAILY
        val genLabel = when (sub) {
            ReportSubTab.WEEKLY -> "生成周报草稿"
            ReportSubTab.MONTHLY -> "生成月报草稿"
            else -> "生成草稿"
        }
        val regenLabel = when (sub) {
            ReportSubTab.WEEKLY -> "重新生成周报"
            ReportSubTab.MONTHLY -> "重新生成月报"
            else -> "重新生成草稿"
        }
        val submitLabel = when (sub) {
            ReportSubTab.WEEKLY -> "确认提交周报"
            ReportSubTab.MONTHLY -> "确认提交月报"
            else -> "确认提交日报"
        }
        val dailyStatementBlank =
            sub == ReportSubTab.DAILY && binding.etSkillStatement.text.isNullOrBlank()
        val serviceDown = viewModel.serviceOk.value == false
        binding.btnGenerate.isEnabled =
            !loading && !submitting && !success && !dailyStatementBlank && !serviceDown
        binding.btnGenerate.text = when {
            loading -> "生成中…"
            success -> "已提交"
            serviceDown -> "服务不可用"
            hasDraft -> regenLabel
            else -> genLabel
        }
        binding.btnGenerate.setBackgroundResource(
            if (binding.btnGenerate.isEnabled) R.drawable.bg_ai_btn_generate
            else R.drawable.bg_ai_btn_generate_disabled
        )
        binding.btnGenerate.setTextColor(
            if (binding.btnGenerate.isEnabled) 0xFF0065FD.toInt() else 0xFF8B93A7.toInt()
        )
        binding.btnGenerate.alpha = 1f

        // 提交成功：隐藏主按钮，只留成功条 +「再写一条」；归档失败时可「重试归档」
        val needArchiveRetry = viewModel.needArchiveRetry.value == true
        binding.btnSubmit.isVisible = hasDraft && (!success || needArchiveRetry)
        binding.btnSubmit.isEnabled = !submitting && hasDraft
        binding.btnSubmit.text = when {
            submitting -> "提交中…"
            needArchiveRetry -> "重试归档"
            else -> submitLabel
        }
        binding.btnSubmit.setBackgroundResource(
            if (binding.btnSubmit.isEnabled) R.drawable.bg_ai_btn_primary
            else R.drawable.bg_ai_btn_disabled
        )
        binding.btnSkillNext.isVisible = success && !chat
        binding.btnSkillNext.text = when (sub) {
            ReportSubTab.WEEKLY -> "再写一份周报"
            ReportSubTab.MONTHLY -> "再写一份月报"
            else -> "再写一条"
        }
        // 日报提交成功后引导生成周报（融合，不新开模块）
        binding.btnGotoWeekly.isVisible =
            success && !chat && sub == ReportSubTab.DAILY
        // 提交成功后引导回看归档，闭环「写 → 看」
        binding.btnViewMyReports.isVisible = success && !chat
        binding.tvDraftTitle.text = if (success) "已提交内容" else "草稿预览"
        binding.tvDraftBadge.text = if (success) "已提交" else "草稿"
        binding.tvDraftBadge.setTextColor(
            if (success) 0xFF047857.toInt() else 0xFF1465EB.toInt()
        )
        binding.tvDraftBadge.setBackgroundResource(
            if (success) R.drawable.bg_ai_alert_ok else R.drawable.bg_ai_draft_badge
        )

        val needStatement = chat || sub == ReportSubTab.DAILY
        binding.etSkillStatement.isEnabled = needStatement && !success && !submitting
        binding.etSkillStatement.alpha = if (binding.etSkillStatement.isEnabled) 1f else 0.65f
        val dateEnabled = sub == ReportSubTab.DAILY && !success && !submitting
        binding.rowDailyDate.isEnabled = dateEnabled
        binding.rowDailyDate.alpha = if (dateEnabled) 1f else 0.65f
        binding.rowDailyDate.isClickable = dateEnabled
        val periodEnabled = (sub == ReportSubTab.WEEKLY || sub == ReportSubTab.MONTHLY) &&
            !success && !submitting
        binding.rowWeeklyPeriod.isEnabled = sub == ReportSubTab.WEEKLY && periodEnabled
        binding.rowWeeklyPeriod.alpha = if (binding.rowWeeklyPeriod.isEnabled) 1f else 0.65f
        binding.rowWeeklyPeriod.isClickable = binding.rowWeeklyPeriod.isEnabled
        binding.rowMonthlyPeriod.isEnabled = sub == ReportSubTab.MONTHLY && periodEnabled
        binding.rowMonthlyPeriod.alpha = if (binding.rowMonthlyPeriod.isEnabled) 1f else 0.65f
        binding.rowMonthlyPeriod.isClickable = binding.rowMonthlyPeriod.isEnabled
        binding.btnSkillVoice.isEnabled =
            needStatement && !success && !submitting && voiceAvailable && !chat
        binding.btnSkillVoice.alpha = if (binding.btnSkillVoice.isEnabled) 1f else 0.4f
        setDraftFieldsEditable(!success && !submitting)
    }

    /** LinearLayout.isEnabled 不会锁住子 EditText，需逐个设置 */
    private fun setDraftFieldsEditable(editable: Boolean) {
        val container = binding.llDraftFields
        for (i in 0 until container.childCount) {
            when (val child = container.getChildAt(i)) {
                is EditText -> {
                    child.isEnabled = editable
                    child.isFocusable = editable
                    child.isFocusableInTouchMode = editable
                    child.isCursorVisible = editable
                }
                is Spinner -> child.isEnabled = editable
            }
        }
    }

    private fun renderMode(mode: AiAssistantMode) {
        // 工单入口已隐藏：若误入则回到问答
        val safeMode = if (mode == AiAssistantMode.WORKORDER) AiAssistantMode.CHAT else mode
        if (safeMode != mode) {
            viewModel.setMode(safeMode)
            return
        }
        val chat = safeMode == AiAssistantMode.CHAT
        val daily = safeMode == AiAssistantMode.DAILY
        val knowledge = safeMode == AiAssistantMode.KNOWLEDGE
        binding.rvMessages.isVisible = chat
        binding.composer.isVisible = chat
        binding.chipRow.isVisible = chat
        binding.skillPanel.isVisible = daily
        binding.knowledgePanel.isVisible = knowledge
        binding.tvEmpty.isVisible = chat && chatAdapter.currentList.isEmpty()
        binding.btnNewChat.isVisible = chat
        binding.btnSkillVoice.isVisible = voiceAvailable && daily
        binding.tabWorkorder.isVisible = false
        refreshSendButton()

        styleMainTabs(chat, daily, knowledge)

        binding.tvTitle.text = when (safeMode) {
            AiAssistantMode.CHAT -> "悦城助手"
            AiAssistantMode.DAILY -> "写汇报"
            AiAssistantMode.KNOWLEDGE -> "贡献资料"
            else -> "悦城助手"
        }
        if (daily) {
            renderReportSubUi(viewModel.reportSubTab.value ?: ReportSubTab.DAILY)
            val saved = viewModel.skillStatementText()
            if (binding.etSkillStatement.text.isNullOrBlank() && saved.isNotBlank()) {
                binding.etSkillStatement.setText(saved)
            }
        }
        binding.draftCard.isVisible = !viewModel.draftId.value.isNullOrBlank() &&
            daily &&
            viewModel.skillLoading.value != true
        binding.draftLoading.isVisible = daily && viewModel.skillLoading.value == true
        binding.btnOpenOrders.isVisible = false
        updateBusyUi()
        updateKnowledgeUi()
    }

    /** 写日报 Tab 内：日报 / 周报 / 月报 文案与输入区显隐 */
    private fun renderReportSubUi(tab: ReportSubTab) {
        val index = when (tab) {
            ReportSubTab.DAILY -> 0
            ReportSubTab.WEEKLY -> 1
            ReportSubTab.MONTHLY -> 2
        }
        SegmentTabUi.applyUnderline(
            listOf(binding.tabReportDaily, binding.tabReportWeekly, binding.tabReportMonthly),
            index
        )
        when (tab) {
            ReportSubTab.DAILY -> {
                binding.tvSkillTitle.text = "写日报"
                binding.tvSkillDesc.text = "可选择日期补写历史日报；点「生成草稿」后可再编辑提交。"
                binding.etSkillStatement.hint = "例如：今天完成移动端接口联调，并处理了登录状态异常问题"
                binding.etSkillStatement.isVisible = true
                binding.rowDailyDate.isVisible = true
                binding.rowWeeklyPeriod.isVisible = false
                binding.rowMonthlyPeriod.isVisible = false
                binding.btnSkillVoice.isVisible = voiceAvailable
            }
            ReportSubTab.WEEKLY -> {
                binding.tvSkillTitle.text = "生成周报"
                binding.tvSkillDesc.text = "选择目标周，聚合该周日报生成草稿并关联 OKR 进度建议。"
                binding.etSkillStatement.isVisible = false
                binding.rowDailyDate.isVisible = false
                binding.rowWeeklyPeriod.isVisible = true
                binding.rowMonthlyPeriod.isVisible = false
                binding.btnSkillVoice.isVisible = false
                refreshWeeklyPeriodLabel()
            }
            ReportSubTab.MONTHLY -> {
                binding.tvSkillTitle.text = "生成月报"
                binding.tvSkillDesc.text = "选择目标月份，聚合该月已定稿周报生成月报草稿。"
                binding.etSkillStatement.isVisible = false
                binding.rowDailyDate.isVisible = false
                binding.rowWeeklyPeriod.isVisible = false
                binding.rowMonthlyPeriod.isVisible = true
                binding.btnSkillVoice.isVisible = false
            }
        }
        val genLp = binding.btnGenerate.layoutParams as android.widget.LinearLayout.LayoutParams
        genLp.marginStart = if (binding.btnSkillVoice.isVisible) {
            (10 * resources.displayMetrics.density).toInt()
        } else {
            0
        }
        binding.btnGenerate.layoutParams = genLp
        binding.draftCard.isVisible =
            !viewModel.draftId.value.isNullOrBlank() &&
                viewModel.mode.value == AiAssistantMode.DAILY &&
                viewModel.skillLoading.value != true
    }

    private fun styleMainTabs(chat: Boolean, daily: Boolean, knowledge: Boolean) {
        val index = when {
            chat -> 0
            daily -> 1
            else -> 2
        }
        SegmentTabUi.apply(
            listOf(binding.tabChat, binding.tabDaily, binding.tabKnowledge),
            index
        )
    }

    private var forceOkrRebuild = false
    /** 展开「AI 依据」的 KR id，重建行时保持开合状态 */
    private val expandedOkrReasons = mutableSetOf<Long>()

    private fun renderOkrSection(force: Boolean = false) {
        val weekly = viewModel.reportSubTab.value == ReportSubTab.WEEKLY
        val hasDraft = !viewModel.draftId.value.isNullOrBlank()
        // 周报只要有草稿就展示 OKR 区（与 Web：有 draft + status 一致；缺 status 也给出说明）
        val show = weekly && hasDraft
        binding.okrSection.isVisible = show
        if (!show) return

        val status = viewModel.okrStatus.value.orEmpty().ifBlank { "missing" }
        val locked = !viewModel.submitSuccess.value.isNullOrBlank()
        val rows = viewModel.okrRows.value.orEmpty()
        val checkedCount = rows.count { it.checked }
        binding.tvOkrCount.isVisible = checkedCount > 0
        binding.tvOkrCount.text = "已选 $checkedCount 项"

        binding.tvOkrSectionHint.text = if (status == "pending") {
            "周报已生成，正在关联本人 OKR…"
        } else {
            "勾选要更新的 KR；进度只能上调。"
        }

        val emptyHint = when {
            status == "pending" ->
                viewModel.okrHint.value?.takeIf { it.isNotBlank() }
                    ?: "OKR 关联计算中，请稍候（不影响继续编辑周报）"
            else -> viewModel.okrHint.value
        }
        val showList = status == "success" && rows.isNotEmpty()
        binding.tvOkrEmpty.isVisible = !showList && !emptyHint.isNullOrBlank()
        binding.tvOkrEmpty.text = emptyHint
        binding.tvOkrEmpty.setBackgroundResource(
            if (status == "pending") R.drawable.bg_ai_alert_warn else R.drawable.bg_ai_alert_warn
        )
        binding.llOkrRows.isVisible = showList
        if (!showList) {
            binding.llOkrRows.removeAllViews()
            binding.llOkrRows.tag = null
            return
        }

        val container = binding.llOkrRows
        val structureTag = rows.map {
            "${it.krId}:${it.checked}:$locked:${it.krId in expandedOkrReasons}:${it.confirmValue}"
        }
        val shouldRebuild = force || forceOkrRebuild ||
            container.childCount != rows.size ||
            container.tag != structureTag
        forceOkrRebuild = false
        if (!shouldRebuild) return
        container.removeAllViews()
        container.tag = structureTag
        val density = resources.displayMetrics.density
        rows.forEach { row ->
            container.addView(buildOkrRowView(row, locked, density))
        }
    }

    private fun buildOkrRowView(
        row: WeeklyOkrRow,
        locked: Boolean,
        density: Float
    ): LinearLayout {
        val unit = row.unit.ifBlank { "%" }
        val editable = row.checked && !locked
        val suggestFloor = maxOf(row.suggestedValue, row.currentValue)
        val confirm = row.confirmValue.toDoubleOrNull() ?: suggestFloor
        val displayTo = if (row.checked) confirm else suggestFloor
        val expanded = row.krId in expandedOkrReasons
        val reasonText = row.reason.ifBlank { row.evidence }
        val matchSuggest = nearProgress(displayTo, suggestFloor)
        val matchCurrent = nearProgress(displayTo, row.currentValue)
        val matchCustom = row.checked && !matchSuggest && !matchCurrent

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (18 * density).toInt(),
                (20 * density).toInt(),
                (18 * density).toInt()
            )
            setBackgroundResource(R.drawable.bg_ai_okr_row)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt() }
        }

        // —— 标题：完整展示，绝不截断 ——
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.TOP
        }
        head.addView(CheckBox(this).apply {
            isChecked = row.checked
            isEnabled = !locked
            minWidth = 0
            minimumWidth = 0
            setPadding(0, (2 * density).toInt(), (4 * density).toInt(), 0)
            setOnCheckedChangeListener { _, checked ->
                viewModel.updateOkrChecked(row.krId, checked)
                forceOkrRebuild = true
                renderOkrSection(force = true)
            }
        })
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        names.addView(TextView(this).apply {
            text = "O · ${row.objectiveTitle.ifBlank { "目标" }}"
            setTextColor(0xFF898FA0.toInt())
            textSize = 13f
            setLineSpacing(3 * density, 1f)
        })
        names.addView(TextView(this).apply {
            text = "KR · ${row.krTitle.ifBlank { "关键结果" }}"
            setTextColor(0xFF111827.toInt())
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(3 * density, 1f)
            setPadding(0, (6 * density).toInt(), 0, 0)
        })
        head.addView(names)
        root.addView(head)

        // —— 进度主视觉（无灰底框，更疏朗） ——
        val compare = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (18 * density).toInt() }
        }
        compare.addView(TextView(this).apply {
            text = "${formatUiProgress(row.currentValue)}$unit"
            setTextColor(0xFFA0A6B3.toInt())
            textSize = 16f
        })
        compare.addView(TextView(this).apply {
            text = "  →  "
            setTextColor(0xFFC5CAD6.toInt())
            textSize = 16f
        })
        val tvConfirm = TextView(this).apply {
            text = "${formatUiProgress(displayTo)}$unit"
            setTextColor(0xFF1465EB.toInt())
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        compare.addView(tvConfirm)
        row.confidence?.let { conf ->
            compare.addView(TextView(this).apply {
                text = "置信 ${(conf * 100).toInt()}%"
                setTextColor(0xFF0F766E.toInt())
                textSize = 11f
                setBackgroundResource(R.drawable.bg_ai_okr_confidence)
                setPadding(
                    (8 * density).toInt(),
                    (4 * density).toInt(),
                    (8 * density).toInt(),
                    (4 * density).toInt()
                )
            })
        }
        root.addView(compare)

        // —— 勾选后：细滑条 + 轻量 pill ——
        var seekBarRef: SeekBar? = null
        if (editable || (row.checked && locked)) {
            val minTenths = (row.currentValue.coerceIn(0.0, 100.0) * 10).roundToInt()
            val maxTenths = 1000
            val seekBar = SeekBar(this).apply {
                max = maxTenths
                progress = (confirm.coerceIn(row.currentValue, 100.0) * 10).roundToInt()
                    .coerceIn(minTenths, maxTenths)
                isEnabled = editable
                splitTrack = false
                progressDrawable = ContextCompat.getDrawable(context, R.drawable.bg_ai_seekbar_progress)
                thumb = ContextCompat.getDrawable(context, R.drawable.bg_ai_seekbar_thumb)
                thumbOffset = 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (14 * density).toInt()
                    marginStart = (-6 * density).toInt()
                    marginEnd = (-6 * density).toInt()
                }
            }
            seekBarRef = seekBar
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val clamped = progress.coerceIn(minTenths, maxTenths)
                    if (sb != null && sb.progress != clamped) {
                        sb.progress = clamped
                        return
                    }
                    tvConfirm.text = "${formatUiProgress(clamped / 10.0)}$unit"
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val value = (sb?.progress ?: minTenths).coerceIn(minTenths, maxTenths) / 10.0
                    viewModel.updateOkrConfirmValue(row.krId, formatUiProgress(value))
                }
            })
            root.addView(seekBar)
            root.addView(TextView(this).apply {
                text = if (editable) {
                    "可上调 · 最低 ${formatUiProgress(row.currentValue)}$unit"
                } else {
                    "已提交，进度只读"
                }
                setTextColor(0xFFA0A6B3.toInt())
                textSize = 11f
                gravity = android.view.Gravity.END
                setPadding(0, (4 * density).toInt(), 0, 0)
            })

            val chips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (16 * density).toInt() }
            }
            chips.addView(
                buildOkrChip(
                    text = "用 AI ${formatUiProgress(suggestFloor)}$unit",
                    active = matchSuggest && !matchCurrent,
                    enabled = editable,
                    density = density
                ) {
                    viewModel.applyOkrSuggested(row.krId)
                    forceOkrRebuild = true
                    renderOkrSection(force = true)
                }
            )
            chips.addView(
                buildOkrChip(
                    text = "保持 ${formatUiProgress(row.currentValue)}$unit",
                    active = matchCurrent,
                    enabled = editable,
                    density = density,
                    startMargin = 8
                ) {
                    viewModel.applyOkrCurrent(row.krId)
                    forceOkrRebuild = true
                    renderOkrSection(force = true)
                }
            )
            chips.addView(TextView(this).apply {
                text = "自定义"
                textSize = 13f
                setPadding(
                    (12 * density).toInt(),
                    (8 * density).toInt(),
                    (4 * density).toInt(),
                    (8 * density).toInt()
                )
                setTextColor(
                    when {
                        !editable -> 0xFFA0A6B3.toInt()
                        matchCustom -> 0xFF1465EB.toInt()
                        else -> 0xFF686D79.toInt()
                    }
                )
                isEnabled = editable
                setOnClickListener {
                    // 自定义 = 聚焦滑条，让用户拖动；若已是建议/当前则轻微提示
                    seekBarRef?.requestFocus()
                    Toast.makeText(this@AiAssistantActivity, "拖动上方进度条即可自定义", Toast.LENGTH_SHORT)
                        .show()
                }
            })
            root.addView(chips)

            // 分隔线
            root.addView(View(this).apply {
                setBackgroundColor(0xFFE8EDF5.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (1 * density).toInt()
                ).apply { topMargin = (16 * density).toInt() }
            })

            root.addView(EditText(this).apply {
                setText(row.remark)
                hint = "备注（可选）"
                textSize = 13f
                setBackgroundResource(R.drawable.bg_ai_input)
                setPadding(
                    (14 * density).toInt(),
                    (12 * density).toInt(),
                    (14 * density).toInt(),
                    (12 * density).toInt()
                )
                isEnabled = editable
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (14 * density).toInt() }
                doAfterTextChanged { editableText ->
                    viewModel.updateOkrRemark(row.krId, editableText?.toString().orEmpty())
                }
            })
        } else if (!row.checked && !locked) {
            root.addView(TextView(this).apply {
                text = "勾选后可调整进度"
                setTextColor(0xFFA0A6B3.toInt())
                textSize = 12f
                setPadding(0, (12 * density).toInt(), 0, 0)
            })
        }

        // —— AI 依据：默认收起 ——
        if (reasonText.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = if (expanded) "收起 AI 依据 ▴" else "查看 AI 依据 ▾"
                setTextColor(0xFF1465EB.toInt())
                textSize = 13f
                setPadding(0, (14 * density).toInt(), 0, (4 * density).toInt())
                setOnClickListener {
                    if (expanded) expandedOkrReasons.remove(row.krId)
                    else expandedOkrReasons.add(row.krId)
                    forceOkrRebuild = true
                    renderOkrSection(force = true)
                }
            })
            if (expanded) {
                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_ai_okr_reason)
                    setPadding(
                        (14 * density).toInt(),
                        (12 * density).toInt(),
                        (14 * density).toInt(),
                        (12 * density).toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (6 * density).toInt() }
                }
                panel.addView(TextView(this).apply {
                    text = reasonText
                    setTextColor(0xFF3D4656.toInt())
                    textSize = 13f
                    setLineSpacing(3 * density, 1f)
                })
                panel.addView(TextView(this).apply {
                    text = "依据来源：本周日报 · 匹配 KR"
                    setTextColor(0xFFA0A6B3.toInt())
                    textSize = 11f
                    setPadding(0, (8 * density).toInt(), 0, 0)
                })
                root.addView(panel)
            }
        }
        return root
    }

    private fun buildOkrChip(
        text: String,
        active: Boolean,
        enabled: Boolean,
        density: Float,
        startMargin: Int = 0,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(
                (14 * density).toInt(),
                (8 * density).toInt(),
                (14 * density).toInt(),
                (8 * density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (startMargin > 0) marginStart = (startMargin * density).toInt()
            }
            isEnabled = enabled
            applyOkrChipStyle(this, active = active, enabled = enabled)
            setOnClickListener { onClick() }
        }
    }

    private fun applyOkrChipStyle(tv: TextView, active: Boolean, enabled: Boolean) {
        when {
            !enabled -> {
                tv.setBackgroundResource(R.drawable.bg_ai_okr_chip_idle)
                tv.setTextColor(0xFFA0A6B3.toInt())
                tv.alpha = 0.65f
            }
            active -> {
                tv.setBackgroundResource(R.drawable.bg_ai_okr_chip_active)
                tv.setTextColor(0xFFFFFFFF.toInt())
                tv.alpha = 1f
            }
            else -> {
                tv.setBackgroundResource(R.drawable.bg_ai_okr_chip_idle)
                tv.setTextColor(0xFF1465EB.toInt())
                tv.alpha = 1f
            }
        }
    }

    private fun nearProgress(a: Double, b: Double): Boolean =
        kotlin.math.abs(a - b) < 0.05

    private fun formatUiProgress(v: Double): String {
        val rounded = (v * 100).toLong() / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    private var lastDraftFieldStructure: List<String> = emptyList()

    private fun applyDraftFields(
        fields: List<AiDraftField>,
        options: Map<String, List<QianwenOption>>
    ) {
        val structure = fields.map {
            "${it.key}|${it.label}|${it.required}|${it.multiline}|${it.optionsKey}"
        }
        val locked = !viewModel.submitSuccess.value.isNullOrBlank()
        if (structure == lastDraftFieldStructure && binding.llDraftFields.childCount > 0) {
            syncDraftFieldValues(fields, locked)
            setDraftFieldsEditable(!locked && viewModel.skillSubmitting.value != true)
            return
        }
        lastDraftFieldStructure = structure
        renderDraftFields(fields, options, force = true)
    }

    private fun syncDraftFieldValues(fields: List<AiDraftField>, locked: Boolean) {
        val focusedKey = (currentFocus as? EditText)?.tag as? String
        val container = binding.llDraftFields
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val key = child.tag as? String ?: continue
            val field = fields.firstOrNull { it.key == key } ?: continue
            when (child) {
                is EditText -> {
                    if (key == focusedKey) continue
                    if (child.text?.toString() != field.value) {
                        renderingFields = true
                        child.setText(field.value)
                        child.setSelection(child.text?.length ?: 0)
                        renderingFields = false
                    }
                    val dateField = isDateField(key)
                    child.isEnabled = !locked
                    child.isFocusable = !locked && !dateField
                    child.isFocusableInTouchMode = !locked && !dateField
                    child.isCursorVisible = !locked && !dateField
                }
                is Spinner -> child.isEnabled = !locked
            }
        }
    }

    private fun isDateField(key: String): Boolean =
        key == "reportDate" || key == "weekStart" || key == "weekEnd" || key == "month"

    private fun renderDraftFields(
        fields: List<AiDraftField>,
        options: Map<String, List<QianwenOption>>,
        force: Boolean = false
    ) {
        if (renderingFields && !force) return
        renderingFields = true
        val container = binding.llDraftFields
        container.removeAllViews()
        val locked = !viewModel.submitSuccess.value.isNullOrBlank()
        fields.forEach { field ->
            val label = TextView(this).apply {
                text = field.label + if (field.required) " *" else ""
                textSize = 12.5f
                setTextColor(0xFF6B7280.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(2, 18, 0, 8)
            }
            container.addView(label)

            val optKey = field.optionsKey
            val opts = optKey?.let { options[it] }.orEmpty()
            if (!optKey.isNullOrBlank() && opts.isNotEmpty()) {
                val spinner = Spinner(this).apply { tag = field.key }
                val labels = mutableListOf("请选择")
                labels.addAll(opts.map { it.label?.ifBlank { it.value } ?: it.value.orEmpty() })
                spinner.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    labels
                )
                val selectedIdx = opts.indexOfFirst { it.value == field.value }.let {
                    if (it >= 0) it + 1 else 0
                }
                spinner.setSelection(selectedIdx, false)
                spinner.isEnabled = !locked
                spinner.background = ContextCompat.getDrawable(this, R.drawable.bg_ai_input)
                spinner.setPadding(28, 22, 28, 22)
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: android.view.View?,
                        position: Int,
                        id: Long
                    ) {
                        if (renderingFields) return
                        val value = if (position <= 0) "" else opts[position - 1].value.orEmpty()
                        viewModel.updatePayloadField(field.key, value)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                container.addView(
                    spinner,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            } else {
                val dateField = isDateField(field.key)
                val input = EditText(this).apply {
                    setText(field.value)
                    textSize = 15f
                    setTextColor(if (locked) 0xFF6D727E.toInt() else 0xFF111827.toInt())
                    setBackgroundResource(R.drawable.bg_ai_input)
                    setPadding(28, 24, 28, 24)
                    minHeight = if (field.multiline) 128 else 52
                    isSingleLine = !field.multiline
                    isEnabled = !locked
                    isFocusable = !locked && !dateField
                    isFocusableInTouchMode = !locked && !dateField
                    isCursorVisible = !locked && !dateField
                    isClickable = !locked
                    tag = field.key
                    hint = when (field.key) {
                        "month" -> "点击选择月份 YYYY-MM"
                        "reportDate", "weekStart", "weekEnd" -> "点击选择日期 YYYY-MM-DD"
                        else -> ""
                    }
                    if (dateField) {
                        setOnClickListener {
                            if (locked) return@setOnClickListener
                            if (field.key == "month") {
                                showMonthPicker(field.key, text?.toString().orEmpty())
                            } else {
                                showDatePicker(field.key, text?.toString().orEmpty())
                            }
                        }
                    } else {
                        doAfterTextChanged { editable ->
                            if (renderingFields) return@doAfterTextChanged
                            viewModel.updatePayloadField(field.key, editable?.toString().orEmpty())
                        }
                    }
                }
                container.addView(
                    input,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
        renderingFields = false
        setDraftFieldsEditable(!locked && viewModel.skillSubmitting.value != true)
    }

    private fun formatReportDateLabel(date: String): String {
        val parts = date.split("-")
        if (parts.size >= 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (y != null && m != null && d != null) return "${y}年${m}月${d}日"
        }
        return date
    }

    private fun formatMonthLabel(month: String): String {
        val parts = month.split("-")
        if (parts.size >= 2) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (y != null && m != null) return "${y}年${m}月"
        }
        return month
    }

    private fun refreshWeeklyPeriodLabel() {
        val start = viewModel.weeklyWeekStart.value.orEmpty().trim()
        val end = viewModel.weeklyWeekEnd.value.orEmpty().trim()
        binding.tvWeeklyPeriod.text = when {
            start.isBlank() || end.isBlank() -> "选择周期"
            else -> formatWeekRangeLabel(start, end)
        }
    }

    private fun formatWeekRangeLabel(start: String, end: String): String {
        val startLabel = formatReportDateLabel(start)
        val endParts = end.split("-")
        if (endParts.size >= 3 && start.split("-").firstOrNull() == endParts.firstOrNull()) {
            val m = endParts[1].toIntOrNull()
            val d = endParts[2].toIntOrNull()
            if (m != null && d != null) return "$startLabel – ${m}月${d}日"
        }
        return "$startLabel – ${formatReportDateLabel(end)}"
    }

    private fun showEntryDailyDatePicker() {
        val current = viewModel.dailyReportDate.value.orEmpty()
        val cal = Calendar.getInstance()
        val parts = current.split("-")
        if (parts.size >= 3) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
            parts[2].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
        }
        YuechengDatePicker.showFromCalendar(this, cal) { y, m, d ->
            val value = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            viewModel.setDailyReportDate(value)
        }
    }

    /** 选周内任意一天，ViewModel 自动对齐周一～周日 */
    private fun showEntryWeeklyDatePicker() {
        val current = viewModel.weeklyWeekStart.value.orEmpty()
        val cal = Calendar.getInstance()
        val parts = current.split("-")
        if (parts.size >= 3) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
            parts[2].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
        }
        YuechengDatePicker.showFromCalendar(this, cal) { y, m, d ->
            val value = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            viewModel.setWeeklyPeriodFromDay(value)
        }
    }

    private fun showEntryMonthlyMonthPicker() {
        val current = viewModel.monthlyMonth.value.orEmpty()
        val cal = Calendar.getInstance()
        val parts = current.split("-")
        if (parts.size >= 2) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
        }
        YuechengDatePicker.showFromCalendar(this, cal, hideDay = true) { y, m, _ ->
            viewModel.setMonthlyMonth(String.format(Locale.US, "%04d-%02d", y, m + 1))
        }
    }

    private fun showDatePicker(fieldKey: String, current: String) {
        val cal = Calendar.getInstance()
        val parts = current.split("-")
        if (parts.size >= 3) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
            parts[2].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
        }
        YuechengDatePicker.showFromCalendar(this, cal) { y, m, d ->
            val value = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            viewModel.updatePayloadField(fieldKey, value, rebuildUi = true)
        }
    }

    private fun showMonthPicker(fieldKey: String, current: String) {
        val cal = Calendar.getInstance()
        val parts = current.split("-")
        if (parts.size >= 2) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
        }
        YuechengDatePicker.showFromCalendar(this, cal, hideDay = true) { y, m, _ ->
            val value = String.format(Locale.US, "%04d-%02d", y, m + 1)
            viewModel.updatePayloadField(fieldKey, value, rebuildUi = true)
        }
    }

    private var lastRevealedDraftId: String? = null
    private var lastOkrStatus: String? = null

    /** 草稿生成后滚到草稿区一次 */
    private fun revealDraftCardIfNeeded(draftId: String?) {
        if (draftId.isNullOrBlank() || draftId == lastRevealedDraftId) return
        lastRevealedDraftId = draftId
        hideSoftKeyboard()
        scrollSkillTo(binding.draftCard)
    }

    private fun revealOkrSectionOnce() {
        hideSoftKeyboard()
        scrollSkillTo(binding.okrSection)
    }

    private fun scrollSkillTo(target: View) {
        binding.skillPanel.post {
            if (!target.isVisible) return@post
            val content = binding.skillPanel.getChildAt(0) ?: return@post
            var offset = 0
            var v: View? = target
            while (v != null && v !== content && v !== binding.skillPanel) {
                offset += v.top
                v = v.parent as? View
            }
            val pad = (12 * resources.displayMetrics.density).toInt()
            binding.skillPanel.smoothScrollTo(0, (offset - pad).coerceAtLeast(0))
        }
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val token = currentFocus?.windowToken ?: binding.root.windowToken
        imm.hideSoftInputFromWindow(token, 0)
        currentFocus?.clearFocus()
    }

    override fun onDestroy() {
        stopVoice()
        super.onDestroy()
    }

    companion object {
        private const val MAX_KNOWLEDGE_BYTES = 10 * 1024 * 1024L

        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_REPORT_SUB = "report_sub"
        const val TAB_CHAT = "chat"
        const val TAB_DAILY = "daily"
        const val TAB_KNOWLEDGE = "knowledge"
    }
}
