package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrPeerUser
import com.fuusy.hiddendanger.databinding.ItemPeerCollaboratorBinding

class PeerCollaboratorAdapter(
    private val onRemove: (OkrPeerUser) -> Unit
) : ListAdapter<OkrPeerUser, PeerCollaboratorAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemPeerCollaboratorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPeerCollaboratorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvName.text = item.displayName
            btnRemove.setOnClickListener { onRemove(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OkrPeerUser>() {
        override fun areItemsTheSame(oldItem: OkrPeerUser, newItem: OkrPeerUser) =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: OkrPeerUser, newItem: OkrPeerUser) =
            oldItem == newItem
    }
}
