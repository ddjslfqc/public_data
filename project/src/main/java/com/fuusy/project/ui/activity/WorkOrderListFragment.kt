package com.fuusy.project.ui.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class WorkOrderListFragment : Fragment() {

    private var _binding: FragmentWorkOrderListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WorkOrderListViewModel by viewModels()
    private lateinit var adapter: HistoryOrderAdapter
    private var currentFilter: FilterType = FilterType.ALL
    private var allOrders: List<com.fuusy.common.data.WorkOrderItem> = emptyList()

    private enum class FilterType(val status: WorkOrderStatus?) {
        ALL(null),
        DRAFT(WorkOrderStatus.DRAFT),
        PENDING(WorkOrderStatus.PENDING),
        SUBMITTED(WorkOrderStatus.SUBMITTED),
        REJECT(WorkOrderStatus.REJECT),
        PROCESSING(WorkOrderStatus.PROCESSING),
        EVAL(WorkOrderStatus.EVAL),
        COMPLETED(WorkOrderStatus.COMPLETED)
    }

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
        adapter = HistoryOrderAdapter { item ->
            ARouter.getInstance()
                .build("/hiddendanger/OrderDetailActivity")
                .withSerializable("workOrder", item)
                .navigation()
        }
        binding.rvWorkOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWorkOrders.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAll() }
        setupTabs()
        setupSearch()
        setupFab()
        setupStatClicks()
        observeViewModel()
        viewModel.loadAll()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAll()
    }

    private fun observeViewModel() {
        viewModel.orders.observe(viewLifecycleOwner) { list ->
            allOrders = list
            updateTabCounts()
            updateStatBar()
            applyFilter()
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
        binding.tabSubmitted.setOnClickListener { switchTab(FilterType.SUBMITTED) }
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
            override fun afterTextChanged(s: Editable?) = applyFilter()
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
        applyFilter()
    }

    private fun updateTabCounts() {
        val tabs = listOf(
            binding.tabAll to FilterType.ALL,
            binding.tabDraft to FilterType.DRAFT,
            binding.tabPending to FilterType.PENDING,
            binding.tabSubmitted to FilterType.SUBMITTED,
            binding.tabReject to FilterType.REJECT,
            binding.tabProcessing to FilterType.PROCESSING,
            binding.tabEval to FilterType.EVAL,
            binding.tabCompleted to FilterType.COMPLETED
        )
        tabs.forEach { (tab, type) ->
            val count = when (type.status) {
                null -> allOrders.size
                WorkOrderStatus.SUBMITTED -> allOrders.count {
                    it.status == WorkOrderStatus.SUBMITTED || it.status == WorkOrderStatus.PENDING
                }
                else -> allOrders.count { it.status == type.status }
            }
            tab.text = "${tabLabel(type)} $count"
        }
    }

    private fun tabLabel(type: FilterType): String = when (type) {
        FilterType.ALL -> "全部"
        FilterType.DRAFT -> "待提交"
        FilterType.PENDING -> "待认领"
        FilterType.SUBMITTED -> "已提交"
        FilterType.REJECT -> "驳回"
        FilterType.PROCESSING -> "处理中"
        FilterType.EVAL -> "待评价"
        FilterType.COMPLETED -> "已完成"
    }

    private fun updateStatBar() {
        binding.statPendingNum.text = allOrders.count {
            it.status == WorkOrderStatus.PENDING || it.status == WorkOrderStatus.SUBMITTED
        }.toString()
        binding.statProcessingNum.text = allOrders.count { it.status == WorkOrderStatus.PROCESSING }.toString()
        binding.statEvalNum.text = allOrders.count { it.status == WorkOrderStatus.EVAL }.toString()
        binding.statCompletedNum.text = allOrders.count { it.status == WorkOrderStatus.COMPLETED }.toString()
    }

    private fun updateTabStyle() {
        val normalBg = R.drawable.bg_tab_normal
        val normalColor = ContextCompat.getColor(requireContext(), R.color.home_text_normal)
        val white = ContextCompat.getColor(requireContext(), R.color.color_ffffff)

        val tabs = listOf(
            binding.tabAll to FilterType.ALL,
            binding.tabDraft to FilterType.DRAFT,
            binding.tabPending to FilterType.PENDING,
            binding.tabSubmitted to FilterType.SUBMITTED,
            binding.tabReject to FilterType.REJECT,
            binding.tabProcessing to FilterType.PROCESSING,
            binding.tabEval to FilterType.EVAL,
            binding.tabCompleted to FilterType.COMPLETED
        )
        tabs.forEach { (tab, type) ->
            val selected = currentFilter == type
            if (selected) {
                if (type == FilterType.ALL) {
                    tab.setBackgroundResource(R.drawable.bg_tab_selected)
                    tab.setTextColor(white)
                } else {
                    tab.background = selectedTabDrawable(type)
                    tab.setTextColor(white)
                }
            } else {
                tab.setBackgroundResource(normalBg)
                tab.setTextColor(normalColor)
            }
        }
    }

    private fun selectedTabDrawable(type: FilterType): android.graphics.drawable.GradientDrawable {
        val color = when (type) {
            FilterType.ALL -> android.graphics.Color.parseColor("#1465EB")
            FilterType.DRAFT -> android.graphics.Color.parseColor("#898FA0")
            FilterType.PENDING -> android.graphics.Color.parseColor("#F97316")
            FilterType.SUBMITTED -> android.graphics.Color.parseColor("#1465EB")
            FilterType.REJECT -> android.graphics.Color.parseColor("#EB1919")
            FilterType.PROCESSING -> android.graphics.Color.parseColor("#6366F1")
            FilterType.EVAL -> android.graphics.Color.parseColor("#8B5CF6")
            FilterType.COMPLETED -> android.graphics.Color.parseColor("#00AA60")
        }
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(color)
        }
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

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun applyFilter() {
        val key = binding.etSearch.text?.toString().orEmpty()
        val status = currentFilter.status
        val filtered = allOrders.filter { order ->
            val statusMatch = when (status) {
                null -> true
                WorkOrderStatus.SUBMITTED -> order.status == WorkOrderStatus.SUBMITTED ||
                    order.status == WorkOrderStatus.PENDING
                else -> order.status == status
            }
            statusMatch && (key.isBlank()
                || order.hiddenDangerName?.contains(key, ignoreCase = true) == true
                || order.workOrderNo?.contains(key, ignoreCase = true) == true
                || order.id.contains(key, ignoreCase = true)
                || order.submitUser.contains(key, ignoreCase = true)
                || order.responsiblePerson?.contains(key, ignoreCase = true) == true)
        }
        adapter.submitList(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): WorkOrderListFragment = WorkOrderListFragment()
    }
}
