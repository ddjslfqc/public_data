package com.fuusy.project.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.R
import com.fuusy.project.databinding.ItemUwbLogBinding
import com.fuusy.project.uwb.UwbDebugViewModel
import com.fuusy.project.uwb.UwbLogDirection
import com.fuusy.project.uwb.UwbLogEntry

class UwbLogAdapter : ListAdapter<UwbLogEntry, UwbLogAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUwbLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemUwbLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UwbLogEntry) {
            val context = binding.root.context
            binding.tvTime.text = UwbDebugViewModel.formatTime(item.timestamp)
            binding.tvTitle.text = item.title
            binding.tvDetail.text = item.detail
            val colorRes = when (item.direction) {
                UwbLogDirection.RX -> R.color.home_green
                UwbLogDirection.TX -> R.color.home_blue
                UwbLogDirection.INFO -> R.color.home_text_secondary
            }
            binding.tvTitle.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }

    private object Diff : DiffUtil.ItemCallback<UwbLogEntry>() {
        override fun areItemsTheSame(oldItem: UwbLogEntry, newItem: UwbLogEntry): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UwbLogEntry, newItem: UwbLogEntry): Boolean =
            oldItem == newItem
    }
}
