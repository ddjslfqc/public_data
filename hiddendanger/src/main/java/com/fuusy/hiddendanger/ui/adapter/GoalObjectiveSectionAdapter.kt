package com.fuusy.hiddendanger.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.databinding.ItemGoalObjectiveSectionBinding
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrNavHelper

class GoalObjectiveSectionAdapter(
    private val onKrClick: (GoalKrItem) -> Unit = {},
    private val onObjectiveEdit: ((OkrObjective) -> Unit)? = null
) : ListAdapter<OkrObjective, GoalObjectiveSectionAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemGoalObjectiveSectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGoalObjectiveSectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val objective = getItem(position)
        holder.binding.apply {
            tvObjectiveTitle.text = objective.title
            val periodEnded = OkrPeriodHelper.isPeriodEnded(objective.endDate)
            val statusLabel = OkrPeriodHelper.objectiveStatusLabel(objective)
            tvObjectiveStatus.text = statusLabel
            tvObjectiveStatus.isVisible = statusLabel.isNotBlank()
            if (periodEnded) {
                tvObjectiveStatus.setBackgroundResource(R.drawable.bg_goal_period_tab_normal)
                tvObjectiveStatus.setTextColor(Color.parseColor("#686D79"))
            } else {
                tvObjectiveStatus.setBackgroundResource(R.drawable.bg_goal_progress_tag)
                tvObjectiveStatus.setTextColor(Color.parseColor("#1365EC"))
            }
            // 仅「我的目标」传入 onObjectiveEdit；周期已结束不展示
            tvEdit.isVisible = onObjectiveEdit != null && !periodEnded
            tvEdit.setOnClickListener {
                if (onObjectiveEdit != null && !periodEnded) onObjectiveEdit.invoke(objective)
            }
            tvCreatedAt.text = objective.createTime?.take(10).orEmpty().ifBlank { "—" }
            tvPeriodLabel.text = OkrPeriodHelper.objectivePeriodDisplay(objective)
            val (completed, total) = OkrPeriodHelper.krCompletionStats(objective)
            tvCurrentProgress.text = if (total > 0) "$completed/$total" else "—"

            val parentKr = objective.parentKr
            if (parentKr != null) {
                tvAlignInfo.isVisible = true
                val oTitle = parentKr.objective?.title.orEmpty()
                tvAlignInfo.text = "对齐：${parentKr.title}" +
                    if (oTitle.isNotBlank()) "（$oTitle）" else ""
            } else {
                tvAlignInfo.isVisible = false
            }

            val krAdapter = (rvKr.adapter as? GoalKrAdapter) ?: GoalKrAdapter(onItemClick = onKrClick).also {
                rvKr.layoutManager = LinearLayoutManager(root.context)
                rvKr.adapter = it
            }
            krAdapter.submitList(
                objective.keyResults.orEmpty().map { kr ->
                    KrNavHelper.goalKrItem(objective, kr)
                }
            )
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OkrObjective>() {
        override fun areItemsTheSame(oldItem: OkrObjective, newItem: OkrObjective) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: OkrObjective, newItem: OkrObjective) =
            oldItem == newItem
    }
}
