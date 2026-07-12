package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrPeriodOption
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.PeerEvalOrgOverviewResponse
import com.fuusy.hiddendanger.data.PeerEvalOrgUserItem
import com.fuusy.hiddendanger.databinding.ActivityPeerEvalOrgBinding
import com.fuusy.hiddendanger.ui.adapter.PeerCollaboratorAdapter
import com.fuusy.hiddendanger.ui.adapter.PeerEvalOrgUserAdapter
import com.fuusy.hiddendanger.viewmodel.PeerEvalOrgViewModel

@Route(path = "/hiddendanger/PeerEvalOrgActivity")
class PeerEvalOrgActivity : AppCompatActivity() {

    private enum class ViewState { USER_LIST, USER_DETAIL }

    private lateinit var binding: ActivityPeerEvalOrgBinding
    private val viewModel: PeerEvalOrgViewModel by viewModels()
    private var viewState = ViewState.USER_LIST
    private var selectedPeriod: String = OkrPeriodHelper.peerEvalPeriod()
    private var selectedUser: PeerEvalOrgUserItem? = null

    private val userAdapter = PeerEvalOrgUserAdapter { user -> openUserDetail(user) }
    private val collaboratorAdapter = PeerCollaboratorAdapter { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPeerEvalOrgBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#0A3D8F")
        applyStatusBarPadding()

        collaboratorAdapter.readOnly = true

        binding.btnBack.setOnClickListener { handleBack() }
        binding.btnUserBack.setOnClickListener { backToUserList() }
        binding.btnDeptFilter.setOnClickListener { showDeptFilterDialog() }
        binding.etSearchMember.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchKeyword(s?.toString().orEmpty())
            }
        })

        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = userAdapter
        binding.rvCollaborators.layoutManager = LinearLayoutManager(this)
        binding.rvCollaborators.adapter = collaboratorAdapter

        observeViewModel()
        intent.getStringExtra(EXTRA_PERIOD)?.let { selectedPeriod = it }
        renderPeriodTabs(OkrPeriodHelper.peerEvalPeriodTabs())
        viewModel.load(selectedPeriod)
    }

    override fun onBackPressed() {
        if (handleBack()) return
        super.onBackPressed()
    }

    private fun handleBack(): Boolean {
        if (viewState == ViewState.USER_DETAIL) {
            backToUserList()
            return true
        }
        finish()
        return true
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) {
            binding.progressLoading.isVisible = it == true && viewState == ViewState.USER_LIST
        }
        viewModel.detailLoading.observe(this) {
            binding.progressLoading.isVisible = it == true && viewState == ViewState.USER_DETAIL
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.users.observe(this) { users ->
            if (viewState != ViewState.USER_LIST) return@observe
            userAdapter.submitList(users)
            val keyword = viewModel.searchKeyword.value.orEmpty().trim()
            val isSearching = keyword.isNotEmpty()
            binding.tvSearchResultHint.isVisible = isSearching
            if (isSearching) {
                binding.tvSearchResultHint.text = "筛选出 ${users.size} 人"
            }
            binding.tvEmptyUsers.isVisible = users.isEmpty()
            binding.tvEmptyUsers.text = if (isSearching) {
                "未找到匹配「$keyword」的成员"
            } else {
                "暂无成员数据"
            }
            binding.rvUsers.isVisible = users.isNotEmpty()
        }
        viewModel.overview.observe(this) { overview ->
            bindHero(overview)
            updateDeptLabel(viewModel.deptFilter(), viewModel.departments.value.orEmpty())
        }
        viewModel.reviewPrep.observe(this) { prep -> bindReviewDetail(prep) }
    }

    private fun openUserDetail(user: PeerEvalOrgUserItem) {
        selectedUser = user
        viewState = ViewState.USER_DETAIL
        binding.tvUserTitle.text = buildString {
            append(user.displayName())
            user.deptName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        viewModel.clearReviewPrep()
        updateViewStateUi()
        viewModel.loadReviewPrep(user.userId)
    }

    private fun backToUserList() {
        viewState = ViewState.USER_LIST
        selectedUser = null
        viewModel.clearReviewPrep()
        userAdapter.submitList(viewModel.users.value.orEmpty())
        updateViewStateUi()
    }

    private fun updateViewStateUi() {
        val list = viewState == ViewState.USER_LIST
        binding.sectionUserList.isVisible = list
        binding.layoutUserHeader.isVisible = !list
        binding.sectionReviewDetail.isVisible = !list
        if (list) {
            binding.layoutReviewContent.isVisible = false
            binding.tvDetailEmpty.isVisible = false
        }
    }

    private fun bindHero(overview: PeerEvalOrgOverviewResponse?) {
        val periodLabel = OkrPeriodHelper.quarterLabel(selectedPeriod)
        binding.tvSummary.text = "组织互评 · $periodLabel"
        val total = overview?.totalUserCount ?: 0
        val completed = overview?.reviewPrepCompletedCount ?: 0
        binding.tvStats.text = "共 $total 人 · $completed 人已复盘 · 全员可见"
    }

    private fun bindReviewDetail(prep: OkrReviewPrep?) {
        if (viewState != ViewState.USER_DETAIL) return
        val hasContent = prep != null && (
            !prep.projectOutput.isNullOrBlank() ||
                !prep.skillGrowth.isNullOrBlank() ||
                !prep.collaborators.isNullOrEmpty()
            )
        binding.tvDetailEmpty.isVisible = !hasContent
        binding.layoutReviewContent.isVisible = hasContent
        if (!hasContent) return

        val detail = prep ?: return
        binding.tvProjectOutput.text =
            detail.projectOutput?.takeIf { it.isNotBlank() } ?: "未填写"
        binding.tvSkillGrowth.text =
            detail.skillGrowth?.takeIf { it.isNotBlank() } ?: "未填写"
        collaboratorAdapter.submitList(detail.collaborators.orEmpty())
    }

    private fun showDeptFilterDialog() {
        val departments = viewModel.departments.value.orEmpty()
        if (departments.isEmpty()) {
            Toast.makeText(this, "暂无部门数据", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = mutableListOf("全部部门")
        labels.addAll(departments.map { it.name })
        AlertDialog.Builder(this)
            .setTitle("选择部门")
            .setItems(labels.toTypedArray()) { _, which ->
                binding.etSearchMember.setText("")
                viewModel.setSearchKeyword("")
                val deptId = if (which == 0) null else departments[which - 1].id
                viewModel.setDeptFilter(deptId)
                updateDeptLabel(deptId, departments)
            }
            .show()
    }

    private fun updateDeptLabel(deptId: Long?, departments: List<OkrDepartment>) {
        binding.tvDeptFilter.text = when (deptId) {
            null -> "全部部门"
            else -> departments.firstOrNull { it.id == deptId }?.name ?: "部门 $deptId"
        }
    }

    private fun renderPeriodTabs(periods: List<OkrPeriodOption>) {
        val container = binding.llPeriodTabs
        container.removeAllViews()
        val defaultPeriod = OkrPeriodHelper.peerEvalPeriod()
        if (periods.none { it.value == selectedPeriod }) {
            selectedPeriod = periods.firstOrNull { it.value == defaultPeriod }?.value
                ?: periods.firstOrNull()?.value
                ?: defaultPeriod
        }
        periods.forEachIndexed { index, period ->
            addPeriodTab(container, period, index > 0)
        }
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
                viewState = ViewState.USER_LIST
                binding.etSearchMember.setText("")
                viewModel.setSearchKeyword("")
                renderPeriodTabs(OkrPeriodHelper.peerEvalPeriodTabs())
                updateViewStateUi()
                viewModel.load(period.value, viewModel.deptFilter())
            }
        }
        container.addView(tab)
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeader) { view, insets ->
            view.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top + dp(4))
            insets
        }
        ViewCompat.requestApplyInsets(binding.heroHeader)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        fun start(context: Context, period: String? = null) {
            context.startActivity(
                Intent(context, PeerEvalOrgActivity::class.java).apply {
                    period?.let { putExtra(EXTRA_PERIOD, it) }
                }
            )
        }

        private const val EXTRA_PERIOD = "extra_period"
    }
}
