package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemGoalKrBinding
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrProgressHelper

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

            tvAchieved.isVisible = item.achieved
            val progressLabel = KrProgressHelper.progressStatusLabel(item)
            val showKrApproval = !item.achieved &&
                !item.approvalLabel.isNullOrBlank() &&
                item.approvalStatus != 1 &&
                progressLabel == null
            tvKrApproval.isVisible = !item.achieved && (progressLabel != null || showKrApproval)
            val krApprovalText = when {
                progressLabel != null -> progressLabel
                item.approvalStatus == 0 -> item.pendingApproverHint ?: item.approvalLabel
                else -> item.approvalLabel
            }
            tvKrApproval.text = krApprovalText

            rowProgress.isVisible = true
            val showValue = shouldShowValueLabel(item)
            tvKrValue.isVisible = showValue
            tvKrValue.text = item.valueLabel
            val percent = if (item.achieved) 100 else item.progressPercent
            tvKrPercent.text = "$percent%"
            viewKrProgress.post {
                val trackWidth = flProgress.width
                if (trackWidth > 0) {
                    viewKrProgress.layoutParams.width =
                        (trackWidth * percent / 100f).toInt().coerceAtLeast(0)
                    viewKrProgress.requestLayout()
                }
            }
        }
    }

    /** 百分比类 KR 用进度条即可；其他单位（个、万元）在进度条左侧补数值 */
    private fun shouldShowValueLabel(item: GoalKrItem): Boolean {
        val unit = item.unit.orEmpty()
        return unit.isNotBlank() && unit != "%"
    }

    class DiffCallback : DiffUtil.ItemCallback<GoalKrItem>() {
        override fun areItemsTheSame(oldItem: GoalKrItem, newItem: GoalKrItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: GoalKrItem, newItem: GoalKrItem) =
            oldItem == newItem
    }
}
