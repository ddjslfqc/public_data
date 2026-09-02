package com.fuusy.hiddendanger.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.MyReportType
import com.fuusy.hiddendanger.databinding.ActivityMyReportsBinding
import com.fuusy.hiddendanger.databinding.SheetReportMonthPickerBinding
import com.fuusy.hiddendanger.ui.adapter.MyReportAdapter
import com.fuusy.hiddendanger.ui.widget.SegmentTabUi
import com.fuusy.hiddendanger.viewmodel.MyReportsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar

@Route(path = "/hiddendanger/MyReportsActivity")
class MyReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyReportsBinding
    private val viewModel: MyReportsViewModel by viewModels()
    private val adapter = MyReportAdapter { MyReportDetailActivity.start(this, it) }
    private var resumeRefreshReady = false
    private var yearBase = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyReportsBinding.inflate(layoutInflater)
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

        binding.rvReports.layoutManager = LinearLayoutManager(this)
        binding.rvReports.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.tabDaily.setOnClickListener { viewModel.setType(MyReportType.DAILY) }
        binding.tabWeekly.setOnClickListener { viewModel.setType(MyReportType.WEEKLY) }
        binding.tabMonthly.setOnClickListener { viewModel.setType(MyReportType.MONTHLY) }
        binding.fabWrite.setOnClickListener { openWritePage() }
        binding.btnYear.setOnClickListener { showFilterSheet(focusYear = true) }
        binding.btnMonth.setOnClickListener { showFilterSheet(focusYear = false) }
        binding.swipeRefresh.setColorSchemeColors(0xFF1465EB.toInt())
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        observe()
        renderFilterChips()
        val openType = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        MyReportType.fromApi(openType)?.let { viewModel.setType(it) }
            ?: viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        if (resumeRefreshReady) {
            viewModel.load()
        } else {
            resumeRefreshReady = true
        }
    }

    private fun showFilterSheet(focusYear: Boolean) {
        yearBase = Calendar.getInstance().get(Calendar.YEAR)
        val dialog = BottomSheetDialog(this)
        val sheet = SheetReportMonthPickerBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        sheet.tvSheetTitle.text = if (focusYear) "选择年份" else "选择月份"
        sheet.btnSheetClose.setOnClickListener { dialog.dismiss() }

        fun bindYears(selectedYear: Int) {
            sheet.llYearRow.removeAllViews()
            val padH = dp(14)
            val padV = dp(7)
            val gap = dp(8)
            (0 until 5).forEach { i ->
                val y = yearBase - i
                val tv = TextView(this).apply {
                    text = "${y}年"
                    textSize = 13f
                    setPadding(padH, padV, padH, padV)
                    setBackgroundResource(R.drawable.bg_report_year_pill)
                    isSelected = y == selectedYear
                    setTextColor(if (isSelected) 0xFF1465EB.toInt() else 0xFF686D79.toInt())
                    setOnClickListener {
                        viewModel.setYear(y)
                        bindYears(y)
                        if (focusYear) dialog.dismiss()
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (i > 0) marginStart = gap }
                sheet.llYearRow.addView(tv, lp)
            }
        }

        fun bindMonths(selectedMonth: Int?) {
            sheet.gridMonths.removeAllViews()
            if (focusYear) return
            val colGap = dp(10)
            val rowGap = dp(10)
            val cellH = dp(44)
            // 全部月份：占满一行
            addMonthCell(
                sheet.gridMonths,
                label = "全部月份",
                selected = selectedMonth == null,
                colSpan = 4,
                height = cellH,
                colGap = colGap,
                rowGap = rowGap,
                firstInRow = true
            ) {
                viewModel.setMonth(null)
                dialog.dismiss()
            }
            (1..12).forEach { m ->
                val col = (m - 1) % 4
                addMonthCell(
                    sheet.gridMonths,
                    label = "${m}月",
                    selected = selectedMonth == m,
                    colSpan = 1,
                    height = cellH,
                    colGap = colGap,
                    rowGap = rowGap,
                    firstInRow = col == 0
                ) {
                    viewModel.setMonth(m)
                    dialog.dismiss()
                }
            }
        }

        bindYears(viewModel.year.value ?: yearBase)
        bindMonths(viewModel.month.value)
        dialog.show()
    }

    private fun addMonthCell(
        grid: GridLayout,
        label: String,
        selected: Boolean,
        colSpan: Int,
        height: Int,
        colGap: Int,
        rowGap: Int,
        firstInRow: Boolean,
        onClick: () -> Unit
    ) {
        val tv = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            setBackgroundResource(R.drawable.bg_report_month_cell)
            isSelected = selected
            setTextColor(if (selected) Color.WHITE else 0xFF111827.toInt())
            setOnClickListener { onClick() }
        }
        val lp = GridLayout.LayoutParams().apply {
            width = 0
            this.height = height
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, colSpan, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
            setMargins(
                if (firstInRow) 0 else colGap / 2,
                rowGap / 2,
                colGap / 2,
                rowGap / 2
            )
        }
        grid.addView(tv, lp)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun renderFilterChips() {
        val year = viewModel.year.value ?: yearBase
        val month = viewModel.month.value
        binding.btnYear.text = "${year}年 ▾"
        binding.btnMonth.text = if (month == null) "全部月份 ▾" else "${month}月 ▾"
    }

    private fun observe() {
        viewModel.type.observe(this) { type ->
            val index = when (type) {
                MyReportType.WEEKLY -> 1
                MyReportType.MONTHLY -> 2
                else -> 0
            }
            SegmentTabUi.applyUnderline(
                listOf(binding.tabDaily, binding.tabWeekly, binding.tabMonthly),
                index
            )
            binding.fabWrite.text = when (type) {
                MyReportType.WEEKLY -> "写周报"
                MyReportType.MONTHLY -> "写月报"
                else -> "写日报"
            }
            binding.tvEmptyTitle.text = when (type) {
                MyReportType.WEEKLY -> "暂无周报"
                MyReportType.MONTHLY -> "暂无月报"
                else -> "暂无日报"
            }
            binding.tvEmptyHint.text = "点右下角按钮即可开始写"
        }
        viewModel.year.observe(this) { renderFilterChips() }
        viewModel.month.observe(this) { renderFilterChips() }
        viewModel.loading.observe(this) { loading ->
            binding.swipeRefresh.isRefreshing = loading == true
            refreshEmptyVisibility()
        }
        viewModel.items.observe(this) { list ->
            adapter.submitList(list?.toList().orEmpty()) {
                refreshEmptyVisibility()
            }
            refreshEmptyVisibility()
        }
        viewModel.filterCount.observe(this) { count ->
            val month = viewModel.month.value
            val label = if (month == null) "全部" else "${month}月"
            binding.tvFilterCount.text = "共 ${count ?: 0} 条·$label"
        }
        viewModel.error.observe(this) { err ->
            val msg = err?.takeIf { it.isNotBlank() }
            binding.tvError.isVisible = msg != null && viewModel.items.value.isNullOrEmpty()
            binding.tvError.text = msg?.let { "$it\n下拉可重试" }.orEmpty()
            refreshEmptyVisibility()
        }
    }

    private fun refreshEmptyVisibility() {
        val loading = viewModel.loading.value == true
        val empty = viewModel.items.value.isNullOrEmpty()
        binding.emptyState.isVisible = empty
        binding.rvReports.isVisible = !empty
        if (!empty) return
        val type = viewModel.type.value ?: MyReportType.DAILY
        if (loading) {
            binding.tvEmptyTitle.text = "加载中…"
            binding.tvEmptyHint.text = "正在拉取归档记录"
        } else {
            binding.tvEmptyTitle.text = when (type) {
                MyReportType.WEEKLY -> "暂无周报"
                MyReportType.MONTHLY -> "暂无月报"
                else -> "暂无日报"
            }
            binding.tvEmptyHint.text = "点右下角按钮即可开始写"
        }
    }

    private fun openWritePage() {
        val type = viewModel.type.value ?: MyReportType.DAILY
        startActivity(
            Intent(this, AiAssistantActivity::class.java).apply {
                putExtra(AiAssistantActivity.EXTRA_OPEN_TAB, AiAssistantActivity.TAB_DAILY)
                putExtra(AiAssistantActivity.EXTRA_REPORT_SUB, type.apiValue)
            }
        )
    }

    companion object {
        const val EXTRA_TYPE = "report_type"
    }
}
