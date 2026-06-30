package com.fuusy.project.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.fuusy.project.ui.model.HomeLeaderItem
import com.fuusy.project.ui.model.HomePendingOrderItem
import com.fuusy.project.workorder.MobileWorkOrderRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        setupGreeting()
        setupLeaderboard()
        setupPendingOrders()
        setupClicks()
    }

    private fun setupGreeting() {
        val hour = SimpleDateFormat("HH", Locale.getDefault()).format(Date()).toInt()
        val greeting = when {
            hour < 6 -> "凌晨好"
            hour < 9 -> "早上好"
            hour < 12 -> "上午好"
            hour < 14 -> "中午好"
            hour < 18 -> "下午好"
            hour < 22 -> "晚上好"
            else -> "夜深了"
        }
        binding.tvGreeting.text = "$greeting，欢迎回来"
        val username = SpUtils.getString("user_name", "")?.takeIf { it.isNotBlank() } ?: "常 伟思"
        binding.tvUsername.text = username
        binding.tvCompletedCount.text = "28"
        binding.tvAverageRating.text = "4.5"
    }

    private fun setupLeaderboard() {
        val leaders = HomeMockData.leaders
        val bindings = listOf(binding.leader1, binding.leader2, binding.leader3)
        leaders.forEachIndexed { index, item ->
            bindings[index].apply {
                tvAvatar.text = item.initial
                tvAvatar.setBackgroundResource(item.avatarBgRes)
                tvName.text = item.name
                tvTaskCount.text = "任务量: ${item.taskCount}"
            }
        }
    }

    private fun setupPendingOrders() {
        binding.rvPendingOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPendingOrders.adapter = pendingAdapter
        refreshPendingOrders()
    }

    private val workOrderRepo = MobileWorkOrderRepository()

    private fun refreshPendingOrders() {
        val activeStatuses = setOf(
            WorkOrderStatus.PENDING,
            WorkOrderStatus.PROCESSING,
            WorkOrderStatus.DRAFT,
            WorkOrderStatus.EVAL
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val items = workOrderRepo.listAll()
                .getOrNull()
                .orEmpty()
                .filter { it.status in activeStatuses }
                .take(3)
                .map { order ->
                    HomePendingOrderItem(
                        title = order.hiddenDangerName.orEmpty(),
                        time = order.submitTime,
                        status = order.nodeName?.takeIf { it.isNotBlank() } ?: order.status.displayName,
                        workOrder = order
                    )
                }
            pendingAdapter.submitList(items)
        }
    }

    private fun setupClicks() {
        binding.tvRankingMore.setOnClickListener {
            showToast("排行榜详情即将上线")
        }
        binding.tvOrdersMore.setOnClickListener {
            openWorkOrderTab()
        }
    }

    private fun openWorkOrderTab() {
        (activity as? ProjectDetailActivity)?.switchToWorkOrderTab()
            ?: ARouter.getInstance()
                .build("/project/HistoryOrderActivity")
                .navigation()
    }

    override fun onResume() {
        super.onResume()
        refreshPendingOrders()
    }

    fun onProjectSwitched(newProjectId: String) {
        // 预留：切换项目后刷新首页数据
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
    }
}

private object HomeMockData {
    val leaders = listOf(
        HomeLeaderItem("章北海", "章", 12, R.drawable.bg_home_avatar_blue, 1),
        HomeLeaderItem("常伟思", "常", 10, R.drawable.bg_home_avatar_gold, 2),
        HomeLeaderItem("汪淼", "汪", 8, R.drawable.bg_home_avatar_green, 3)
    )
}
