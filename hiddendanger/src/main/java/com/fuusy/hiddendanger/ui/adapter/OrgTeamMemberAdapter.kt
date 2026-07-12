package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OrgTeamMemberItem
import com.fuusy.hiddendanger.databinding.ItemOrgTeamMemberBinding

class OrgTeamMemberAdapter(
    private val onMemberClick: (OrgTeamMemberItem) -> Unit
) : ListAdapter<OrgTeamMemberItem, OrgTeamMemberAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemOrgTeamMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOrgTeamMemberBinding.inflate(
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
            root.setOnClickListener { onMemberClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OrgTeamMemberItem>() {
        override fun areItemsTheSame(oldItem: OrgTeamMemberItem, newItem: OrgTeamMemberItem) =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: OrgTeamMemberItem, newItem: OrgTeamMemberItem) =
            oldItem == newItem
    }
}
