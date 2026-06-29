package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.hiddendanger.databinding.ItemDraftBoxBinding

class DraftBoxAdapter(
    private val onEdit: (WorkOrderItem) -> Unit,
    private val onDelete: (WorkOrderItem) -> Unit
) : ListAdapter<WorkOrderItem, DraftBoxAdapter.DraftViewHolder>(DIFF) {
    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WorkOrderItem>() {
            override fun areItemsTheSame(oldItem: WorkOrderItem, newItem: WorkOrderItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: WorkOrderItem, newItem: WorkOrderItem) =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DraftViewHolder {
        val binding =
            ItemDraftBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DraftViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DraftViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DraftViewHolder(private val binding: ItemDraftBoxBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WorkOrderItem) {
            binding.tvOrderId.text = item.id
            binding.tvSaveTime.text = item.submitTime
            binding.tvFindTime.text = item.submitTime
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
} 