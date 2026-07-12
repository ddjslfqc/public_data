package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
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
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrPeriodOption
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.OrgTeamMemberItem
import com.fuusy.hiddendanger.databinding.ActivityOrgTeamBinding
import com.fuusy.hiddendanger.ui.adapter.GoalObjectiveSectionAdapter
import com.fuusy.hiddendanger.ui.adapter.OrgTeamMemberAdapter
import com.fuusy.hiddendanger.ui.adapter.PeerCollaboratorAdapter
import com.fuusy.hiddendanger.ui.model.KrNavHelper
import com.fuusy.hiddendanger.viewmodel.OrgTeamViewModel

@Route(path = "/hiddendanger/OrgTeamActivity")
class OrgTeamActivity : AppCompatActivity() {

    private enum class ViewState { MEMBER_LIST, MEMBER_DETAIL }

    private lateinit var binding: ActivityOrgTeamBinding
    private val viewModel: OrgTeamViewModel by viewModels()
    private var viewState = ViewState.MEMBER_LIST
    private var selectedPeriod: String = OkrPeriodHelper.currentQuarterValue()
    private var selectedMember: OrgTeamMemberItem? = null

    private val memberAdapter = OrgTeamMemberAdapter { member -> openMemberDetail(member) }
    private val objectiveAdapter = GoalObjectiveSectionAdapter { item ->
        startActivity(
            Intent(this, KrDetailActivity::class.java).apply {
                KrNavHelper.putExtra(this, item)
                putExtra(KrDetailActivity.EXTRA_READ_ONLY, true)
            }
        )
    }
    private val collaboratorAdapter = PeerCollaboratorAdapter { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrgTeamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#0A3D8F")
        applyStatusBarPadding()

        collaboratorAdapter.readOnly = true

        binding.btnBack.setOnClickListener { handleBack() }
        binding.btnUserBack.setOnClickListener { backToMemberList() }

        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = memberAdapter
        binding.rvObjectives.layoutManager = LinearLayoutManager(this)
        binding.rvObjectives.adapter = objectiveAdapter
        binding.rvCollaborators.layoutManager = LinearLayoutManager(this)
        binding.rvCollaborators.adapter = collaboratorAdapter

        observeViewModel()
        intent.getStringExtra(EXTRA_PERIOD)?.let { selectedPeriod = it }
        renderPeriodTabs(OkrPeriodHelper.defaultPeriodTabs())
        viewModel.load(selectedPeriod)
    }

    override fun onBackPressed() {
        if (handleBack()) return
        super.onBackPressed()
    }

    private fun handleBack(): Boolean {
        if (viewState == ViewState.MEMBER_DETAIL) {
            backToMemberList()
            return true
        }
        finish()
        return true
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) {
            binding.progressLoading.isVisible = it == true && viewState == ViewState.MEMBER_LIST
        }
        viewModel.detailLoading.observe(this) {
            binding.progressLoading.isVisible = it == true && viewState == ViewState.MEMBER_DETAIL
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.members.observe(this) { members ->
            if (viewState != ViewState.MEMBER_LIST) return@observe
            memberAdapter.submitList(members)
            binding.tvEmptyMembers.isVisible = members.isEmpty()
            binding.tvEmptyMembers.text = "暂无成员数据"
            binding.rvMembers.isVisible = members.isNotEmpty()
        }
        viewModel.memberCount.observe(this) { bindHeroStats() }
        viewModel.okrMemberCount.observe(this) { bindHeroStats() }
        viewModel.reviewCompletedCount.observe(this) { bindHeroStats() }
        viewModel.reviewPrep.observe(this) { prep -> bindReviewDetail(prep) }
    }

    private fun bindHeroStats() {
        val periodLabel = OkrPeriodHelper.quarterLabel(selectedPeriod)
        binding.tvSummary.text = "团队成员 · $periodLabel"
        val total = viewModel.memberCount.value ?: 0
        val okrCount = viewModel.okrMemberCount.value ?: 0
        val reviewCount = viewModel.reviewCompletedCount.value ?: 0
        binding.tvStats.text = "共 $total 人 · $okrCount 人设 OKR · $reviewCount 人已复盘"
    }

    private fun openMemberDetail(member: OrgTeamMemberItem) {
        selectedMember = member
        viewState = ViewState.MEMBER_DETAIL
        binding.tvUserTitle.text = buildString {
            append(member.ownerName)
            member.deptName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        val objectives = member.objectives
        val emptyObjectives = objectives.isEmpty()
        binding.tvEmptyObjectives.isVisible = emptyObjectives
        binding.rvObjectives.isVisible = !emptyObjectives
        objectiveAdapter.submitList(objectives)
        viewModel.clearReviewPrep()
        binding.tvDetailEmpty.isVisible = false
        binding.layoutReviewContent.isVisible = false
        updateViewStateUi()
        viewModel.loadReviewPrep(member.userId)
    }

    private fun backToMemberList() {
        viewState = ViewState.MEMBER_LIST
        selectedMember = null
        viewModel.clearReviewPrep()
        objectiveAdapter.submitList(emptyList())
        memberAdapter.submitList(viewModel.members.value.orEmpty())
        updateViewStateUi()
    }

    private fun updateViewStateUi() {
        val onList = viewState == ViewState.MEMBER_LIST
        binding.sectionMemberList.isVisible = onList
        binding.sectionMemberDetail.isVisible = !onList
    }

    private fun bindReviewDetail(prep: OkrReviewPrep?) {
        if (viewState != ViewState.MEMBER_DETAIL) return
        val hasContent = prep != null && (
            !prep.projectOutput.isNullOrBlank() ||
                !prep.skillGrowth.isNullOrBlank() ||
                !prep.collaborators.isNullOrEmpty()
            )
        binding.tvDetailEmpty.isVisible = !hasContent
        binding.layoutReviewContent.isVisible = hasContent
        if (!hasContent) return

        binding.tvProjectOutput.text =
            prep?.projectOutput?.takeIf { it.isNotBlank() } ?: "未填写"
        binding.tvSkillGrowth.text =
            prep?.skillGrowth?.takeIf { it.isNotBlank() } ?: "未填写"
        collaboratorAdapter.submitList(prep?.collaborators.orEmpty())
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
                viewState = ViewState.MEMBER_LIST
                renderPeriodTabs(OkrPeriodHelper.defaultPeriodTabs())
                updateViewStateUi()
                viewModel.load(period.value)
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
        private const val EXTRA_PERIOD = "period"

        fun start(context: Context, period: String? = null) {
            context.startActivity(
                Intent(context, OrgTeamActivity::class.java).apply {
                    period?.let { putExtra(EXTRA_PERIOD, it) }
                }
            )
        }
    }
}
