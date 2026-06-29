package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.hiddendanger.databinding.ItemHiddenDangerBinding

class HiddenDangerAdapter(
    private val onItemClick: (WorkOrderItem) -> Unit
) : ListAdapter<WorkOrderItem, HiddenDangerAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHiddenDangerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(private val binding: ItemHiddenDangerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: WorkOrderItem) {
            binding.item = item
            binding.executePendingBindings()
            
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<WorkOrderItem>() {
        override fun areItemsTheSame(oldItem: WorkOrderItem, newItem: WorkOrderItem): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: WorkOrderItem, newItem: WorkOrderItem): Boolean {
            return oldItem == newItem
        }
    }
}