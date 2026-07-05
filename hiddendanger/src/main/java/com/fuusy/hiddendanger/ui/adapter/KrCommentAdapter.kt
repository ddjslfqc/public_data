package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.databinding.ItemKrCommentBinding
import com.fuusy.hiddendanger.databinding.ItemKrCommentInlineBinding

class KrCommentAdapter(
    private val currentUserId: Long?,
    private val inline: Boolean = false,
    private val onDelete: (OkrKrComment) -> Unit
) : ListAdapter<OkrKrComment, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int = if (inline) VIEW_INLINE else VIEW_CARD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_INLINE -> InlineVH(
                ItemKrCommentInlineBinding.inflate(inflater, parent, false)
            )
            else -> CardVH(
                ItemKrCommentBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is CardVH -> bindCard(holder.binding, item)
            is InlineVH -> bindInline(holder.binding, item, position == itemCount - 1)
        }
    }

    private fun bindCard(binding: ItemKrCommentBinding, item: OkrKrComment) {
        binding.apply {
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

    private fun bindInline(binding: ItemKrCommentInlineBinding, item: OkrKrComment, isLast: Boolean) {
        binding.apply {
            tvAuthor.text = item.displayName
            tvContent.text = item.content
            tvTime.text = formatTime(item.createTime)
            val dept = item.deptName?.takeIf { it.isNotBlank() }
            tvDept.isVisible = dept != null
            tvDept.text = dept
            divider.isVisible = !isLast
            val canDelete = currentUserId != null && currentUserId == item.userId
            btnDelete.isVisible = canDelete
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace('T', ' ').take(16)
    }

    class CardVH(val binding: ItemKrCommentBinding) : RecyclerView.ViewHolder(binding.root)

    class InlineVH(val binding: ItemKrCommentInlineBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<OkrKrComment>() {
        override fun areItemsTheSame(oldItem: OkrKrComment, newItem: OkrKrComment) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: OkrKrComment, newItem: OkrKrComment) =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_CARD = 0
        private const val VIEW_INLINE = 1
    }
}
