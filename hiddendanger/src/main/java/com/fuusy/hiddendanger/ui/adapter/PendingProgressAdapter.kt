package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.PendingUpdateRecordItem
import com.fuusy.hiddendanger.databinding.ItemPendingKrBinding

class PendingProgressAdapter(
    private val onApprove: (PendingUpdateRecordItem, Boolean) -> Unit
) : ListAdapter<PendingUpdateRecordItem, PendingProgressAdapter.VH>(DiffCallback()) {

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
            tvObjectiveTitle.text = item.objectiveTitle.orEmpty()
            tvKrTitle.text = item.krTitle.orEmpty()
            val unit = item.unit.orEmpty()
            val oldValue = item.currentValue?.toString().orEmpty()
            val newValue = item.submittedValue?.toString().orEmpty()
            val target = item.targetValue?.toString().orEmpty()
            tvKrDetail.text = buildString {
                if (newValue.isNotBlank()) {
                    append("提交进度 $newValue$unit")
                    if (oldValue.isNotBlank()) append("（原 $oldValue$unit）")
                } else if (oldValue.isNotBlank()) {
                    append("当前进度 $oldValue$unit")
                }
                if (target.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("目标 $target$unit")
                }
                item.remark?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n")
                    append("说明：$it")
                }
            }
            btnApprove.setOnClickListener { onApprove(item, true) }
            btnReject.setOnClickListener { onApprove(item, false) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PendingUpdateRecordItem>() {
        override fun areItemsTheSame(
            oldItem: PendingUpdateRecordItem,
            newItem: PendingUpdateRecordItem
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: PendingUpdateRecordItem,
            newItem: PendingUpdateRecordItem
        ) = oldItem == newItem
    }
}
