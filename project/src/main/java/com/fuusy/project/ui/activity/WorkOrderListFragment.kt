package com.fuusy.project.ui.activity

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.project.R
import com.fuusy.project.adapter.HistoryOrderAdapter
import com.fuusy.project.databinding.FragmentWorkOrderListBinding
import com.fuusy.project.viewmodel.WorkOrderListViewModel
import com.fuusy.project.workorder.WorkOrderListScope

/** 工单列表：状态与 workorder-api.md §3.1 一致 */
class WorkOrderListFragment : Fragment() {

    private var _binding: FragmentWorkOrderListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WorkOrderListViewModel by viewModels()
    private lateinit var adapter: HistoryOrderAdapter
    private var currentFilter: FilterType = FilterType.ALL
    private var displayOrders: List<com.fuusy.common.data.WorkOrderItem> = emptyList()
    private var countOrders: List<com.fuusy.common.data.WorkOrderItem> = emptyList()

    private enum class FilterType(val status: WorkOrderStatus?) {
        ALL(null),
        /** 与首页「待处理工单」一致：待提交 / 待认领 / 处理中 / 待评价 */
        ACTIVE(null),
        DRAFT(WorkOrderStatus.DRAFT),
        PENDING(WorkOrderStatus.PENDING),
        REJECT(WorkOrderStatus.REJECT),
        PROCESSING(WorkOrderStatus.PROCESSING),
        EVAL(WorkOrderStatus.EVAL),
        COMPLETED(WorkOrderStatus.COMPLETED)
    }

