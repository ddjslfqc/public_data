package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.databinding.ItemKrCommentBinding

class KrCommentAdapter(
    private val currentUserId: Long?,
    private val onDelete: (OkrKrComment) -> Unit
) : ListAdapter<OkrKrComment, KrCommentAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemKrCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKrCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvAuthor.text = item.displayName
            tvContent.text = item.content
            tvTime.text = formatTime(item.createTime)
            val dept = item.deptName?.takeIf { it.isNotBlank() }
            tvDept.isVisible = dept != null
            tvDept.text = dept
            val canDelete = currentUserId != null && currentUserId == item.userId
            btnDelete.isVisible = canDelete
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace('T', ' ').take(16)
    }

    class DiffCallback : DiffUtil.ItemCallback<OkrKrComment>() {
        override fun areItemsTheSame(oldItem: OkrKrComment, newItem: OkrKrComment) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: OkrKrComment, newItem: OkrKrComment) =
            oldItem == newItem
    }
}
