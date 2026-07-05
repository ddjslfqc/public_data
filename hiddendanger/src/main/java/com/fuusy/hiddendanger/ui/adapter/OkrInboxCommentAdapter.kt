package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.databinding.ItemOkrInboxCommentBinding
import com.fuusy.hiddendanger.ui.model.OkrInboxGroup

class OkrInboxCommentAdapter(
    private val allowReply: () -> Boolean,
    private val onToggleExpand: (OkrInboxGroup) -> Unit,
    private val onReply: (krId: Long, content: String) -> Unit,
    private val onViewDetail: (OkrInboxGroup) -> Unit
) : ListAdapter<OkrInboxGroup, OkrInboxCommentAdapter.VH>(DiffCallback()) {

    private var expandedKrId: Long? = null
    private var threads: Map<Long, List<OkrKrComment>> = emptyMap()
    private var loadingKrIds: Set<Long> = emptySet()

    class VH(val binding: ItemOkrInboxCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        val threadAdapter = KrCommentAdapter(null, inline = true) { }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOkrInboxCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding).also { holder ->
            holder.binding.rvThread.apply {
                layoutManager = LinearLayoutManager(parent.context)
                adapter = holder.threadAdapter
                isNestedScrollingEnabled = false
            }
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val group = getItem(position)
        val krId = group.krId
        val expanded = expandedKrId == krId
        val loading = loadingKrIds.contains(krId)

        holder.binding.apply {
            tvKrTitle.text = group.krTitle?.takeIf { it.isNotBlank() } ?: "KR #$krId"
            tvAuthor.text = group.preview.displayName
            tvContent.text = group.preview.content
            tvTime.text = formatTime(group.preview.createTime)
            tvInboxCount.isVisible = !expanded && group.inboxCount > 1
            tvInboxCount.text = "共 ${group.inboxCount} 条"
            ivExpand.rotation = if (expanded) 90f else 0f

            layoutPreview.isVisible = !expanded
            sectionExpanded.isVisible = expanded

            tvKrTitle.setOnClickListener { onViewDetail(group) }
            btnViewKrDetail.setOnClickListener { onViewDetail(group) }
            ivExpand.setOnClickListener { onToggleExpand(group) }
            layoutPreview.setOnClickListener { onToggleExpand(group) }

            if (expanded) {
                val thread = threads[krId].orEmpty()
                progressThread.isVisible = loading
                rvThread.isVisible = !loading && thread.isNotEmpty()
                tvThreadEmpty.isVisible = !loading && thread.isEmpty()
                holder.threadAdapter.submitList(thread)

                layoutReply.isVisible = allowReply()
                btnSendReply.isEnabled = !loading
                btnSendReply.setOnClickListener { sendReply(group, etReply) }
                etReply.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEND) {
                        sendReply(group, etReply)
                        true
                    } else {
                        false
                    }
                }
            } else {
                layoutReply.isVisible = false
            }
        }
    }

    private fun ItemOkrInboxCommentBinding.sendReply(group: OkrInboxGroup, etReply: android.widget.EditText) {
        val text = etReply.text?.toString().orEmpty()
        if (text.isBlank()) return
        onReply(group.krId, text)
        etReply.text?.clear()
    }

    fun updateExpandState(
        expandedKrId: Long?,
        threads: Map<Long, List<OkrKrComment>>,
        loadingKrIds: Set<Long>
    ) {
        this.expandedKrId = expandedKrId
        this.threads = threads
        this.loadingKrIds = loadingKrIds
        notifyDataSetChanged()
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace('T', ' ').take(16)
    }

    class DiffCallback : DiffUtil.ItemCallback<OkrInboxGroup>() {
        override fun areItemsTheSame(oldItem: OkrInboxGroup, newItem: OkrInboxGroup) =
            oldItem.krId == newItem.krId

        override fun areContentsTheSame(oldItem: OkrInboxGroup, newItem: OkrInboxGroup) =
            oldItem == newItem
    }
}
