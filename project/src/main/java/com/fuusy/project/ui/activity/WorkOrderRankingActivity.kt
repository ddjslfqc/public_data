package com.fuusy.project.ui.activity

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.project.R
import com.fuusy.project.databinding.ActivityWorkOrderRankingBinding
import com.fuusy.project.databinding.ItemWorkOrderRankingBinding
import com.fuusy.project.workorder.MobileWorkOrderRepository
import com.fuusy.project.workorder.WorkOrderDashboardDto
import com.fuusy.project.workorder.WorkOrderRankingItemDto
import kotlinx.coroutines.launch

@Route(path = "/project/WorkOrderRankingActivity")
class WorkOrderRankingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkOrderRankingBinding
    private val repo = MobileWorkOrderRepository()
    private val avatarBgs = intArrayOf(
        R.drawable.bg_home_avatar_blue,
        R.drawable.bg_home_avatar_gold,
        R.drawable.bg_home_avatar_green
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkOrderRankingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = Color.parseColor("#EEF1F8")
        binding.btnBack.setOnClickListener { finish() }
        binding.rvRanking.layoutManager = LinearLayoutManager(this)
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val rankingResult = repo.ranking(50)
            val dashboardResult = repo.dashboard()
            rankingResult.onSuccess { list ->
                binding.rvRanking.adapter = RankingAdapter(list)
            }.onFailure {
                Toast.makeText(this@WorkOrderRankingActivity, it.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
            dashboardResult.onSuccess { applyMyRank(it) }
        }
    }

    private fun applyMyRank(dashboard: WorkOrderDashboardDto) {
        val myRank = dashboard.myRank
        if (myRank != null && myRank.rank > 0) {
            binding.tvMyRank.text = "第 ${myRank.rank} 名 · 完成任务 ${myRank.completedCount} 个"
        } else {
            binding.tvMyRank.text = "完成任务 ${dashboard.completedCount} 个"
        }
    }

    private inner class RankingAdapter(
        private val items: List<WorkOrderRankingItemDto>
    ) : RecyclerView.Adapter<RankingAdapter.VH>() {

        inner class VH(val binding: ItemWorkOrderRankingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val binding = ItemWorkOrderRankingBinding.inflate(layoutInflater, parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val name = item.nickName?.takeIf { it.isNotBlank() } ?: "用户"
            holder.binding.apply {
                tvRank.text = item.rank.toString()
                tvName.text = name
                tvAvatar.text = name.firstOrNull()?.toString() ?: "?"
                tvAvatar.setBackgroundResource(avatarBgs[position % avatarBgs.size])
                tvTaskCount.text = "完成任务 ${item.completedCount} 个"
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
