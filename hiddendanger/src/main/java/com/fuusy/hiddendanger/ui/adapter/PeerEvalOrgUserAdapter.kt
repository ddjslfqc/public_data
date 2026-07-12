package com.fuusy.hiddendanger.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.PeerEvalOrgUserItem
import com.fuusy.hiddendanger.databinding.ItemPeerEvalOrgUserBinding

class PeerEvalOrgUserAdapter(
    private val onUserClick: (PeerEvalOrgUserItem) -> Unit
) : ListAdapter<PeerEvalOrgUserItem, PeerEvalOrgUserAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemPeerEvalOrgUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPeerEvalOrgUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvUserName.text = item.displayName()
            tvDeptName.text = item.deptName?.takeIf { it.isNotBlank() } ?: "—"
            if (item.reviewPrepCompleted) {
                tvReviewStatus.text = "已复盘"
                tvReviewStatus.setTextColor(Color.parseColor("#00AA60"))
                tvReviewStatus.setBackgroundResource(R.drawable.bg_goal_status_processing)
            } else {
                tvReviewStatus.text = "未复盘"
                tvReviewStatus.setTextColor(Color.parseColor("#898FA0"))
                tvReviewStatus.setBackgroundResource(R.drawable.bg_goal_period_tab_normal)
            }
            tvEvalStats.text = buildString {
                append("合作人 ${item.collaboratorCount}")
                append(" · 待评 ${item.evalPendingCount}")
                append(" · 已评 ${item.evalCompletedCount}")
            }
            tvReceivedStats.text = item.receivedSummary()
            root.setOnClickListener { onUserClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PeerEvalOrgUserItem>() {
        override fun areItemsTheSame(oldItem: PeerEvalOrgUserItem, newItem: PeerEvalOrgUserItem) =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: PeerEvalOrgUserItem, newItem: PeerEvalOrgUserItem) =
            oldItem == newItem
    }
}
