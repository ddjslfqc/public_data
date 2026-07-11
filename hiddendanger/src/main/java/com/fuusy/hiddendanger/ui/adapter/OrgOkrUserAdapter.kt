package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OrgOkrUserSummary
import com.fuusy.hiddendanger.databinding.ItemOrgOkrUserBinding

class OrgOkrUserAdapter(
    private val onUserClick: (OrgOkrUserSummary) -> Unit
) : ListAdapter<OrgOkrUserSummary, OrgOkrUserAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemOrgOkrUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOrgOkrUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvUserName.text = item.ownerName
            tvDeptName.text = item.deptName?.takeIf { it.isNotBlank() } ?: "—"
            tvObjectiveCount.text = "${item.objectiveCount} 个目标"
            tvAvgProgress.text = "进度 ${item.avgProgress}%"
            root.setOnClickListener { onUserClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OrgOkrUserSummary>() {
        override fun areItemsTheSame(oldItem: OrgOkrUserSummary, newItem: OrgOkrUserSummary) =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: OrgOkrUserSummary, newItem: OrgOkrUserSummary) =
            oldItem == newItem
    }
}
