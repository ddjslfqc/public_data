package com.fuusy.hiddendanger.ui

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
import com.fuusy.hiddendanger.data.OkrAlignmentTreeResponse
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrPeriodOption
import com.fuusy.hiddendanger.data.OrgOkrAlignmentItem
import com.fuusy.hiddendanger.data.OrgOkrUserSummary
import com.fuusy.hiddendanger.databinding.ActivityOrgOkrBinding
import com.fuusy.hiddendanger.ui.adapter.GoalObjectiveSectionAdapter
import com.fuusy.hiddendanger.ui.adapter.OrgOkrAlignmentAdapter
import com.fuusy.hiddendanger.ui.adapter.OrgOkrUserAdapter
import com.fuusy.hiddendanger.ui.model.KrNavHelper
import com.fuusy.hiddendanger.viewmodel.OrgOkrViewModel

@Route(path = "/hiddendanger/OrgOkrActivity")
class OrgOkrActivity : AppCompatActivity() {

    private enum class BrowseMode { MEMBER, ALIGNMENT }
    private enum class ViewState { LIST, USER_DETAIL, OBJECTIVE_DETAIL }

    private lateinit var binding: ActivityOrgOkrBinding
    private val viewModel: OrgOkrViewModel by viewModels()
    private var browseMode = BrowseMode.MEMBER
    private var viewState = ViewState.LIST
    private var selectedPeriod: String = OkrPeriodHelper.currentQuarterValue()

    private val userAdapter = OrgOkrUserAdapter { user -> openUserDetail(user) }
    private val alignmentAdapter = OrgOkrAlignmentAdapter { item -> openObjectiveDetail(item) }
    private val objectiveAdapter = GoalObjectiveSectionAdapter { item ->
        startActivity(
            Intent(this, KrDetailActivity::class.java).apply {
                KrNavHelper.putExtra(this, item)
                putExtra(KrDetailActivity.EXTRA_READ_ONLY, true)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrgOkrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#0A3D8F")
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { handleBack() }
        binding.btnUserBack.setOnClickListener { handleBack() }
        binding.btnDeptFilter.setOnClickListener { showDeptFilterDialog() }
        binding.tabViewMember.setOnClickListener { switchBrowseMode(BrowseMode.MEMBER) }
        binding.tabViewAlignment.setOnClickListener { switchBrowseMode(BrowseMode.ALIGNMENT) }
        binding.etSearchMember.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchKeyword(s?.toString().orEmpty())
            }
        })

        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = userAdapter
        binding.rvAlignment.layoutManager = LinearLayoutManager(this)
        binding.rvAlignment.adapter = alignmentAdapter
        binding.rvObjectives.layoutManager = LinearLayoutManager(this)
        binding.rvObjectives.adapter = objectiveAdapter

