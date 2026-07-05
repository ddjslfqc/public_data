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
        applyStatusBarPadding()
        binding.tabSubmitted.visibility = View.GONE
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
        if (arguments?.getBoolean(ARG_SHOW_ACTIVE_PENDING) == true || pendingShowActive) {
            pendingShowActive = false
            switchTab(FilterType.ACTIVE)
        }
    }

    /** 从首页「待处理工单 · 查看更多」进入时调用 */
    fun showActivePendingOrders() {
        if (_binding == null) {
            pendingShowActive = true
            return
        }
        switchTab(FilterType.ACTIVE)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        viewModel.refreshTabCounts()
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
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SHOW_ACTIVE_PENDING = "show_active_pending"

        val ACTIVE_PENDING_STATUSES = setOf(
            WorkOrderStatus.PENDING,
            WorkOrderStatus.PROCESSING,
            WorkOrderStatus.DRAFT,
            WorkOrderStatus.EVAL
        )

        fun newInstance(showActivePending: Boolean = false): WorkOrderListFragment =
            WorkOrderListFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SHOW_ACTIVE_PENDING, showActivePending)
                }
            }
    }
}
