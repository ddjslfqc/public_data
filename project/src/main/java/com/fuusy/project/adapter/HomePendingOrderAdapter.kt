package com.fuusy.project.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.databinding.ItemHomePendingOrderBinding
import com.fuusy.project.ui.model.HomePendingOrderItem

class HomePendingOrderAdapter(
    private val onItemClick: (HomePendingOrderItem) -> Unit
) : ListAdapter<HomePendingOrderItem, HomePendingOrderAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemHomePendingOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHomePendingOrderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvTitle.text = item.title
            tvTime.text = item.time
            tvStatus.text = item.status
            root.setOnClickListener { onItemClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HomePendingOrderItem>() {
        override fun areItemsTheSame(oldItem: HomePendingOrderItem, newItem: HomePendingOrderItem) =
            oldItem.title == newItem.title && oldItem.time == newItem.time

        override fun areContentsTheSame(oldItem: HomePendingOrderItem, newItem: HomePendingOrderItem) =
            oldItem == newItem
    }
}
