package com.fuusy.project.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.R
import com.fuusy.project.databinding.ItemVideoListBinding
import com.fuusy.project.repo.VideoInfo
import com.fuusy.project.ui.FlvThumbnailLoader
import kotlinx.coroutines.CoroutineScope

class VideoListAdapter(
    private val scope: CoroutineScope,
    private val onItemClick: (VideoInfo) -> Unit,
    private val onActionClick: (VideoInfo) -> Unit
) : ListAdapter<VideoInfo, VideoListAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VideoViewHolder) {
        holder.cancelCover()
        super.onViewRecycled(holder)
    }

    inner class VideoViewHolder(
        private val binding: ItemVideoListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun cancelCover() {
            FlvThumbnailLoader.cancel(binding.ivCover)
        }

        fun bind(item: VideoInfo) {
            binding.tvCameraName.text = item.show_name ?: "未知摄像头"
            binding.tvCameraLocation.text = item.location.orEmpty()
            binding.tvCameraLocation.isVisible = !item.location.isNullOrBlank()
            binding.tvChannelBadge.text = item.channel_label ?: "通道1"

            when (item.type) {
                0 -> bindOnline(item)
                1 -> bindOffline()
                else -> bindAlarm()
            }

            bindCover(item)

            binding.root.setOnClickListener { onItemClick(item) }
            binding.tvActionBtn.setOnClickListener { onActionClick(item) }
        }

        private fun bindCover(item: VideoInfo) {
            val cacheKey = "${item.device_id.orEmpty()}_${item.channel_id.orEmpty()}"
                .ifBlank { item.videoPath.orEmpty() }
            if (item.type == 0 && !item.videoPath.isNullOrBlank()) {
                FlvThumbnailLoader.bindCover(
                    context = binding.root.context,
                    imageView = binding.ivCover,
                    cacheKey = cacheKey,
                    streamUrl = item.videoPath,
                    scope = scope
                )
            } else {
                FlvThumbnailLoader.cancel(binding.ivCover)
                binding.ivCover.setImageDrawable(null)
            }
        }

        private fun bindOnline(item: VideoInfo) {
            binding.tvLiveBadge.isVisible = true
            binding.ivAlarmCenter.isVisible = false

            val dangerLabel = item.danger_label
            binding.tvDangerBadge.isVisible = !dangerLabel.isNullOrBlank()
            binding.tvDangerBadge.text = dangerLabel.orEmpty()

            binding.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_online)
            binding.tvActionBtn.text = "查看"
            binding.tvActionBtn.setBackgroundResource(R.drawable.bg_action_btn)
            binding.tvActionBtn.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.color_ffffff)
            )
        }

        private fun bindOffline() {
            binding.tvLiveBadge.isVisible = false
            binding.tvDangerBadge.isVisible = false
            binding.ivAlarmCenter.isVisible = false
            binding.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline)
            binding.tvActionBtn.text = "重连"
            binding.tvActionBtn.setBackgroundResource(R.drawable.bg_action_btn_reconnect)
            binding.tvActionBtn.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.home_text_normal)
            )
        }

        private fun bindAlarm() {
            binding.tvLiveBadge.isVisible = false
            binding.tvDangerBadge.isVisible = false
            binding.ivAlarmCenter.isVisible = true
            binding.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_alarm)
            binding.tvActionBtn.text = "PTZ"
            binding.tvActionBtn.setBackgroundResource(R.drawable.bg_action_btn)
            binding.tvActionBtn.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.color_ffffff)
            )
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoInfo>() {
        override fun areItemsTheSame(oldItem: VideoInfo, newItem: VideoInfo) =
            oldItem.channel_id == newItem.channel_id

        override fun areContentsTheSame(oldItem: VideoInfo, newItem: VideoInfo) =
            oldItem == newItem
    }
}