        observeViewModel()
        renderPeriodTabs(OkrPeriodHelper.defaultPeriodTabs())
        updateBrowseModeUi()
        viewModel.load(selectedPeriod)
    }

    override fun onBackPressed() {
        if (handleBack()) return
        super.onBackPressed()
    }

    private fun handleBack(): Boolean {
        when (viewState) {
            ViewState.OBJECTIVE_DETAIL -> {
                viewState = ViewState.LIST
                objectiveAdapter.submitList(emptyList())
                updateViewStateUi()
                return true
            }
            ViewState.USER_DETAIL -> {
                viewState = ViewState.LIST
                objectiveAdapter.submitList(emptyList())
                updateViewStateUi()
                return true
            }
            ViewState.LIST -> finish()
        }
        return true
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) {
            binding.progressLoading.isVisible = it == true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.orgUsers.observe(this) { users ->
            if (viewState != ViewState.LIST || browseMode != BrowseMode.MEMBER) return@observe
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
                "当前周期暂无成员设置 OKR"
            }
            binding.rvUsers.isVisible = users.isNotEmpty()
        }
        viewModel.alignmentItems.observe(this) { items ->
            if (viewState != ViewState.LIST || browseMode != BrowseMode.ALIGNMENT) return@observe
            alignmentAdapter.submitList(items)
            val keyword = viewModel.searchKeyword.value.orEmpty().trim()
            val isSearching = keyword.isNotEmpty()
            binding.tvAlignmentSearchHint.isVisible = isSearching
            if (isSearching) {
                binding.tvAlignmentSearchHint.text = "筛选出 ${items.size} 个目标"
            }
            binding.tvEmptyAlignment.isVisible = items.isEmpty()
            binding.tvEmptyAlignment.text = if (isSearching) {
                "未找到匹配「$keyword」的目标"
            } else {
                "当前周期暂无对齐目标"
            }
            binding.rvAlignment.isVisible = items.isNotEmpty()
        }
        viewModel.memberCount.observe(this) { bindHeroStats() }
        viewModel.objectiveCount.observe(this) { bindHeroStats() }
        viewModel.rootChainCount.observe(this) { bindHeroStats() }
        viewModel.alignmentTree.observe(this) { tree ->
            bindHero(tree)
            updateDeptLabel(viewModel.deptFilter(), tree?.departments.orEmpty())
        }
    }

    private fun bindHeroStats() {
        val periodLabel = viewModel.alignmentTree.value?.periodLabel
            ?: OkrPeriodHelper.quarterLabel(selectedPeriod)
        binding.tvSummary.text = "组织 OKR · $periodLabel"
        val userCount = viewModel.memberCount.value ?: 0
        val objectiveCount = viewModel.objectiveCount.value ?: 0
        val chainCount = viewModel.rootChainCount.value ?: 0
        binding.tvStats.text = when (browseMode) {
            BrowseMode.MEMBER -> "共 $userCount 人 · $objectiveCount 个目标 · 全员可见"
            BrowseMode.ALIGNMENT -> "$objectiveCount 个目标 · $chainCount 条对齐链 · 全员可见"
        }
    }

    private fun switchBrowseMode(mode: BrowseMode) {
        if (browseMode == mode && viewState == ViewState.LIST) return
        browseMode = mode
        viewState = ViewState.LIST
        objectiveAdapter.submitList(emptyList())
        binding.etSearchMember.hint = if (mode == BrowseMode.MEMBER) {
            "搜索姓名或部门"
        } else {
            "搜索姓名、部门或目标"
        }
        updateBrowseModeUi()
        updateViewStateUi()
        bindHeroStats()
        refreshCurrentList()
    }

    private fun refreshCurrentList() {
        when (browseMode) {
            BrowseMode.MEMBER -> userAdapter.submitList(viewModel.orgUsers.value.orEmpty())
            BrowseMode.ALIGNMENT -> alignmentAdapter.submitList(viewModel.alignmentItems.value.orEmpty())
        }
    }

    private fun updateBrowseModeUi() {
        val memberSelected = browseMode == BrowseMode.MEMBER
        binding.tabViewMember.apply {
            setBackgroundResource(
                if (memberSelected) R.drawable.bg_goal_period_tab_selected
                else R.drawable.bg_personal_menu_card
            )
            setTextColor(
                if (memberSelected) Color.parseColor("#1365EC")
                else Color.parseColor("#686D79")
            )
        }
        binding.tabViewAlignment.apply {
            setBackgroundResource(
                if (memberSelected) R.drawable.bg_personal_menu_card
                else R.drawable.bg_goal_period_tab_selected
            )
            setTextColor(
                if (memberSelected) Color.parseColor("#686D79")
                else Color.parseColor("#1365EC")
            )
        }
    }

    private fun openUserDetail(user: OrgOkrUserSummary) {
        viewState = ViewState.USER_DETAIL
        binding.tvUserTitle.text = buildString {
            append(user.ownerName)
            user.deptName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        val objectives = user.objectives
        val empty = objectives.isEmpty()
        binding.tvEmptyObjectives.isVisible = empty
        binding.rvObjectives.isVisible = !empty
        objectiveAdapter.submitList(objectives)
        updateViewStateUi()
    }

    private fun openObjectiveDetail(item: OrgOkrAlignmentItem) {
        viewState = ViewState.OBJECTIVE_DETAIL
        binding.tvUserTitle.text = buildString {
            append(item.ownerName)
            item.deptName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        binding.tvEmptyObjectives.isVisible = false
        binding.rvObjectives.isVisible = true
        objectiveAdapter.submitList(listOf(item.objective))
        updateViewStateUi()
    }

    private fun updateViewStateUi() {
        val onList = viewState == ViewState.LIST
        val onDetail = !onList

        binding.layoutViewTabs.isVisible = onList
        binding.sectionFilters.isVisible = onList
        binding.sectionUserList.isVisible = onList && browseMode == BrowseMode.MEMBER
        binding.sectionAlignmentList.isVisible = onList && browseMode == BrowseMode.ALIGNMENT
        binding.layoutUserHeader.isVisible = onDetail
        binding.tvObjectivesTitle.isVisible = onDetail
        binding.rvObjectives.isVisible = onDetail && objectiveAdapter.itemCount > 0
        binding.tvEmptyObjectives.isVisible = onDetail && objectiveAdapter.itemCount == 0
    }

    private fun bindHero(tree: OkrAlignmentTreeResponse?) {
        bindHeroStats()
    }

    private fun showDeptFilterDialog() {
        val departments = viewModel.orgDepartments.value.orEmpty()
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
        val dateBased = OkrPeriodHelper.currentQuarterValue()
        if (periods.none { it.value == selectedPeriod }) {
            selectedPeriod = periods.firstOrNull { it.value == dateBased }?.value
                ?: periods.firstOrNull()?.value
                ?: dateBased
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
                viewState = ViewState.LIST
                binding.etSearchMember.setText("")
                viewModel.setSearchKeyword("")
                renderPeriodTabs(OkrPeriodHelper.defaultPeriodTabs())
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
}
