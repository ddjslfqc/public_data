package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.hiddendanger.data.MyReportType
import com.fuusy.hiddendanger.data.ReportArchiveItem
import com.fuusy.hiddendanger.data.ReportOkrUpdateItem
import com.fuusy.hiddendanger.databinding.ActivityMyReportDetailBinding
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.repository.DailyReportRepository
import com.fuusy.hiddendanger.repository.WeeklyReportOkrCache
import com.fuusy.hiddendanger.ui.widget.YuechengDatePicker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MyReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyReportDetailBinding
    private val dailyRepo = DailyReportRepository()
    private var currentItem: ReportArchiveItem? = null
    private var contentChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = Color.WHITE
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            binding.toolbar.updatePadding(
                top = status.top,
                left = cutout.left,
                right = cutout.right
            )
            insets
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        binding.btnBack.setOnClickListener { finishWithResultIfNeeded() }
        binding.btnGoMyGoals.setOnClickListener { openMyGoals() }
        binding.btnGoMyGoalsFooter.setOnClickListener { openMyGoals() }
        binding.btnEditDaily.setOnClickListener { openEditDialog() }
        binding.btnDeleteDaily.setOnClickListener { openDeleteDialog() }
        onBackPressedDispatcher.addCallback(this) {
            finishWithResultIfNeeded()
        }
        bind(fromIntent(intent))
    }

    private fun finishWithResultIfNeeded() {
        if (contentChanged) {
            setResult(RESULT_OK)
        }
        finish()
    }

    private fun openMyGoals() {
        ARouter.getInstance()
            .build("/hiddendanger/MyGoalsActivity")
            .navigation(this)
    }

    private fun bind(item: ReportArchiveItem) {
        currentItem = item
        val type = MyReportType.fromApi(item.type)
        binding.tvTitle.text = "${type?.label ?: "报告"}详情"

        val cache = WeeklyReportOkrCache.load(this, item.id)
        val weekStart = item.weekStart?.takeIf { it.isNotBlank() } ?: cache?.weekStart
        val weekEnd = item.weekEnd?.takeIf { it.isNotBlank() } ?: cache?.weekEnd
        val okrUpdates = item.okrUpdates.orEmpty().ifEmpty { cache?.okrUpdates.orEmpty() }

        binding.tvPeriod.text = formatPeriodTitle(item, weekStart, weekEnd)
        binding.tvMeta.text = buildMetaLine(type, item)

        binding.tvSummaryLabel.text = when (type) {
            MyReportType.WEEKLY -> "本周工作总结"
            MyReportType.MONTHLY -> "本月工作成果 / 突破"
            else -> "工作总结"
        }
        binding.tvSummary.text = item.summary.orEmpty().ifBlank { "（无内容）" }

        val showIssuesCard = type == MyReportType.WEEKLY || type == MyReportType.MONTHLY
        binding.cardIssues.isVisible = showIssuesCard
        if (showIssuesCard) {
            val issues = item.issues.orEmpty().trim()
            binding.tvIssues.text = issues.ifBlank { "暂无" }
            binding.tvIssues.setTextColor(
                if (issues.isBlank()) 0xFFA0A6B3.toInt() else 0xFF1A1A1A.toInt()
            )
        }

        val showOkr = type == MyReportType.WEEKLY && okrUpdates.isNotEmpty()
        binding.badgeOkrSynced.isVisible = showOkr
        binding.cardOkr.isVisible = showOkr
        binding.btnGoMyGoalsFooter.isVisible = type == MyReportType.WEEKLY && !showOkr

        binding.rowDailyActions.isVisible = type == MyReportType.DAILY
        if (showOkr) {
            binding.tvOkrCount.text = "${okrUpdates.size} 项已更新"
            renderOkrUpdates(okrUpdates)
        }
    }

    private fun openEditDialog() {
        val item = currentItem ?: return
        if (MyReportType.fromApi(item.type) != MyReportType.DAILY) return

        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (16 * density).toInt(),
                (20 * density).toInt(),
                (8 * density).toInt()
            )
        }
        val dateLabel = TextView(this).apply {
            text = "日报日期"
            setTextColor(0xFF374151.toInt())
            textSize = 13f
        }
        var pickedDate = resolveReportDate(item)
        val dateValue = TextView(this).apply {
            text = pickedDate
            setTextColor(0xFF1465EB.toInt())
            textSize = 15f
            setPadding(0, (6 * density).toInt(), 0, (12 * density).toInt())
            setOnClickListener {
                showDatePicker(pickedDate) { picked ->
                    pickedDate = picked
                    text = picked
                }
            }
        }
        val contentLabel = TextView(this).apply {
            text = "日报正文"
            setTextColor(0xFF374151.toInt())
            textSize = 13f
        }
        val contentInput = EditText(this).apply {
            setText(item.summary.orEmpty())
            minLines = 6
            gravity = android.view.Gravity.TOP
            setHint("请输入日报正文")
            setPadding(
                (12 * density).toInt(),
                (10 * density).toInt(),
                (12 * density).toInt(),
                (10 * density).toInt()
            )
        }
        root.addView(dateLabel)
        root.addView(dateValue)
        root.addView(contentLabel)
        root.addView(contentInput)

        AlertDialog.Builder(this)
            .setTitle("编辑日报")
            .setView(root)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val reportDate = pickedDate.trim()
                val content = contentInput.text.toString().trim()
                if (reportDate.isBlank() || content.isBlank()) {
                    Toast.makeText(this, "请填写日期和日报正文", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveDailyEdit(item.id, reportDate, content)
            }
            .show()
    }

    private fun openDeleteDialog() {
        val item = currentItem ?: return
        if (MyReportType.fromApi(item.type) != MyReportType.DAILY) return
        AlertDialog.Builder(this)
            .setTitle("删除日报")
            .setMessage("确定删除这条日报吗？删除后无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认删除") { _, _ ->
                deleteDaily(item.id)
            }
            .show()
    }

    private fun saveDailyEdit(id: Long, reportDate: String, content: String) {
        binding.btnEditDaily.isEnabled = false
        lifecycleScope.launch {
            dailyRepo.updateDailyReport(id, reportDate, content)
                .onSuccess {
                    contentChanged = true
                    Toast.makeText(this@MyReportDetailActivity, "日报已修改", Toast.LENGTH_SHORT).show()
                    val updated = currentItem?.copy(
                        reportDate = reportDate,
                        period = formatPeriodFromReportDate(reportDate),
                        summary = content
                    )
                    if (updated != null) {
                        currentItem = updated
                        bind(updated)
                    }
                }
                .onFailure { e ->
                    Toast.makeText(
                        this@MyReportDetailActivity,
                        e.message ?: "日报修改失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            binding.btnEditDaily.isEnabled = true
        }
    }

    private fun deleteDaily(id: Long) {
        binding.btnDeleteDaily.isEnabled = false
        lifecycleScope.launch {
            dailyRepo.deleteDailyReport(id)
                .onSuccess {
                    Toast.makeText(this@MyReportDetailActivity, "日报已删除", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .onFailure { e ->
                    Toast.makeText(
                        this@MyReportDetailActivity,
                        e.message ?: "日报删除失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.btnDeleteDaily.isEnabled = true
                }
        }
    }

    private fun showDatePicker(current: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val parts = current.split("-")
        if (parts.size >= 3) {
            cal.set(parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR), (parts[1].toIntOrNull() ?: 1) - 1, parts[2].toIntOrNull() ?: 1)
        }
        YuechengDatePicker.showFromCalendar(this, cal) { y, m, d ->
            onPicked(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d))
        }
    }

    private fun resolveReportDate(item: ReportArchiveItem): String {
        item.reportDate?.takeIf { it.isNotBlank() }?.let { return it }
        val period = item.period.orEmpty()
        val match = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})日").find(period)
        if (match != null) {
            val (y, mo, d) = match.destructured
            return String.format(Locale.getDefault(), "%04d-%02d-%02d", y.toInt(), mo.toInt(), d.toInt())
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    private fun formatPeriodFromReportDate(date: String): String {
        val parts = date.split("-")
        if (parts.size >= 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (y != null && m != null && d != null) return "${y}年${m}月${d}日"
        }
        return date
    }

    private fun renderOkrUpdates(updates: List<ReportOkrUpdateItem>) {
        binding.llOkrUpdates.removeAllViews()
        val density = resources.displayMetrics.density
        updates.forEachIndexed { index, row ->
            if (index > 0) {
                binding.llOkrUpdates.addView(View(this).apply {
                    setBackgroundColor(0xFFEEF1F8.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply { topMargin = (14 * density).toInt() }
                })
            }
            binding.llOkrUpdates.addView(buildOkrUpdateRow(row, density))
        }
    }

    private fun buildOkrUpdateRow(row: ReportOkrUpdateItem, density: Float): LinearLayout {
        val unit = row.unit?.ifBlank { "%" } ?: "%"
        val from = row.fromValue
        val to = row.toValue
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (binding.llOkrUpdates.childCount > 0) {
                    topMargin = (14 * density).toInt()
                }
            }
            addView(TextView(this@MyReportDetailActivity).apply {
                text = "O · ${row.objectiveTitle.orEmpty().ifBlank { "目标" }}"
                setTextColor(0xFF898FA0.toInt())
                textSize = 12f
                setLineSpacing(2 * density, 1f)
            })
            addView(TextView(this@MyReportDetailActivity).apply {
                text = "KR · ${row.krTitle.orEmpty().ifBlank { "关键结果" }}"
                setTextColor(0xFF111827.toInt())
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setLineSpacing(2 * density, 1f)
                setPadding(0, (4 * density).toInt(), 0, 0)
            })
            addView(LinearLayout(this@MyReportDetailActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (10 * density).toInt(), 0, 0)
                if (from != null) {
                    addView(TextView(this@MyReportDetailActivity).apply {
                        text = "${formatProgress(from)}$unit"
                        setTextColor(0xFF898FA0.toInt())
                        textSize = 14f
                    })
                    addView(TextView(this@MyReportDetailActivity).apply {
                        text = "  →  "
                        setTextColor(0xFFC5CAD6.toInt())
                        textSize = 14f
                    })
                }
                addView(TextView(this@MyReportDetailActivity).apply {
                    text = "${formatProgress(to)}$unit"
                    setTextColor(0xFF1465EB.toInt())
                    textSize = 18f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MyReportDetailActivity).apply {
                    text = "已更新"
                    setTextColor(0xFF0F766E.toInt())
                    textSize = 11f
                    setBackgroundResource(com.fuusy.hiddendanger.R.drawable.bg_report_okr_updated)
                    setPadding(
                        (8 * density).toInt(),
                        (3 * density).toInt(),
                        (8 * density).toInt(),
                        (3 * density).toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = (10 * density).toInt() }
                })
            })
            val remark = row.remark.orEmpty().trim()
            if (remark.isNotBlank()) {
                addView(TextView(this@MyReportDetailActivity).apply {
                    text = remark
                    setTextColor(0xFF898FA0.toInt())
                    textSize = 12f
                    setPadding(0, (6 * density).toInt(), 0, 0)
                })
            }
        }
    }

    private fun formatPeriodTitle(
        item: ReportArchiveItem,
        weekStart: String?,
        weekEnd: String?
    ): String {
        val range = formatWeekRange(weekStart, weekEnd)
        if (range.isNotBlank()) return range
        return item.period.orEmpty().ifBlank { "—" }
    }

    private fun formatWeekRange(weekStart: String?, weekEnd: String?): String {
        if (weekStart.isNullOrBlank() || weekEnd.isNullOrBlank()) return ""
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val start = runCatching { inFmt.parse(weekStart) }.getOrNull() ?: return ""
        val end = runCatching { inFmt.parse(weekEnd) }.getOrNull() ?: return ""
        val yFmt = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
        val mdFmt = SimpleDateFormat("M月d日", Locale.getDefault())
        val startCal = Calendar.getInstance().apply { time = start }
        val endCal = Calendar.getInstance().apply { time = end }
        return if (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)) {
            "${yFmt.format(start)} – ${mdFmt.format(end)}"
        } else {
            "${yFmt.format(start)} – ${yFmt.format(end)}"
        }
    }

    private fun buildMetaLine(type: MyReportType?, item: ReportArchiveItem): String {
        val parts = mutableListOf<String>()
        type?.label?.let { parts.add(it) }
        item.userName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        item.submittedAt?.takeIf { it.isNotBlank() }?.let { parts.add("提交于 $it") }
        return parts.joinToString(" · ").ifBlank { "—" }
    }

    private fun formatProgress(v: Double?): String {
        if (v == null) return "—"
        val rounded = (v * 100).toLong() / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_USER_NAME = "user_name"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_REPORT_DATE = "report_date"
        private const val EXTRA_PERIOD = "period"
        private const val EXTRA_WEEK_START = "week_start"
        private const val EXTRA_WEEK_END = "week_end"
        private const val EXTRA_SUMMARY = "summary"
        private const val EXTRA_ISSUES = "issues"
        private const val EXTRA_SUBMITTED_AT = "submitted_at"
        private const val EXTRA_OKR_UPDATES_JSON = "okr_updates_json"
        private val gson = com.google.gson.Gson()

        fun start(context: Context, item: ReportArchiveItem) {
            // Intent 带上 okrUpdates：接口有则用接口；否则详情里再兜本地缓存
            val cache = WeeklyReportOkrCache.load(context, item.id)
            val okr = item.okrUpdates.orEmpty().ifEmpty { cache?.okrUpdates.orEmpty() }
            val weekStart = item.weekStart?.takeIf { it.isNotBlank() } ?: cache?.weekStart
            val weekEnd = item.weekEnd?.takeIf { it.isNotBlank() } ?: cache?.weekEnd
            context.startActivity(
                Intent(context, MyReportDetailActivity::class.java).apply {
                    putExtra(EXTRA_ID, item.id)
                    putExtra(EXTRA_USER_ID, item.userId)
                    putExtra(EXTRA_USER_NAME, item.userName)
                    putExtra(EXTRA_TYPE, item.type)
                    putExtra(EXTRA_REPORT_DATE, item.reportDate)
                    putExtra(EXTRA_PERIOD, item.period)
                    putExtra(EXTRA_WEEK_START, weekStart)
                    putExtra(EXTRA_WEEK_END, weekEnd)
                    putExtra(EXTRA_SUMMARY, item.summary)
                    putExtra(EXTRA_ISSUES, item.issues)
                    putExtra(EXTRA_SUBMITTED_AT, item.submittedAt)
                    if (okr.isNotEmpty()) {
                        putExtra(EXTRA_OKR_UPDATES_JSON, gson.toJson(okr))
                    }
                }
            )
        }

        private fun fromIntent(intent: Intent): ReportArchiveItem {
            val okrJson = intent.getStringExtra(EXTRA_OKR_UPDATES_JSON)
            val okr = if (!okrJson.isNullOrBlank()) {
                runCatching {
                    gson.fromJson(
                        okrJson,
                        object : com.google.gson.reflect.TypeToken<List<ReportOkrUpdateItem>>() {}.type
                    ) as? List<ReportOkrUpdateItem>
                }.getOrNull()
            } else null
            return ReportArchiveItem(
                id = intent.getLongExtra(EXTRA_ID, 0L),
                userId = intent.getLongExtra(EXTRA_USER_ID, 0L),
                userName = intent.getStringExtra(EXTRA_USER_NAME),
                type = intent.getStringExtra(EXTRA_TYPE),
                reportDate = intent.getStringExtra(EXTRA_REPORT_DATE),
                period = intent.getStringExtra(EXTRA_PERIOD),
                weekStart = intent.getStringExtra(EXTRA_WEEK_START),
                weekEnd = intent.getStringExtra(EXTRA_WEEK_END),
                summary = intent.getStringExtra(EXTRA_SUMMARY),
                issues = intent.getStringExtra(EXTRA_ISSUES),
                submittedAt = intent.getStringExtra(EXTRA_SUBMITTED_AT),
                okrUpdates = okr
            )
        }
    }
}
