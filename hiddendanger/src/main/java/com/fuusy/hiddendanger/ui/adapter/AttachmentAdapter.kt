package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ItemAttachmentBinding
import android.util.Log

class AttachmentAdapter(
    private val onDeleteClick: (String) -> Unit,
    private val onAddClick: () -> Unit,
    private val onItemClick: (AttachmentItem.Media, Int) -> Unit // 添加点击事件回调
) : ListAdapter<AttachmentItem, RecyclerView.ViewHolder>(AttachmentDiffCallback()) {

    companion object {
        private const val TYPE_MEDIA = 0
        private const val TYPE_ADD_BUTTON = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AttachmentItem.Media -> TYPE_MEDIA
            is AttachmentItem.AddButton -> TYPE_ADD_BUTTON
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_MEDIA -> {
                val binding = ItemAttachmentBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                MediaViewHolder(binding)
            }

            TYPE_ADD_BUTTON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_add_attachment, parent, false)
                AddButtonViewHolder(view)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MediaViewHolder -> {
                val item = getItem(position) as AttachmentItem.Media
                Log.d("AttachmentAdapter", "绑定媒体项 $position: ${item.path}")
                holder.bind(item)
            }
            is AddButtonViewHolder -> holder.bind()
        }
    }

    override fun submitList(list: List<AttachmentItem>?) {
        Log.d("AttachmentAdapter", "提交列表: ${list?.size} 项")
        list?.forEachIndexed { index, item ->
            when (item) {
                is AttachmentItem.Media -> Log.d("AttachmentAdapter", "列表项 $index: ${item.path}")
                is AttachmentItem.AddButton -> Log.d("AttachmentAdapter", "列表项 $index: AddButton")
            }
        }
        super.submitList(list)
    }

    inner class MediaViewHolder(
        private val binding: ItemAttachmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AttachmentItem.Media) {
            // 使用 Glide 加载图片/视频缩略图
            Glide.with(binding.ivAttachment.context).load(item.path)
                .error(com.fuusy.common.R.mipmap.img_placeholder).into(binding.ivAttachment)

            // 显示/隐藏视频播放图标
            binding.ivPlay.visibility =
                if (item.type == AttachmentItem.MediaType.VIDEO) View.VISIBLE else View.GONE
            Log.d(
                "AttachmentAdapter",
                "Item: ${item.path}, Type: ${item.type}, Play Button Visibility: ${binding.ivPlay.visibility}"
            )
            // 删除按钮点击事件
            binding.btnDelete.setOnClickListener {
                onDeleteClick(item.path)
            }

            // 整个item的点击事件 (用于启动PictureSelector预览)
            binding.root.setOnClickListener {
                onItemClick(item, adapterPosition)
            }
        }
    }

    inner class AddButtonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener { onAddClick() }
        }

        fun bind() {
        }
    }

    private class AttachmentDiffCallback : DiffUtil.ItemCallback<AttachmentItem>() {
        override fun areItemsTheSame(oldItem: AttachmentItem, newItem: AttachmentItem): Boolean {
            return when {
                oldItem is AttachmentItem.Media && newItem is AttachmentItem.Media -> {
                oldItem.path == newItem.path
                }
                oldItem is AttachmentItem.AddButton && newItem is AttachmentItem.AddButton -> {
                    true
                }
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: AttachmentItem, newItem: AttachmentItem): Boolean {
            return when {
                oldItem is AttachmentItem.Media && newItem is AttachmentItem.Media -> {
                    oldItem.path == newItem.path && oldItem.type == newItem.type
                }
                oldItem is AttachmentItem.AddButton && newItem is AttachmentItem.AddButton -> {
                    true
                }
                else -> false
            }
        }
    }

}