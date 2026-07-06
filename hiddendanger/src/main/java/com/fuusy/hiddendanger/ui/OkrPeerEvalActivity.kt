package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.app.Dialog
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
import com.fuusy.hiddendanger.data.OkrPeerUser
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
    private var showEvalTab = false

    private lateinit var collaboratorAdapter: PeerCollaboratorAdapter
    private lateinit var taskAdapter: PeerEvalTaskAdapter

    private var prepFieldsBound = false

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
        taskAdapter = PeerEvalTaskAdapter { task -> openSubmit(task) }

        binding.rvCollaborators.layoutManager = LinearLayoutManager(this)
        binding.rvCollaborators.adapter = collaboratorAdapter
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = taskAdapter

        binding.btnBack.setOnClickListener { finish() }
        binding.tabReview.setOnClickListener { selectTab(review = true) }
        binding.tabEval.setOnClickListener { selectTab(review = false) }
        binding.btnAddCollaborator.setOnClickListener { showUserPicker() }
        binding.btnSaveReview.setOnClickListener {
            viewModel.saveReviewPrep(
                binding.etProjectOutput.text?.toString().orEmpty(),
                binding.etSkillGrowth.text?.toString().orEmpty()
            )
        }

        observeViewModel()
        val openEvalTab = intent.getBooleanExtra(EXTRA_SHOW_EVAL_TAB, false)
        selectTab(review = !openEvalTab)
        viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { binding.progressLoading.isVisible = it == true }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.reviewPrep.observe(this) { prep ->
            if (!prepFieldsBound) {
                binding.etProjectOutput.setText(prep.projectOutput.orEmpty())
                binding.etSkillGrowth.setText(prep.skillGrowth.orEmpty())
                prepFieldsBound = true
            }
            refreshCollaborators()
        }
        viewModel.tasks.observe(this) { tasks ->
            taskAdapter.submitList(tasks)
            binding.tvTasksEmpty.isVisible = tasks.isEmpty()
            binding.rvTasks.isVisible = tasks.isNotEmpty()
            updateEvalTabBadge(tasks.count { !it.isDone })
        }
        viewModel.saved.observe(this) { saved ->
            if (saved) {
                viewModel.consumeSaved()
                Toast.makeText(this, "复盘已保存", Toast.LENGTH_SHORT).show()
                selectTab(review = false)
            }
        }
    }

    private fun refreshCollaborators() {
        val list = viewModel.collaboratorsSnapshot()
        collaboratorAdapter.submitList(list)
        binding.tvNoCollaborator.isVisible = list.isEmpty()
        binding.rvCollaborators.isVisible = list.isNotEmpty()
    }

    private fun updateEvalTabBadge(pending: Int) {
        binding.tabEval.text = if (pending > 0) "待我评价 ($pending)" else "待我评价"
    }

    private fun selectTab(review: Boolean) {
        showEvalTab = !review
        binding.tabReview.setBackgroundResource(
            if (review) R.drawable.bg_goal_period_tab_selected
            else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabEval.setBackgroundResource(
            if (!review) R.drawable.bg_goal_period_tab_selected
            else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabReview.setTextColor(
            Color.parseColor(if (review) "#1365EC" else "#686D79")
        )
        binding.tabEval.setTextColor(
            Color.parseColor(if (!review) "#1365EC" else "#686D79")
        )
        binding.panelReview.isVisible = review
        binding.panelEval.isVisible = !review
    }

    private fun showUserPicker() {
        val options = viewModel.userOptions.value.orEmpty()
        val selectedIds = viewModel.collaboratorsSnapshot().map { it.userId }.toSet()
        val available = options.filter { it.userId !in selectedIds }

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

        fun updateConfirmLabel(count: Int) {
            btnConfirm.text = if (count > 0) "确定（$count）" else "确定"
            btnConfirm.alpha = if (count > 0) 1f else 0.45f
            btnConfirm.isEnabled = count > 0
        }

        val pickAdapter = PeerCollaboratorPickAdapter { count -> updateConfirmLabel(count) }
        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = pickAdapter
        pickAdapter.submitList(available)

        val hasOptions = available.isNotEmpty()
        rvUsers.isVisible = hasOptions
        tvEmpty.isVisible = !hasOptions
        updateConfirmLabel(0)

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

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.toolbar)
    }

    companion object {
        private const val EXTRA_PERIOD = "period"
        private const val EXTRA_SHOW_EVAL_TAB = "show_eval_tab"

        fun start(context: Context, period: String? = null, showEvalTab: Boolean = false) {
            context.startActivity(
                Intent(context, OkrPeerEvalActivity::class.java).apply {
                    period?.let { putExtra(EXTRA_PERIOD, it) }
                    putExtra(EXTRA_SHOW_EVAL_TAB, showEvalTab)
                }
            )
        }
    }
}
