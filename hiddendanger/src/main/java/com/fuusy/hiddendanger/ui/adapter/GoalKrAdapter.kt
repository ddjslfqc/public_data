package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemGoalKrBinding
import com.fuusy.hiddendanger.ui.model.GoalKrItem

class GoalKrAdapter(
    private val onItemClick: (GoalKrItem) -> Unit = {}
) : ListAdapter<GoalKrItem, GoalKrAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemGoalKrBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGoalKrBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvKrTitle.text = item.title
            root.setOnClickListener { onItemClick(item) }

            if (item.achieved) {
                tvAchieved.isVisible = true
                tvKrValue.isVisible = false
                tvKrApproval.isVisible = false
                tvKrPercent.isVisible = false
                flProgress.isVisible = false
                ivKrArrow.isVisible = true
            } else {
                tvAchieved.isVisible = false
                tvKrValue.isVisible = true
                tvKrValue.text = item.valueLabel
                val progressLabel = com.fuusy.hiddendanger.ui.model.KrProgressHelper
                    .progressStatusLabel(item)
                tvKrApproval.isVisible = when {
                    progressLabel != null -> true
                    !item.approvalLabel.isNullOrBlank() && item.approvalStatus != 1 -> true
                    else -> false
                }
                tvKrApproval.text = progressLabel ?: item.approvalLabel
                tvKrPercent.isVisible = true
                flProgress.isVisible = true
                tvKrPercent.text = "${item.progressPercent}%"
                ivKrArrow.isVisible = true
                viewKrProgress.post {
                    val trackWidth = flProgress.width
                    if (trackWidth > 0) {
                        viewKrProgress.layoutParams.width =
                            (trackWidth * item.progressPercent / 100f).toInt().coerceAtLeast(0)
                        viewKrProgress.requestLayout()
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GoalKrItem>() {
        override fun areItemsTheSame(oldItem: GoalKrItem, newItem: GoalKrItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: GoalKrItem, newItem: GoalKrItem) =
            oldItem == newItem
    }
}
