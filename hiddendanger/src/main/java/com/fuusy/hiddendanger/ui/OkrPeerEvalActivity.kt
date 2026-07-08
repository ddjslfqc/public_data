package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.app.Dialog
import android.widget.ProgressBar
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
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrPeerUser
import com.fuusy.hiddendanger.data.PeerEvalSummary
import com.fuusy.hiddendanger.data.PeerEvalTask
import com.fuusy.hiddendanger.databinding.ActivityOkrPeerEvalBinding
import com.fuusy.hiddendanger.ui.adapter.PeerCollaboratorAdapter
import com.fuusy.hiddendanger.ui.adapter.PeerCollaboratorPickAdapter
import com.fuusy.hiddendanger.ui.adapter.PeerEvalTaskAdapter
import com.fuusy.hiddendanger.viewmodel.PeerEvalViewModel

@Route(path = "/hiddendanger/OkrPeerEvalActivity")
class OkrPeerEvalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOkrPeerEvalBinding
    private val viewModel: PeerEvalViewModel by viewModels()
    private var currentTab = PeerTab.REVIEW

    private lateinit var collaboratorAdapter: PeerCollaboratorAdapter
    private lateinit var taskAdapter: PeerEvalTaskAdapter

    private var prepFieldsBound = false
    private var reviewCompleted = false
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOkrPeerEvalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#FFFFFF")
        applyStatusBarPadding()

        val period = intent.getStringExtra(EXTRA_PERIOD)
        viewModel.init(period)

        collaboratorAdapter = PeerCollaboratorAdapter { user ->
            viewModel.removeCollaborator(user.userId)
            refreshCollaborators()
        }
        taskAdapter = PeerEvalTaskAdapter { task ->
            if (task.isDone) openDetail(task) else openSubmit(task)
        }

        binding.rvCollaborators.layoutManager = LinearLayoutManager(this)
        binding.rvCollaborators.adapter = collaboratorAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = taskAdapter

        binding.btnBack.setOnClickListener { finish() }
        binding.tabReview.setOnClickListener { selectTab(PeerTab.REVIEW) }
        binding.tabEval.setOnClickListener { selectTab(PeerTab.EVAL) }
        binding.tabReceived.setOnClickListener { selectTab(PeerTab.RECEIVED) }
        binding.btnAddCollaborator.setOnClickListener { showUserPicker() }
        binding.btnSaveReview.setOnClickListener {
            viewModel.saveReviewPrep(
                binding.etProjectOutput.text?.toString().orEmpty(),
                binding.etSkillGrowth.text?.toString().orEmpty()
            )
        }
        binding.btnViewReceivedDetail.setOnClickListener {
            viewModel.ensureReceivedDetailLoaded {
                OkrPeerEvalReceivedActivity.start(
                    this,
                    viewModel.period,
                    viewModel.receivedSnapshot()
                )
            }
        }

        observeViewModel()
        bindPeerEvalPeriodUi()
        when {
            intent.getBooleanExtra(EXTRA_SHOW_RECEIVED_TAB, false) -> selectTab(PeerTab.RECEIVED)
            intent.getBooleanExtra(EXTRA_SHOW_EVAL_TAB, false) -> selectTab(PeerTab.EVAL)
            else -> selectTab(PeerTab.REVIEW)
        }
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) {
            viewModel.refreshOnReturn()
        } else {
            viewModel.loadInitial()
            resumedOnce = true
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { binding.progressLoading.isVisible = it == true }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.reviewPrep.observe(this) { prep ->
            bindReviewPanel(prep)
        }
        viewModel.tasks.observe(this) { tasks ->
            taskAdapter.submitList(tasks)
            binding.tvTasksEmpty.isVisible = tasks.isEmpty()
            binding.rvTasks.isVisible = tasks.isNotEmpty()
            updateEvalTabBadge(tasks.count { !it.isDone })
        }
        viewModel.summary.observe(this) { bindReceivedPreview(it) }
        viewModel.saved.observe(this) { saved ->
            if (saved) {
                viewModel.consumeSaved()
                Toast.makeText(this, "复盘已保存", Toast.LENGTH_SHORT).show()
                viewModel.reviewPrep.value?.let { bindReviewPanel(it) }
            }
        }
    }

    private fun bindReviewPanel(prep: OkrReviewPrep) {
        reviewCompleted = prep.isReviewCompleted()
        val quarterLabel = OkrPeriodHelper.peerEvalQuarterLabel()

        binding.tvReviewSectionTitle.text =
            if (reviewCompleted) "我的复盘" else "$quarterLabel 产出与收获"
        binding.tvReviewSavedBadge.isVisible = reviewCompleted
        binding.tvReviewSectionHint.isVisible = !reviewCompleted
        binding.tvPeerEvalScope.isVisible = !reviewCompleted
        binding.tvReviewPeriodSubtitle.isVisible = reviewCompleted
        binding.tvReviewPeriodSubtitle.text = "$quarterLabel 复盘记录"

        binding.etProjectOutput.isVisible = !reviewCompleted
        binding.tvProjectOutput.isVisible = reviewCompleted
        binding.etSkillGrowth.isVisible = !reviewCompleted
        binding.tvSkillGrowth.isVisible = reviewCompleted
        binding.btnAddCollaborator.isVisible = !reviewCompleted
        binding.btnSaveReview.isVisible = !reviewCompleted
        binding.tvCollaboratorHint.isVisible = !reviewCompleted

        if (reviewCompleted) {
            binding.tvProjectOutput.text = prep.projectOutput.orEmpty()
            binding.tvSkillGrowth.text = prep.skillGrowth.orEmpty()
        } else if (!prepFieldsBound) {
            binding.etProjectOutput.setText(prep.projectOutput.orEmpty())
            binding.etSkillGrowth.setText(prep.skillGrowth.orEmpty())
            prepFieldsBound = true
        }

        collaboratorAdapter.readOnly = reviewCompleted
        refreshCollaborators()
        updateReviewTabLabel()
    }

    private fun updateReviewTabLabel() {
        val label = OkrPeriodHelper.peerEvalQuarterLabel()
        binding.tabReview.text = if (reviewCompleted) "我的复盘" else "$label 复盘"
    }

    private fun bindReceivedPreview(summary: PeerEvalSummary?) {
        val count = summary?.receivedEvaluatorCount ?: 0
        val hasData = count > 0
        binding.tvReceivedCount.isVisible = hasData
        binding.tvReceivedScore.isVisible = hasData
        binding.btnViewReceivedDetail.isVisible = hasData
        binding.tvReceivedEmpty.isVisible = !hasData
        if (hasData && summary != null) {
            binding.tvReceivedCount.text = "$count 位同事已完成评价"
            binding.tvReceivedScore.text = "%.1f".format(summary.receivedAverageScore ?: 0.0)
            updateReceivedTabBadge(count)
        } else {
            updateReceivedTabBadge(0)
        }
    }

    private fun refreshCollaborators() {
        val list = viewModel.collaboratorsSnapshot()
        collaboratorAdapter.submitList(list)
        binding.tvNoCollaborator.isVisible = list.isEmpty()
        binding.rvCollaborators.isVisible = list.isNotEmpty()
    }

    private fun updateEvalTabBadge(pending: Int) {
        binding.tabEval.text = if (pending > 0) "待我评价($pending)" else "待我评价"
    }

    private fun updateReceivedTabBadge(count: Int) {
        binding.tabReceived.text = if (count > 0) "收到的($count)" else "收到的评价"
    }

    private fun selectTab(tab: PeerTab) {
        currentTab = tab
        val review = tab == PeerTab.REVIEW
        val eval = tab == PeerTab.EVAL
        val received = tab == PeerTab.RECEIVED

        binding.tabReview.setBackgroundResource(
            if (review) R.drawable.bg_goal_period_tab_selected else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabEval.setBackgroundResource(
            if (eval) R.drawable.bg_goal_period_tab_selected else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabReceived.setBackgroundResource(
            if (received) R.drawable.bg_goal_period_tab_selected else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabReview.setTextColor(Color.parseColor(if (review) "#1365EC" else "#686D79"))
        binding.tabEval.setTextColor(Color.parseColor(if (eval) "#1365EC" else "#686D79"))
        binding.tabReceived.setTextColor(Color.parseColor(if (received) "#1365EC" else "#686D79"))

        binding.panelReview.isVisible = review
        binding.panelEval.isVisible = eval
        binding.panelReceived.isVisible = received
        if (received) {
            viewModel.ensureReceivedDetailLoaded()
        }
    }

    private fun showUserPicker() {
        val dialog = Dialog(this, R.style.CustomDialog)
        val sheetView = layoutInflater.inflate(R.layout.dialog_peer_collaborator_picker, null)
        dialog.setContentView(sheetView)
        dialog.window?.apply {
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val rvUsers = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_users)
        val tvEmpty = sheetView.findViewById<TextView>(R.id.tv_empty)
        val btnConfirm = sheetView.findViewById<TextView>(R.id.btn_confirm)
        val progress = sheetView.findViewById<ProgressBar>(R.id.progress_loading)

        fun updateConfirmLabel(count: Int) {
            btnConfirm.text = if (count > 0) "确定（$count）" else "确定"
            btnConfirm.alpha = if (count > 0) 1f else 0.45f
            btnConfirm.isEnabled = count > 0
        }

        val pickAdapter = PeerCollaboratorPickAdapter { count -> updateConfirmLabel(count) }
        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = pickAdapter
        updateConfirmLabel(0)

        fun bindColleagueList() {
            val selectedIds = viewModel.collaboratorsSnapshot().map { it.userId }.toSet()
            val available = viewModel.userOptions.value.orEmpty()
                .filter { it.userId !in selectedIds }
            progress.isVisible = false
            pickAdapter.submitList(available)
            val hasOptions = available.isNotEmpty()
            rvUsers.isVisible = hasOptions
            tvEmpty.isVisible = !hasOptions
            tvEmpty.text = "暂无可选同事"
        }

        progress.isVisible = true
        rvUsers.isVisible = false
        tvEmpty.isVisible = false

        sheetView.findViewById<android.widget.ImageView>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }
        btnConfirm.setOnClickListener {
            val picked = pickAdapter.selectedUsers()
            if (picked.isEmpty()) {
                Toast.makeText(this, "请至少选择 1 位同事", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addCollaborators(picked)
            refreshCollaborators()
            dialog.dismiss()
        }

        dialog.show()
        viewModel.refreshColleagues { bindColleagueList() }
    }

    private fun openSubmit(task: PeerEvalTask) {
        OkrPeerEvalSubmitActivity.start(
            context = this,
            period = viewModel.period,
            targetUserId = task.targetUserId,
            targetUserName = task.targetUserName.orEmpty(),
            deptName = task.deptName
        )
    }

    private fun openDetail(task: PeerEvalTask) {
        OkrPeerEvalDetailActivity.start(
            context = this,
            period = viewModel.period,
            targetUserId = task.targetUserId,
            targetUserName = task.targetUserName.orEmpty(),
            deptName = task.deptName
        )
    }

    private fun bindPeerEvalPeriodUi() {
        val label = OkrPeriodHelper.peerEvalQuarterLabel()
        updateReviewTabLabel()
        binding.tvPeerEvalScope.text =
            "基于 $label 合作经历进行互评；当前 ${OkrPeriodHelper.quarterLabel(OkrPeriodHelper.currentQuarterValue())} 目标不在互评范围内"
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.toolbar)
    }

    private enum class PeerTab { REVIEW, EVAL, RECEIVED }

    companion object {
        private const val EXTRA_PERIOD = "period"
        private const val EXTRA_SHOW_EVAL_TAB = "show_eval_tab"
        private const val EXTRA_SHOW_RECEIVED_TAB = "show_received_tab"

        fun start(
            context: Context,
            period: String? = null,
            showEvalTab: Boolean = false,
            showReceivedTab: Boolean = false
        ) {
            context.startActivity(
                Intent(context, OkrPeerEvalActivity::class.java).apply {
                    period?.let { putExtra(EXTRA_PERIOD, it) }
                    putExtra(EXTRA_SHOW_EVAL_TAB, showEvalTab)
                    putExtra(EXTRA_SHOW_RECEIVED_TAB, showReceivedTab)
                }
            )
        }
    }
}
