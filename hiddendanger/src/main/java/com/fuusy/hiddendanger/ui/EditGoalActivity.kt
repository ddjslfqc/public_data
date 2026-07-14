package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.os.Bundle
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
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.network.UserIdProvider
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.AlignableKr
import com.fuusy.hiddendanger.databinding.ActivityEditGoalBinding
import com.fuusy.hiddendanger.ui.adapter.GoalKrEditAdapter
import com.fuusy.hiddendanger.ui.model.GoalAlignType
import com.fuusy.hiddendanger.ui.model.GoalKrEditItem
import com.fuusy.hiddendanger.ui.model.GoalKrWeightHelper
import com.fuusy.hiddendanger.viewmodel.EditGoalViewModel

@Route(path = "/hiddendanger/EditGoalActivity")
class EditGoalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditGoalBinding
    private val viewModel: EditGoalViewModel by viewModels()
    private lateinit var krAdapter: GoalKrEditAdapter

    private var currentPeriodValue = PERIOD_Q2

    private val periodTabs by lazy {
        listOf(
            binding.tabQ2 to PERIOD_Q2,
            binding.tabQ3 to PERIOD_Q3,
            binding.tabQ4 to PERIOD_Q4,
            binding.tabAnnual to PERIOD_ANNUAL
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditGoalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#BAD4FF")
        applyStatusBarPadding()

        currentPeriodValue = intent.getStringExtra(EXTRA_PERIOD_VALUE) ?: PERIOD_Q2

        krAdapter = GoalKrEditAdapter(
            onDelete = { position -> krAdapter.removeItem(position) },
            onChanged = { updateKrUiState() },
            onItemAdded = { index -> scrollToKrAndFocus(index) },
            onAssigneeClick = { position -> showAssigneePicker(position) },
            onWeightChanged = { index, weight -> krAdapter.applyLinkedWeights(index, weight) }
        )

        binding.rvKr.layoutManager = LinearLayoutManager(this)
        binding.rvKr.adapter = krAdapter
        binding.rvKr.itemAnimator = null

        binding.btnDraft.isVisible = false
        binding.btnSubmit.text = "创建目标"

        setupActions()
        setupPeriodTabs()
        setupAlignSection()
        observeViewModel()

        krAdapter.submitItems(listOf(GoalKrEditItem()))
        updateKrUiState()
        viewModel.loadAlignOptions()
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { loading ->
            binding.btnSubmit.isEnabled = loading != true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.created.observe(this) { id ->
            if (id != null) {
                Toast.makeText(this, "目标已创建", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
        viewModel.alignOptions.observe(this) {
            updateScopeLabel()
        }
        viewModel.deptOptions.observe(this) {
            updateOwnDeptLabel()
            updateScopeLabel()
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnAddKr.setOnClickListener { addKrItem() }
        binding.btnSubmit.setOnClickListener {
            if (validateForm()) {
                viewModel.createObjective(
                    periodQueryValue = currentPeriodValue,
                    title = binding.etObjective.text?.toString().orEmpty(),
                    description = binding.etDescription.text?.toString(),
                    alignEnabled = binding.switchAlign.isChecked,
                    krItems = krAdapter.currentItems()
                )
            }
        }
        binding.rowOwnDept.setOnClickListener { showOwnDeptPicker() }
    }

    private fun setupAlignSection() {
        binding.switchAlign.setOnCheckedChangeListener { _, checked ->
            binding.llAlignContent.isVisible = checked
        }

        binding.tabAlignDept.setOnClickListener { selectAlignType(GoalAlignType.DEPARTMENT) }
        binding.tabAlignSupervisor.setOnClickListener { selectAlignType(GoalAlignType.SUPERVISOR) }

        binding.rowAlignLevel.setOnClickListener { showScopePicker() }
        binding.rowAlignTarget.setOnClickListener { showAlignKrPicker() }

        selectAlignType(GoalAlignType.DEPARTMENT)
    }

    private fun selectAlignType(type: GoalAlignType) {
        viewModel.alignType = type
        val selectedBg = R.drawable.bg_goal_align_selected
        val normalBg = R.drawable.bg_goal_align_normal

        binding.tabAlignDept.setBackgroundResource(
            if (type == GoalAlignType.DEPARTMENT) selectedBg else normalBg
        )
        binding.tabAlignSupervisor.setBackgroundResource(
            if (type == GoalAlignType.SUPERVISOR) selectedBg else normalBg
        )
        binding.tvAlignDeptLabel.setTextColor(
            if (type == GoalAlignType.DEPARTMENT) Color.WHITE else Color.BLACK
        )
        binding.tvAlignSupervisorLabel.setTextColor(
            if (type == GoalAlignType.SUPERVISOR) Color.WHITE else Color.BLACK
        )

        binding.tvAlignScopeLabel.text =
            if (type == GoalAlignType.DEPARTMENT) "选择部门 *" else "选择人员 *"

        viewModel.selectedParentKr = null
        binding.tvAlignTarget.text = "请选择要对齐的 KR"
        updateScopeLabel()
        viewModel.refreshAlignableKrs()
    }

    private fun updateScopeLabel() {
        val text = when (viewModel.alignType) {
            GoalAlignType.DEPARTMENT -> viewModel.selectedDept?.name
                ?: alignmentDepartments().firstOrNull()?.name
                ?: "请选择部门"
            GoalAlignType.SUPERVISOR -> viewModel.selectedUser?.displayName ?: "请选择人员"
        }
        binding.tvAlignLevel.text = text
    }

    private fun updateOwnDeptLabel() {
        binding.tvOwnDept.text = viewModel.ownDept?.name ?: "请选择所属部门"
    }

    private fun showOwnDeptPicker() {
        val depts = viewModel.deptOptions.value.orEmpty()
        if (depts.isEmpty()) {
            Toast.makeText(this, "暂无可选部门", Toast.LENGTH_SHORT).show()
            return
        }
        showEntityPicker("所属部门", depts.map { it.name }) { index ->
            viewModel.ownDept = depts[index]
            updateOwnDeptLabel()
        }
    }

    private fun showScopePicker() {
        when (viewModel.alignType) {
            GoalAlignType.DEPARTMENT -> {
                val depts = alignmentDepartments()
                if (depts.isEmpty()) {
                    Toast.makeText(this, "暂无可选部门", Toast.LENGTH_SHORT).show()
                    return
                }
                showEntityPicker("选择部门", depts.map { it.name }) { index ->
                    viewModel.selectedDept = depts[index]
                    viewModel.selectedParentKr = null
                    binding.tvAlignTarget.text = "请选择要对齐的 KR"
                    updateScopeLabel()
                    viewModel.refreshAlignableKrs()
                }
            }
            GoalAlignType.SUPERVISOR -> {
                val users = viewModel.alignOptions.value?.users.orEmpty()
                if (users.isEmpty()) {
                    Toast.makeText(this, "暂无可选人员", Toast.LENGTH_SHORT).show()
                    return
                }
                showEntityPicker("选择人员", users.map { it.displayName }) { index ->
                    viewModel.selectedUser = users[index]
                    viewModel.selectedParentKr = null
                    binding.tvAlignTarget.text = "请选择要对齐的 KR"
                    updateScopeLabel()
                    viewModel.refreshAlignableKrs()
                }
            }
        }
    }

    private fun showAlignKrPicker() {
        val krs = viewModel.alignableKrs.value.orEmpty()
        if (krs.isEmpty()) {
            Toast.makeText(this, "暂无可对齐的 KR", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = krs.map { krLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择对齐的 KR")
            .setItems(labels) { dialog, which ->
                viewModel.selectedParentKr = krs[which]
                binding.tvAlignTarget.text = labels[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun showAssigneePicker(position: Int) {
        val users = viewModel.alignOptions.value?.users.orEmpty()
        val selfName = "本人"
        val names = listOf(selfName) + users.map { it.displayName }
        AlertDialog.Builder(this)
            .setTitle("KR 负责人")
            .setItems(names.toTypedArray()) { dialog, which ->
                val items = krAdapter.currentItems().toMutableList()
                if (position !in items.indices) return@setItems
                if (which == 0) {
                    items[position] = items[position].copy(
                        assigneeUserId = UserIdProvider.userId,
                        assigneeName = selfName
                    )
                } else {
                    val user = users[which - 1]
                    items[position] = items[position].copy(
                        assigneeUserId = user.id,
                        assigneeName = user.displayName
                    )
                }
                krAdapter.submitItems(items)
                dialog.dismiss()
            }
            .show()
    }

    private fun krLabel(kr: AlignableKr): String {
        val owner = kr.objective?.ownerName.orEmpty()
        val objectiveTitle = kr.objective?.title.orEmpty()
        return buildString {
            append(kr.title)
            if (owner.isNotBlank() || objectiveTitle.isNotBlank()) {
                append("（")
                if (owner.isNotBlank()) append(owner)
                if (owner.isNotBlank() && objectiveTitle.isNotBlank()) append(" · ")
                if (objectiveTitle.isNotBlank()) append(objectiveTitle)
                append("）")
            }
        }
    }

    private fun alignmentDepartments(): List<com.fuusy.hiddendanger.data.OkrDepartment> {
        val fromAlign = viewModel.alignOptions.value?.departments.orEmpty()
        if (fromAlign.isNotEmpty()) return fromAlign
        return viewModel.deptOptions.value.orEmpty()
    }

    private fun showEntityPicker(title: String, options: List<String>, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options.toTypedArray()) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun addKrItem() {
        if (!krAdapter.canAddMore()) {
            Toast.makeText(
                this,
                "最多添加 ${GoalKrEditAdapter.MAX_KR_COUNT} 条关键结果",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        krAdapter.addItem()
    }

    private fun scrollToKrAndFocus(index: Int) {
        binding.rvKr.post {
            binding.rvKr.smoothScrollToPosition(index)
            binding.rvKr.postDelayed({
                val holder = binding.rvKr.findViewHolderForAdapterPosition(index)
                (holder as? GoalKrEditAdapter.VH)?.requestTitleFocus()
            }, 120)
        }
    }

    private fun updateKrUiState() {
        binding.btnAddKr.alpha = if (krAdapter.canAddMore()) 1f else 0.45f
        binding.tvKrCount.text = "已添加 ${krAdapter.itemCount()} 条"
        val multiKr = krAdapter.itemCount() > 1
        binding.tvKrWeightTotal.isVisible = multiKr
        if (!multiKr) return
        val total = krAdapter.totalWeight()
        binding.tvKrWeightTotal.text =
            "权重合计 ${total}%（拖动滑块调整，其余 KR 自动联动）"
        binding.tvKrWeightTotal.setTextColor(
            Color.parseColor(if (total == GoalKrWeightHelper.TOTAL) "#00AA60" else "#EB1919")
        )
    }

    private fun setupPeriodTabs() {
        periodTabs.forEach { (tab, value) ->
            tab.setOnClickListener { selectPeriod(value) }
        }
        updatePeriodTabStyle()
    }

    private fun selectPeriod(value: String) {
        currentPeriodValue = value
        updatePeriodTabStyle()
    }

    private fun updatePeriodTabStyle() {
        val selectedBg = R.drawable.bg_goal_form_period_selected
        val normalBg = R.drawable.bg_goal_form_period_normal
        val selectedText = Color.parseColor("#1365EC")
        val normalText = Color.parseColor("#000000")

        periodTabs.forEach { (tab, value) ->
            val selected = value == currentPeriodValue
            tab.setBackgroundResource(if (selected) selectedBg else normalBg)
            tab.setTextColor(if (selected) selectedText else normalText)
        }
    }

    private fun validateForm(): Boolean {
        val objective = binding.etObjective.text?.toString()?.trim().orEmpty()
        if (objective.isEmpty()) {
            Toast.makeText(this, "请填写目标内容", Toast.LENGTH_SHORT).show()
            return false
        }
        if (viewModel.ownDept == null) {
            Toast.makeText(this, "请选择所属部门", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.switchAlign.isChecked && viewModel.selectedParentKr == null) {
            Toast.makeText(this, "请选择要对齐的上级 KR", Toast.LENGTH_SHORT).show()
            return false
        }

        val krs = krAdapter.currentItems()
        val filledKrs = krs.filter { it.title.isNotBlank() }
        if (filledKrs.isEmpty()) {
            Toast.makeText(this, "请至少填写一条关键结果", Toast.LENGTH_SHORT).show()
            return false
        }
        if (filledKrs.sumOf { it.weight } != GoalKrWeightHelper.TOTAL) {
            Toast.makeText(this, "KR 权重合计必须为 100%", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.heroHeader) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.heroHeader)
    }

    companion object {
        const val EXTRA_PERIOD_VALUE = "period_value"
        private const val PERIOD_Q2 = "quarter-2"
        private const val PERIOD_Q3 = "quarter-3"
        private const val PERIOD_Q4 = "quarter-4"
        private const val PERIOD_ANNUAL = "year"

        fun start(periodValue: String? = PERIOD_Q2) {
            ARouter.getInstance()
                .build("/hiddendanger/EditGoalActivity")
                .withString(EXTRA_PERIOD_VALUE, periodValue ?: PERIOD_Q2)
                .navigation()
        }
    }
}
