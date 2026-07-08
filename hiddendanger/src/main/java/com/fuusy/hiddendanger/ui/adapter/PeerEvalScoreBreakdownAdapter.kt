package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.PeerEvalScoreBreakdownItem
import com.fuusy.hiddendanger.databinding.ItemPeerEvalScoreBreakdownBinding

class PeerEvalScoreBreakdownAdapter :
    ListAdapter<PeerEvalScoreBreakdownItem, PeerEvalScoreBreakdownAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemPeerEvalScoreBreakdownBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPeerEvalScoreBreakdownBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvTitle.text = item.itemTitle.orEmpty()
            tvScore.text = "%.1f 分".format(item.averageScore)
            progressScore.progress = (item.averageScore * 100).toInt().coerceIn(0, 500)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PeerEvalScoreBreakdownItem>() {
        override fun areItemsTheSame(oldItem: PeerEvalScoreBreakdownItem, newItem: PeerEvalScoreBreakdownItem) =
            oldItem.itemId == newItem.itemId

        override fun areContentsTheSame(oldItem: PeerEvalScoreBreakdownItem, newItem: PeerEvalScoreBreakdownItem) =
            oldItem == newItem
    }
}
