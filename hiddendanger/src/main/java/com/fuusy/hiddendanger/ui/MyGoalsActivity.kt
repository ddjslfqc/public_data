package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.fuusy.hiddendanger.ui.model.KrNavHelper
import com.fuusy.hiddendanger.viewmodel.MyGoalsViewModel

@Route(path = "/hiddendanger/MyGoalsActivity")
class MyGoalsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyGoalsBinding
    private val viewModel: MyGoalsViewModel by viewModels()
    private val objectiveAdapter = GoalObjectiveSectionAdapter { item ->
        krDetailLauncher.launch(
            Intent(this, KrDetailActivity::class.java).apply {
                KrNavHelper.putExtra(this, item)
            }
        )
    }
    private var selectedPeriod: String? = null
    private var cachedPeriods: List<OkrPeriodOption> = emptyList()
    private var peerEvalPending = 0
    private var peerEvalReceived = 0
    private var resumedOnce = false

    private val krDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.loadGoalsOnly(selectedPeriod)
            viewModel.refreshBadgesWithoutPeerEval()
        }
    }

    private val editGoalLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.loadGoalsOnly(selectedPeriod)
            viewModel.refreshBadgesWithoutPeerEval()
        }
    }

    private val peerEvalLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBadgesAfterPeerEval()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyGoalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#0A3D8F")
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddGoal.setOnClickListener {
            editGoalLauncher.launch(
                Intent(this, EditGoalActivity::class.java).apply {
                    putExtra(
                        EditGoalActivity.EXTRA_PERIOD_VALUE,
                        viewModel.activePeriodValue() ?: OkrPeriodHelper.currentQuarterValue()
                    )
                }
            )
        }
        binding.cardKrApproval.setOnClickListener {
            ARouter.getInstance().build("/hiddendanger/KrApprovalActivity").navigation()
        }
        binding.cardKrComments.setOnClickListener {
            OkrCommentListActivity.start(this)
        }
        binding.cardColleagueEval.setOnClickListener {
            openPeerEval(
                showEvalTab = peerEvalPending > 0,
                showReceivedTab = peerEvalPending == 0 && peerEvalReceived > 0
            )
        }

        binding.rvObjectives.layoutManager = LinearLayoutManager(this)
        binding.rvObjectives.adapter = objectiveAdapter

        observeViewModel()
        selectedPeriod = OkrPeriodHelper.currentQuarterValue()
        viewModel.load(selectedPeriod)
        updatePeriodActions()
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) {
            viewModel.refreshBadgesWithoutPeerEval()
        } else {
            resumedOnce = true
        }
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
        viewModel.isDeptLeader.observe(this) { isLeader ->
            binding.cardKrApproval.isVisible = isLeader == true
            // 非负责人时评论入口占满整行
            val commentsLp = binding.cardKrComments.layoutParams as? LinearLayout.LayoutParams
            commentsLp?.marginStart = if (isLeader == true) dp(4) else 0
            binding.cardKrComments.layoutParams = commentsLp
        }
        viewModel.receivedCommentCount.observe(this) { count ->
            binding.tvCommentBadge.text = if (count > 99) "99+" else count.toString()
            binding.tvCommentBadge.isVisible = count > 0
        }
        viewModel.peerEvalPendingCount.observe(this) { pending ->
            peerEvalPending = pending
            bindColleagueEvalSummary(
                pending,
                viewModel.peerEvalCompletedCount.value ?: 0,
                viewModel.peerEvalReceivedCount.value ?: 0,
                viewModel.peerEvalReceivedScore.value
            )
        }
        viewModel.peerEvalCompletedCount.observe(this) { completed ->
            bindColleagueEvalSummary(
                viewModel.peerEvalPendingCount.value ?: 0,
                completed,
                viewModel.peerEvalReceivedCount.value ?: 0,
                viewModel.peerEvalReceivedScore.value
            )
        }
        viewModel.peerEvalReceivedCount.observe(this) { received ->
            peerEvalReceived = received
            bindColleagueEvalSummary(
                viewModel.peerEvalPendingCount.value ?: 0,
                viewModel.peerEvalCompletedCount.value ?: 0,
                received,
                viewModel.peerEvalReceivedScore.value
            )
        }
        viewModel.peerEvalReceivedScore.observe(this) { score ->
            bindColleagueEvalSummary(
                viewModel.peerEvalPendingCount.value ?: 0,
                viewModel.peerEvalCompletedCount.value ?: 0,
                viewModel.peerEvalReceivedCount.value ?: 0,
                score
            )
        }
        viewModel.myGoal.observe(this) { data ->
            if (data == null) return@observe
            cachedPeriods = data.periods.orEmpty()
            renderPeriodTabs(cachedPeriods)
            updatePeriodActions()
            val objectives = data.objectives.orEmpty()
            val highlighted = data.currentObjective ?: objectives.firstOrNull()
            bindHero(highlighted, objectives.size)
            bindObjectives(objectives)
        }
    }

    private fun bindColleagueEvalSummary(
        pending: Int,
        completed: Int,
        received: Int,
        receivedScore: Double?
    ) {
        binding.tvPeerPendingValue.text = "$pending 人"
        binding.tvPeerCompletedValue.text = "$completed 人"
        binding.tvPeerReceivedValue.text = if (received > 0) {
            if (receivedScore != null) "$received 人 · ${"%.1f".format(receivedScore)}分"
            else "$received 人"
        } else {
            "暂无"
        }
        when {
            pending > 0 -> {
                binding.tvColleagueEvalTag.isVisible = true
                binding.tvColleagueEvalTag.text = "待评价 $pending"
                binding.tvColleagueEvalTag.setBackgroundResource(R.drawable.bg_archive_tag_warn)
                binding.tvColleagueEvalTag.setTextColor(Color.parseColor("#EA9300"))
            }
            received > 0 -> {
                binding.tvColleagueEvalTag.isVisible = true
                binding.tvColleagueEvalTag.text = "收到 $received 条"
                binding.tvColleagueEvalTag.setBackgroundResource(R.drawable.bg_goal_status_processing)
                binding.tvColleagueEvalTag.setTextColor(Color.parseColor("#00AA60"))
            }
            completed > 0 -> {
                binding.tvColleagueEvalTag.isVisible = true
                binding.tvColleagueEvalTag.text = "已完成"
                binding.tvColleagueEvalTag.setBackgroundResource(R.drawable.bg_goal_status_processing)
                binding.tvColleagueEvalTag.setTextColor(Color.parseColor("#00AA60"))
            }
            else -> binding.tvColleagueEvalTag.isVisible = false
        }
    }

    private fun openPeerEval(showEvalTab: Boolean, showReceivedTab: Boolean = false) {
        peerEvalLauncher.launch(
            Intent(this, OkrPeerEvalActivity::class.java).apply {
                putExtra(OkrPeerEvalActivity.EXTRA_PERIOD, OkrPeriodHelper.peerEvalPeriod())
                putExtra(OkrPeerEvalActivity.EXTRA_SHOW_EVAL_TAB, showEvalTab)
                putExtra(OkrPeerEvalActivity.EXTRA_SHOW_RECEIVED_TAB, showReceivedTab)
            }
        )
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
        val dateBased = OkrPeriodHelper.currentQuarterValue()
        val tabs = periods.ifEmpty { OkrPeriodHelper.defaultPeriodTabs() }
        if (selectedPeriod == null || tabs.none { it.value == selectedPeriod }) {
            selectedPeriod = tabs.firstOrNull { it.value == dateBased }?.value
                ?: tabs.firstOrNull { it.active }?.value
                ?: tabs.firstOrNull()?.value
                ?: dateBased
        }
        tabs.forEachIndexed { index, period -> addPeriodTab(container, period, index > 0) }
    }

    private fun updatePeriodActions() {
        val period = selectedPeriod ?: OkrPeriodHelper.currentQuarterValue()
        val ended = OkrPeriodHelper.isPeriodEndedByValue(period)
        val showPeerEval = OkrPeriodHelper.isPeerEvalVisibleForTab(period)
        binding.btnAddGoal.isEnabled = !ended
        binding.btnAddGoal.alpha = if (ended) 0.45f else 1f
        binding.tvPeriodEndedHint.isVisible = ended
        binding.tvPeriodEndedHint.text =
            "${OkrPeriodHelper.quarterLabel(period)} 已结束，仅可查看，不可新增目标或更新进度"
        binding.section360.isVisible = showPeerEval
    }

    private fun addPeriodTab(container: LinearLayout, period: OkrPeriodOption, addMargin: Boolean) {
        val tab = TextView(this).apply {
            text = period.label
            setPadding(dp(14), 0, dp(14), 0)
            gravity = android.view.Gravity.CENTER
            textSize = 12f
            includeFontPadding = false
            val selected = period.value == selectedPeriod
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
                updatePeriodActions()
                viewModel.loadGoalsOnly(period.value)
                if (OkrPeriodHelper.isPeerEvalVisibleForTab(period.value)) {
                    viewModel.refreshPeerEvalBadges()
                }
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
