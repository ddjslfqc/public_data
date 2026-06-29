package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrPeriodOption
import com.fuusy.hiddendanger.databinding.ActivityMyGoalsBinding
import com.fuusy.hiddendanger.ui.adapter.GoalObjectiveSectionAdapter
import com.fuusy.hiddendanger.viewmodel.MyGoalsViewModel

@Route(path = "/hiddendanger/MyGoalsActivity")
class MyGoalsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyGoalsBinding
    private val viewModel: MyGoalsViewModel by viewModels()
    private val objectiveAdapter = GoalObjectiveSectionAdapter()
    private var selectedPeriod: String? = null
    private var cachedPeriods: List<OkrPeriodOption> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyGoalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#0A3D8F")
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddGoal.setOnClickListener {
            EditGoalActivity.start(viewModel.activePeriodValue())
        }
        binding.cardKrApproval.setOnClickListener {
            ARouter.getInstance().build("/hiddendanger/KrApprovalActivity").navigation()
        }

        binding.rvObjectives.layoutManager = LinearLayoutManager(this)
        binding.rvObjectives.adapter = objectiveAdapter

        binding.section360.isVisible = false

        observeViewModel()
        viewModel.load(null)
    }

    override fun onResume() {
        super.onResume()
        viewModel.load(selectedPeriod)
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { loading ->
            binding.progressLoading.isVisible = loading == true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.pendingCount.observe(this) { count ->
            binding.tvPendingBadge.text = if (count > 99) "99+" else count.toString()
            binding.tvPendingBadge.isVisible = count > 0
        }
        viewModel.myGoal.observe(this) { data ->
            if (data == null) return@observe
            cachedPeriods = data.periods.orEmpty()
            renderPeriodTabs(cachedPeriods)
            val objectives = data.objectives.orEmpty()
            val highlighted = data.currentObjective ?: objectives.firstOrNull()
            bindHero(highlighted, objectives.size)
            bindObjectives(objectives)
        }
    }

    private fun bindHero(highlighted: OkrObjective?, objectiveCount: Int) {
        if (highlighted == null) {
            binding.tvSummary.text = "暂无目标"
            binding.tvPeriodRange.text = "点击右上角 + 添加目标"
            binding.tvProgressDetail.text = ""
            binding.tvProgressRing.text = "0"
            return
        }
        binding.tvSummary.text = if (objectiveCount > 1) {
            "${highlighted.title} 等 ${objectiveCount} 个目标"
        } else {
            highlighted.title
        }
        binding.tvPeriodRange.text = OkrPeriodHelper.formatPeriodRange(highlighted)
        binding.tvProgressDetail.text = highlighted.progressText.orEmpty()
        binding.tvProgressRing.text = highlighted.progress.toString()
    }

    private fun bindObjectives(objectives: List<OkrObjective>) {
        val empty = objectives.isEmpty()
        binding.tvEmptyObjectives.isVisible = empty
        binding.rvObjectives.isVisible = !empty
        objectiveAdapter.submitList(objectives)
    }

    private fun renderPeriodTabs(periods: List<OkrPeriodOption>) {
        val container = binding.llPeriodTabs
        container.removeAllViews()
        val tabs = if (periods.isEmpty()) {
            listOf(
                OkrPeriodOption("Q2 2026", "quarter-2", true),
                OkrPeriodOption("Q3 2026", "quarter-3", false),
                OkrPeriodOption("Q4 2026", "quarter-4", false),
                OkrPeriodOption("年度目标", "year", false)
            )
        } else {
            periods.forEach { if (it.active) selectedPeriod = it.value }
            if (selectedPeriod == null) selectedPeriod = periods.firstOrNull()?.value
            periods
        }
        tabs.forEachIndexed { index, period -> addPeriodTab(container, period, index > 0) }
    }

    private fun addPeriodTab(container: LinearLayout, period: OkrPeriodOption, addMargin: Boolean) {
        val tab = TextView(this).apply {
            text = period.label
            setPadding(dp(14), 0, dp(14), 0)
            gravity = android.view.Gravity.CENTER
            textSize = 12f
            includeFontPadding = false
            val selected = period.value == selectedPeriod || period.active
            setBackgroundResource(
                if (selected) R.drawable.bg_goal_period_tab_selected
                else R.drawable.bg_goal_period_tab_normal
            )
            setTextColor(
                if (selected) Color.parseColor("#1365EC")
                else Color.parseColor("#B3FFFFFF")
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
            ).apply {
                if (addMargin) marginStart = dp(8)
            }
            setOnClickListener {
                if (selectedPeriod == period.value) return@setOnClickListener
                selectedPeriod = period.value
                renderPeriodTabs(cachedPeriods)
                viewModel.load(period.value)
            }
        }
        container.addView(tab)
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeader) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top + dp(4))
            insets
        }
        ViewCompat.requestApplyInsets(binding.heroHeader)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
