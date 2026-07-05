package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.databinding.ItemGoalObjectiveSectionBinding
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrNavHelper

class GoalObjectiveSectionAdapter(
    private val onKrClick: (GoalKrItem) -> Unit = {}
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
            tvObjectiveStatus.text = objective.statusLabel.orEmpty()
            tvObjectiveStatus.isVisible = !objective.statusLabel.isNullOrBlank()
            tvCreatedAt.text = objective.createTime?.take(10).orEmpty().ifBlank { "—" }
            tvPeriodLabel.text = objective.periodLabel.orEmpty().ifBlank { "—" }
            tvCurrentProgress.text = "${objective.progress}%"

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
