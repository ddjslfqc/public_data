package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fuusy.hiddendanger.databinding.ItemAlbumMediaBinding
import com.fuusy.hiddendanger.ui.album.AlbumMediaItem

class PersonalAlbumAdapter(
    private val onItemClick: (Int, List<AlbumMediaItem>) -> Unit
) : ListAdapter<AlbumMediaItem, PersonalAlbumAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemAlbumMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        holder.itemView.setOnClickListener {
            onItemClick(position, currentList)
        }
    }

    class ViewHolder(private val binding: ItemAlbumMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AlbumMediaItem) {
            if (item.type == AlbumMediaItem.MediaType.VIDEO) {
                Glide.with(binding.ivAlbum)
                    .load(item.path)
                    .frame(1000000)
                    .into(binding.ivAlbum)
                binding.ivVideo.visibility = android.view.View.VISIBLE
                binding.tvDuration.visibility = android.view.View.VISIBLE
                binding.tvDuration.text = formatDuration(item.duration)
            } else {
                Glide.with(binding.ivAlbum).load(item.path).into(binding.ivAlbum)
                binding.ivVideo.visibility = android.view.View.GONE
                binding.tvDuration.visibility = android.view.View.GONE
            }
            binding.tvAiTag.visibility = if (item.aiTag) android.view.View.VISIBLE else android.view.View.GONE
        }
        private fun formatDuration(duration: Long): String {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AlbumMediaItem>() {
        override fun areItemsTheSame(oldItem: AlbumMediaItem, newItem: AlbumMediaItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AlbumMediaItem, newItem: AlbumMediaItem): Boolean {
            return oldItem == newItem
        }
    }
} 