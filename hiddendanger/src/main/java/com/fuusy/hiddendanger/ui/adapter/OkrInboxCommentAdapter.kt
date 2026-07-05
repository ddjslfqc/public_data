package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.databinding.ItemOkrInboxCommentBinding

class OkrInboxCommentAdapter :
    ListAdapter<OkrKrComment, OkrInboxCommentAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemOkrInboxCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOkrInboxCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvKrTitle.text = item.krTitle?.takeIf { it.isNotBlank() } ?: "KR #${item.krId}"
            tvAuthor.text = item.displayName
            tvContent.text = item.content
            tvTime.text = formatTime(item.createTime)
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
