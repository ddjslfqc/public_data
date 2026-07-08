package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.PeerEvalTask
import com.fuusy.hiddendanger.databinding.ItemPeerEvalTaskBinding

class PeerEvalTaskAdapter(
    private val onClick: (PeerEvalTask) -> Unit
) : ListAdapter<PeerEvalTask, PeerEvalTaskAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemPeerEvalTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPeerEvalTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvName.text = item.targetUserName?.takeIf { it.isNotBlank() } ?: "用户${item.targetUserId}"
            val dept = item.deptName?.takeIf { it.isNotBlank() }
            tvDept.isVisible = dept != null
            tvDept.text = dept
            if (item.isDone) {
                tvStatus.text = "已评价 · 详情"
                tvStatus.setBackgroundResource(com.fuusy.hiddendanger.R.drawable.bg_status_done)
                tvStatus.setTextColor(0xFF00AA60.toInt())
                root.alpha = 0.72f
            } else {
                tvStatus.text = "待评价"
                tvStatus.setBackgroundResource(com.fuusy.hiddendanger.R.drawable.bg_archive_tag_warn)
                tvStatus.setTextColor(0xFFEA9300.toInt())
                root.alpha = 1f
            }
            root.setOnClickListener { onClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PeerEvalTask>() {
        override fun areItemsTheSame(oldItem: PeerEvalTask, newItem: PeerEvalTask) =
            oldItem.targetUserId == newItem.targetUserId

        override fun areContentsTheSame(oldItem: PeerEvalTask, newItem: PeerEvalTask) =
            oldItem == newItem
    }
}
