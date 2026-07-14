package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.databinding.ItemPendingKrBinding

class PendingKrAdapter(
    private val onApprove: (PendingKrItem, Boolean) -> Unit
) : ListAdapter<PendingKrItem, PendingKrAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemPendingKrBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPendingKrBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            // 与「进度更新」卡片对齐：顶部先看清是谁提交/指派的
            val ownerName = item.krOwnerName?.takeIf { it.isNotBlank() }
                ?: item.objectiveOwnerName?.takeIf { it.isNotBlank() }
            tvObjectiveTitle.text = buildString {
                if (!ownerName.isNullOrBlank()) {
                    append("提交人 ").append(ownerName)
                    item.krOwnerDeptName?.takeIf { it.isNotBlank() }?.let {
                        append(" · ").append(it)
                    }
                } else {
                    append(item.objectiveTitle.orEmpty())
                }
            }
            val roleLabel = item.approvalRoleLabel?.takeIf { it.isNotBlank() }
            val context = buildString {
                if (!roleLabel.isNullOrBlank()) {
                    append("您作为").append(roleLabel).append("审批")
                }
                item.objectiveTitle?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append("所属目标 ").append(it)
                }
                // 兼容旧接口：若顶部没拼出提交人，则继续展示后端 contextLine
                if (ownerName.isNullOrBlank()) {
                    item.contextLine?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append("\n")
                        append(it)
                    }
                } else {
                    item.objectiveOwnerName
                        ?.takeIf { it.isNotBlank() && it != ownerName }
                        ?.let {
                            if (isNotEmpty()) append(" · ")
                            append("目标创建人 ").append(it)
                        }
                }
            }
            tvContextLine.isVisible = context.isNotBlank()
            tvContextLine.text = context
            tvKrTitle.text = item.title
            val unit = item.unit.orEmpty()
            val target = item.targetValue?.toString().orEmpty()
            val weight = item.weight?.toString().orEmpty()
            tvKrDetail.text = buildString {
                if (target.isNotBlank()) append("目标值 $target$unit")
                if (weight.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("权重 $weight")
                }
            }
            btnApprove.setOnClickListener { onApprove(item, true) }
            btnReject.setOnClickListener { onApprove(item, false) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PendingKrItem>() {
        override fun areItemsTheSame(oldItem: PendingKrItem, newItem: PendingKrItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PendingKrItem, newItem: PendingKrItem) =
            oldItem == newItem
    }
}
