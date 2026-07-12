package com.fuusy.project.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.common.support.Constants
import com.fuusy.common.utils.SpUtils
import com.fuusy.project.R
import com.fuusy.project.adapter.HomePendingOrderAdapter
import com.fuusy.project.databinding.FragmentHomeBinding
import com.fuusy.project.databinding.ItemHomeLeaderBinding
import com.fuusy.project.ui.model.HomePendingOrderItem
import com.fuusy.project.workorder.MobileWorkOrderRepository
import com.fuusy.project.workorder.WorkOrderListScope
import com.fuusy.project.workorder.WorkOrderRankingItemDto
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val pendingAdapter = HomePendingOrderAdapter { item ->
        item.workOrder?.let { order ->
            ARouter.getInstance()
                .build("/hiddendanger/OrderDetailActivity")
                .withSerializable("workOrder", order)
                .navigation()
        }
    }

    private val workOrderRepo = MobileWorkOrderRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyStatusBarPadding()
        setupGreeting()
        resetDashboardUi()
        setupPendingOrders()
        setupClicks()
        refreshDashboard()
    }

    private fun resetDashboardUi() {
        binding.tvCompletedCount.text = "0"
        binding.tvAverageRating.text = "--"
        binding.cardRanking.isVisible = false
        renderLeaderboard(emptyList())
    }

    private fun applyStatusBarPadding() {
        val extraTopPx = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerArea) { v, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarTop + extraTopPx)
            insets
        }
        ViewCompat.requestApplyInsets(binding.headerArea)
    }

    private fun setupGreeting() {
        val greeting = timeGreeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        binding.tvGreeting.text = "$greeting，欢迎回来"
        val username = SpUtils.getString("user_name", "")?.takeIf { it.isNotBlank() } ?: "用户"
        binding.tvUsername.text = username
    }

    private fun setupPendingOrders() {
        binding.rvPendingOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPendingOrders.adapter = pendingAdapter
        refreshPendingOrders()
    }

    private fun refreshDashboard() {
        viewLifecycleOwner.lifecycleScope.launch {
            workOrderRepo.dashboard()
                .onSuccess { data ->
                    binding.tvCompletedCount.text = data.completedCount.toString()
                    binding.tvAverageRating.text = formatAverageRating(data.averageRating)
                    val ranking = data.rankingTop3.orEmpty()
                    binding.cardRanking.isVisible = ranking.isNotEmpty()
                    renderLeaderboard(ranking)
                }
                .onFailure {
                    binding.tvCompletedCount.text = "0"
                    binding.tvAverageRating.text = "--"
                    binding.cardRanking.isVisible = false
                    renderLeaderboard(emptyList())
                }
        }
    }

    private fun formatAverageRating(rating: Double): String =
        if (rating > 0) String.format("%.1f", rating) else "--"

    private fun renderLeaderboard(items: List<WorkOrderRankingItemDto>) {
        val avatarBgs = intArrayOf(
            R.drawable.bg_home_avatar_blue,
            R.drawable.bg_home_avatar_gold,
            R.drawable.bg_home_avatar_green
        )
        val bindings = listOf(binding.leader1, binding.leader2, binding.leader3)
        bindings.forEach { include ->
            ItemHomeLeaderBinding.bind(include.root).root.visibility = View.GONE
        }
        items.take(3).forEachIndexed { index, item ->
            val row = ItemHomeLeaderBinding.bind(bindings[index].root)
            row.root.visibility = View.VISIBLE
            val name = item.nickName?.takeIf { it.isNotBlank() } ?: "用户"
            row.tvAvatar.text = name.firstOrNull()?.toString() ?: "?"
            row.tvAvatar.setBackgroundResource(avatarBgs[index % avatarBgs.size])
            row.tvName.text = name
            row.tvTaskCount.text = "任务量: ${item.completedCount}"
        }
    }

    private fun refreshPendingOrders() {
        val activeStatuses = setOf(
            WorkOrderStatus.PENDING,
            WorkOrderStatus.PROCESSING,
            WorkOrderStatus.DRAFT,
            WorkOrderStatus.EVAL,
            WorkOrderStatus.REJECT
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val items = workOrderRepo.listAll(WorkOrderListScope.RELATED)
                .getOrNull()
                .orEmpty()
                .filter { it.status in activeStatuses }
                .take(3)
                .map { HomePendingOrderItem.fromWorkOrder(it) }
            pendingAdapter.submitList(items)
        }
    }

    private fun setupClicks() {
        binding.cardUwbDebug.setOnClickListener {
            ARouter.getInstance().build("/project/UwbDebugActivity").navigation()
        }
        binding.cardStatCompleted.setOnClickListener { openCompletedOrders() }
        binding.cardStatRating.setOnClickListener {
            ARouter.getInstance()
                .build("/hiddendanger/EvaluationRecordActivity")
                .navigation()
        }
        binding.tvRankingMore.setOnClickListener {
            ARouter.getInstance()
                .build("/project/WorkOrderRankingActivity")
                .navigation()
        }
        binding.tvOrdersMore.setOnClickListener { openRelatedTasks() }
    }

    private fun openCompletedOrders() {
        ARouter.getInstance()
            .build("/project/HistoryOrderActivity")
            .withString(HistoryOrderActivity.EXTRA_LIST_MODE, HistoryOrderActivity.MODE_COMPLETED)
            .navigation()
    }

    private fun openRelatedTasks() {
        ARouter.getInstance()
            .build("/project/HistoryOrderActivity")
            .withString(HistoryOrderActivity.EXTRA_LIST_MODE, HistoryOrderActivity.MODE_RELATED)
            .navigation()
    }

    override fun onResume() {
        super.onResume()
        setupGreeting()
        refreshDashboard()
        refreshPendingOrders()
    }

    fun onProjectSwitched(newProjectId: String) {
        refreshDashboard()
        refreshPendingOrders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(projectId: String? = null): HomeFragment {
            return HomeFragment().apply {
                arguments = Bundle().apply {
                    projectId?.let { putString(Constants.KEY_PROJECT_ID, it) }
                }
            }
        }

        fun timeGreeting(hour: Int): String = when (hour) {
            in 5..11 -> "早上好"
            in 12..17 -> "中午好"
            else -> "晚上好"
        }
    }
}
