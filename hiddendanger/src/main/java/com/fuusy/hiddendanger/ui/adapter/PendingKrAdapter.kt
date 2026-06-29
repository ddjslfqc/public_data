package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
            tvObjectiveTitle.text = item.objectiveTitle.orEmpty()
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
