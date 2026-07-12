package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OrgOkrAlignmentItem
import com.fuusy.hiddendanger.databinding.ItemOrgOkrAlignmentBinding

class OrgOkrAlignmentAdapter(
    private val onItemClick: (OrgOkrAlignmentItem) -> Unit
) : ListAdapter<OrgOkrAlignmentItem, OrgOkrAlignmentAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemOrgOkrAlignmentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOrgOkrAlignmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val density = holder.itemView.resources.displayMetrics.density
        holder.binding.apply {
            val indentPx = (item.depth * 16 * density).toInt()
            viewIndent.isVisible = item.depth > 0
            viewIndent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = indentPx
            }

            val dept = item.deptName?.takeIf { it.isNotBlank() }
            tvOwner.text = if (dept != null) "${item.ownerName} · $dept" else item.ownerName
            tvObjectiveTitle.text = item.objective.title

            val parentKr = item.objective.parentKr
            if (parentKr != null) {
                tvAlignInfo.isVisible = true
                val oTitle = parentKr.objective?.title.orEmpty()
                tvAlignInfo.text = "对齐 KR：${parentKr.title}" +
                    if (oTitle.isNotBlank()) "（$oTitle）" else ""
            } else {
                tvAlignInfo.isVisible = false
            }

            val (completed, total) = OkrPeriodHelper.krCompletionStats(item.objective)
            val progress = item.objective.progress
            tvProgress.text = buildString {
                append("进度 $progress%")
                if (total > 0) append(" · KR $completed/$total")
            }
            root.setOnClickListener { onItemClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OrgOkrAlignmentItem>() {
        override fun areItemsTheSame(oldItem: OrgOkrAlignmentItem, newItem: OrgOkrAlignmentItem) =
            oldItem.objective.id == newItem.objective.id

        override fun areContentsTheSame(oldItem: OrgOkrAlignmentItem, newItem: OrgOkrAlignmentItem) =
            oldItem == newItem
    }
}
