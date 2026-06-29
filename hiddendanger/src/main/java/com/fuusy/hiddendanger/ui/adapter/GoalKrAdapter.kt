package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemGoalKrBinding
import com.fuusy.hiddendanger.ui.model.GoalKrItem

class GoalKrAdapter : ListAdapter<GoalKrItem, GoalKrAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemGoalKrBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGoalKrBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvKrTitle.text = item.title
            if (item.achieved) {
                tvAchieved.isVisible = true
                tvKrValue.isVisible = false
                tvKrPercent.isVisible = false
                flProgress.isVisible = false
            } else {
                tvAchieved.isVisible = false
                tvKrValue.isVisible = true
                tvKrPercent.isVisible = true
                flProgress.isVisible = true
                tvKrValue.text = item.approvalLabel ?: item.valueLabel
                tvKrPercent.text = "${item.progressPercent}%"
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
            oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: GoalKrItem, newItem: GoalKrItem) =
            oldItem == newItem
    }
}