    private var pendingShowActive = false
    private var pendingShowCompletedOnly = false
    private var pendingListMode: String? = null
    private var completedOnlyMode = false
    private var relatedOnlyMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkOrderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isSecondaryListPage()) {
            applyStatusBarPadding()
        }
        binding.tabSubmitted.visibility = View.GONE
        setEmptyState("暂无工单", "可以创建新工单或切换筛选条件")
        adapter = HistoryOrderAdapter { item ->
            ARouter.getInstance()
                .build("/hiddendanger/OrderDetailActivity")
                .withSerializable("workOrder", item)
                .navigation()
        }
        binding.rvWorkOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWorkOrders.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { reload() }
        setupTabs()
        setupSearch()
        setupFab()
        setupStatClicks()
        observeViewModel()
        updateTabStyle()
        reload()
        applyListModeFromArgs()
    }

    private fun applyListModeFromArgs() {
        val listMode = arguments?.getString(ARG_LIST_MODE).orEmpty().ifBlank {
            pendingListMode ?: when {
                arguments?.getBoolean(ARG_SHOW_COMPLETED_ONLY) == true || pendingShowCompletedOnly ->
                    HistoryOrderActivity.MODE_COMPLETED
                arguments?.getBoolean(ARG_SHOW_ACTIVE_PENDING) == true || pendingShowActive ->
                    HistoryOrderActivity.MODE_RELATED
                else -> ""
            }
        }
        pendingListMode = null
        when (listMode) {
            HistoryOrderActivity.MODE_COMPLETED -> enterCompletedMode()
            HistoryOrderActivity.MODE_RELATED -> enterRelatedMode()
        }
    }

    private fun enterCompletedMode() {
        completedOnlyMode = true
        applyCompletedOnlyUi()
        currentFilter = FilterType.COMPLETED
        viewModel.setListScope(WorkOrderListScope.HANDLED_COMPLETED)
        viewModel.load(null)
    }

    private fun enterRelatedMode() {
        relatedOnlyMode = true
        applyRelatedOnlyUi()
        currentFilter = FilterType.ALL
        viewModel.setListScope(WorkOrderListScope.RELATED)
        viewModel.load(null)
    }

    private fun applyRelatedOnlyUi() {
        binding.llSearch.visibility = View.GONE
        binding.statBar.visibility = View.GONE
        binding.fabCreate.visibility = View.GONE
        binding.topArea.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_ffffff))
        binding.topArea.updatePadding(bottom = dp(4))
        repositionListBelow(R.id.top_area, marginTopDp = 0)
        setEmptyState("暂无相关任务", "你提报或指派给你的工单会出现在这里")
    }

    private fun applyCompletedOnlyUi() {
        binding.topArea.visibility = View.GONE
        binding.statBar.visibility = View.GONE
        binding.fabCreate.visibility = View.GONE
        repositionListBelow(anchorBelowParent = true, marginTopDp = 0)
        setEmptyState("暂无完成任务", "你处理完成的工单会显示在这里")
        binding.rvWorkOrders.updatePadding(bottom = dp(16))
    }

    private fun setEmptyState(title: String, subtitle: String) {
        binding.layoutEmpty.tvEmptyTitle.text = title
        binding.layoutEmpty.tvEmptySubtitle.text = subtitle
    }

    private fun isSecondaryListPage(): Boolean {
        val args = arguments ?: return false
        if (args.getString(ARG_LIST_MODE) in SECONDARY_LIST_MODES) return true
        return args.getBoolean(ARG_SHOW_COMPLETED_ONLY) || args.getBoolean(ARG_SHOW_ACTIVE_PENDING)
    }

    private fun repositionListBelow(anchorViewId: Int = 0, anchorBelowParent: Boolean = false, marginTopDp: Int = 0) {
        val params = binding.swipeRefresh.layoutParams as ConstraintLayout.LayoutParams
        if (anchorBelowParent) {
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToBottom = ConstraintLayout.LayoutParams.UNSET
        } else {
            params.topToBottom = anchorViewId
            params.topToTop = ConstraintLayout.LayoutParams.UNSET
        }
        params.topMargin = dp(marginTopDp)
        binding.swipeRefresh.layoutParams = params
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 从首页「我相关的任务 · 查看更多」进入时调用 */
    fun showActivePendingOrders() {
        if (_binding == null) {
            pendingListMode = HistoryOrderActivity.MODE_RELATED
            return
        }
        enterRelatedMode()
    }

    /** 从首页/档案「完成任务」进入时调用 */
    fun showCompletedOrders() {
        if (_binding == null) {
            pendingListMode = HistoryOrderActivity.MODE_COMPLETED
            return
        }
        enterCompletedMode()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        if (!completedOnlyMode) {
            viewModel.refreshTabCounts()
        }
        when (currentFilter) {
            FilterType.ACTIVE -> viewModel.load(null)
            else -> viewModel.load(currentFilter.status)
        }
    }

    private fun observeViewModel() {
        viewModel.orders.observe(viewLifecycleOwner) { list ->
            displayOrders = applyStatusFilter(list)
            applySearchFilter()
        }
        viewModel.allForCount.observe(viewLifecycleOwner) { list ->
            countOrders = list
            updateTabCounts()
            updateStatBar()
        }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading == true
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun setupTabs() {
        binding.tabAll.setOnClickListener { switchTab(FilterType.ALL) }
        binding.tabDraft.setOnClickListener { switchTab(FilterType.DRAFT) }
        binding.tabPending.setOnClickListener { switchTab(FilterType.PENDING) }
        binding.tabReject.setOnClickListener { switchTab(FilterType.REJECT) }
        binding.tabProcessing.setOnClickListener { switchTab(FilterType.PROCESSING) }
        binding.tabEval.setOnClickListener { switchTab(FilterType.EVAL) }
        binding.tabCompleted.setOnClickListener { switchTab(FilterType.COMPLETED) }
    }

    private fun setupStatClicks() {
        binding.statPending.setOnClickListener { switchTab(FilterType.PENDING) }
        binding.statProcessing.setOnClickListener { switchTab(FilterType.PROCESSING) }
        binding.statEval.setOnClickListener { switchTab(FilterType.EVAL) }
        binding.statCompleted.setOnClickListener { switchTab(FilterType.COMPLETED) }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applySearchFilter()
        })
    }

    private fun setupFab() {
        binding.fabCreate.setOnClickListener {
            try {
                ARouter.getInstance()
                    .build("/hiddendanger/CreateWorkOrderActivity")
                    .navigation()
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "无法打开创建工单页", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchTab(filter: FilterType) {
        currentFilter = filter
        updateTabStyle()
        when (filter) {
            FilterType.ACTIVE -> viewModel.load(null)
            else -> viewModel.load(filter.status)
        }
    }

    private fun applyStatusFilter(list: List<com.fuusy.common.data.WorkOrderItem>): List<com.fuusy.common.data.WorkOrderItem> =
        when (currentFilter) {
            FilterType.ACTIVE -> list.filter { it.status in ACTIVE_PENDING_STATUSES }
            FilterType.ALL -> list
            else -> list.filter { it.status == currentFilter.status }
        }

    private fun updateTabCounts() {
        val tabs = listOf(
            binding.tabAll to FilterType.ALL,
            binding.tabDraft to FilterType.DRAFT,
            binding.tabPending to FilterType.PENDING,
            binding.tabReject to FilterType.REJECT,
            binding.tabProcessing to FilterType.PROCESSING,
            binding.tabEval to FilterType.EVAL,
            binding.tabCompleted to FilterType.COMPLETED
        )
        tabs.forEach { (tab, type) ->
            val count = when (type.status) {
                null -> countOrders.size
                else -> countOrders.count { it.status == type.status }
            }
            tab.text = "${tabLabel(type)} $count"
        }
    }

    private fun tabLabel(type: FilterType): String = when (type) {
        FilterType.ALL -> "全部"
        FilterType.ACTIVE -> "待处理"
        FilterType.DRAFT -> "待提交"
        FilterType.PENDING -> "待认领"
        FilterType.REJECT -> "驳回"
        FilterType.PROCESSING -> "处理中"
        FilterType.EVAL -> "待评价"
        FilterType.COMPLETED -> "已完成"
    }

    private fun updateStatBar() {
        binding.statPendingNum.text = countOrders.count { it.status == WorkOrderStatus.PENDING }.toString()
        binding.statProcessingNum.text = countOrders.count { it.status == WorkOrderStatus.PROCESSING }.toString()
        binding.statEvalNum.text = countOrders.count { it.status == WorkOrderStatus.EVAL }.toString()
        binding.statCompletedNum.text = countOrders.count { it.status == WorkOrderStatus.COMPLETED }.toString()
    }

    private fun updateTabStyle() {
        val normalColor = Color.parseColor("#F3F4F6")
        val normalTextColor = ContextCompat.getColor(requireContext(), R.color.home_text_normal)
        val selectedTextColor = ContextCompat.getColor(requireContext(), R.color.color_ffffff)
        val cornerRadiusPx = resources.getDimension(R.dimen.wo_filter_tab_radius)

        val tabs = listOf(
            binding.tabAll to FilterType.ALL,
            binding.tabDraft to FilterType.DRAFT,
            binding.tabPending to FilterType.PENDING,
            binding.tabReject to FilterType.REJECT,
            binding.tabProcessing to FilterType.PROCESSING,
            binding.tabEval to FilterType.EVAL,
            binding.tabCompleted to FilterType.COMPLETED
        )
        tabs.forEach { (tab, type) ->
            val selected = when (currentFilter) {
                FilterType.ACTIVE -> type == FilterType.ALL
                else -> currentFilter == type
            }
            applyTabBackground(
                tab = tab,
                color = if (selected) selectedTabColor(type) else normalColor,
                cornerRadiusPx = cornerRadiusPx
            )
            tab.setTextColor(if (selected) selectedTextColor else normalTextColor)
        }
    }

    private fun applyTabBackground(tab: TextView, color: Int, cornerRadiusPx: Float) {
        tab.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(color)
        }
        tab.clipToOutline = true
        tab.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private fun selectedTabColor(type: FilterType): Int = when (type) {
        FilterType.ALL, FilterType.ACTIVE -> ContextCompat.getColor(requireContext(), R.color.home_blue)
        FilterType.DRAFT -> Color.parseColor("#898FA0")
        FilterType.PENDING -> Color.parseColor("#F97316")
        FilterType.REJECT -> Color.parseColor("#EB1919")
        FilterType.PROCESSING -> Color.parseColor("#6366F1")
        FilterType.EVAL -> Color.parseColor("#8B5CF6")
        FilterType.COMPLETED -> Color.parseColor("#00AA60")
    }

    private fun applyStatusBarPadding() {
        val extraTopPx = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.topArea) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop + extraTopPx)
            insets
        }
        ViewCompat.requestApplyInsets(binding.topArea)
    }

    private fun applySearchFilter() {
        val key = binding.etSearch.text?.toString().orEmpty()
        val filtered = displayOrders.filter { order ->
            key.isBlank()
                || order.hiddenDangerName?.contains(key, ignoreCase = true) == true
                || order.workOrderNo?.contains(key, ignoreCase = true) == true
                || order.id.contains(key, ignoreCase = true)
                || order.submitUser.contains(key, ignoreCase = true)
                || order.responsiblePerson?.contains(key, ignoreCase = true) == true
        }
        adapter.submitList(filtered)
        val isEmpty = filtered.isEmpty()
        binding.layoutEmpty.root.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty && key.isNotBlank()) {
            setEmptyState("未找到匹配的工单", "试试其他关键词或切换筛选条件")
        } else if (isEmpty) {
            when {
                completedOnlyMode -> setEmptyState("暂无完成任务", "你处理完成的工单会显示在这里")
                relatedOnlyMode -> setEmptyState("暂无相关任务", "你提报或指派给你的工单会出现在这里")
                else -> setEmptyState("暂无工单", "可以创建新工单或切换筛选条件")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SHOW_ACTIVE_PENDING = "show_active_pending"
        private const val ARG_SHOW_COMPLETED_ONLY = "show_completed_only"
        private const val ARG_LIST_MODE = "list_mode"

        val ACTIVE_PENDING_STATUSES = setOf(
            WorkOrderStatus.PENDING,
            WorkOrderStatus.PROCESSING,
            WorkOrderStatus.DRAFT,
            WorkOrderStatus.EVAL,
            WorkOrderStatus.REJECT
        )

        private val SECONDARY_LIST_MODES = setOf(
            HistoryOrderActivity.MODE_COMPLETED,
            HistoryOrderActivity.MODE_RELATED
        )

        fun newInstance(
            showActivePending: Boolean = false,
            showCompletedOnly: Boolean = false,
            listMode: String = ""
        ): WorkOrderListFragment =
            WorkOrderListFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SHOW_ACTIVE_PENDING, showActivePending)
                    putBoolean(ARG_SHOW_COMPLETED_ONLY, showCompletedOnly)
                    if (listMode.isNotBlank()) putString(ARG_LIST_MODE, listMode)
                }
            }
    }
}
