package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.OkrPeerUser
import com.fuusy.hiddendanger.databinding.ItemPeerCollaboratorPickBinding

class PeerCollaboratorPickAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<OkrPeerUser, PeerCollaboratorPickAdapter.VH>(DiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()

    fun selectedUsers(): List<OkrPeerUser> =
        currentList.filter { it.userId in selectedIds }

    fun clearSelection() {
        selectedIds.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPeerCollaboratorPickBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemPeerCollaboratorPickBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: OkrPeerUser) {
            val selected = user.userId in selectedIds
            binding.tvName.text = user.displayName
            val dept = user.deptName?.takeIf { it.isNotBlank() }
            binding.tvDept.isVisible = dept != null
            binding.tvDept.text = dept
            binding.ivCheck.isVisible = selected
            binding.viewCheckPlaceholder.isVisible = !selected
            binding.tvName.setTextColor(if (selected) 0xFF1365EC.toInt() else 0xFF000000.toInt())
            binding.root.setOnClickListener {
                if (user.userId in selectedIds) {
                    selectedIds.remove(user.userId)
                } else {
                    selectedIds.add(user.userId)
                }
                notifyItemChanged(bindingAdapterPosition)
                onSelectionChanged(selectedIds.size)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OkrPeerUser>() {
        override fun areItemsTheSame(oldItem: OkrPeerUser, newItem: OkrPeerUser) =
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: OkrPeerUser, newItem: OkrPeerUser) =
            oldItem == newItem
    }
}
