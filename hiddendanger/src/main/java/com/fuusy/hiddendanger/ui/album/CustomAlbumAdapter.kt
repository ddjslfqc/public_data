package com.fuusy.hiddendanger.ui.album

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ItemAlbumMediaBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class CustomAlbumAdapter(
    private val getSelected: () -> Set<String>,
    private val mode: String,
    private val onItemClick: (AlbumMediaItem) -> Unit,
    private val pathToIdMapping: Map<String, String> = emptyMap()
) : ListAdapter<AlbumMediaItem, CustomAlbumAdapter.MediaViewHolder>(DiffCallback()) {

    private var selectedIds: Set<String> = emptySet()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    fun updateSelected(newSelected: Set<String>) {
        val oldSelected = selectedIds.toSet()
        val newSnapshot = newSelected.toSet()
        selectedIds = newSnapshot
        android.util.Log.d("CustomAlbumAdapter", "=== 更新选中状态 ===")
        android.util.Log.d("CustomAlbumAdapter", "旧选中状态: ${oldSelected.joinToString()}")
        android.util.Log.d("CustomAlbumAdapter", "新选中状态: ${newSnapshot.joinToString()}")
        android.util.Log.d("CustomAlbumAdapter", "当前列表项目数量: ${currentList.size}")
        android.util.Log.d("CustomAlbumAdapter", "当前列表所有ID: ${currentList.map { it.id }.joinToString()}")
        
        // 构建路径到ID的映射，用于备选匹配
        val pathToIdMap = currentList.associateBy { it.path }
        android.util.Log.d("CustomAlbumAdapter", "路径映射: ${pathToIdMap.keys.take(3).joinToString()}")
        
        // 检查是否有通过路径匹配的项目
        val matchedByPath = mutableSetOf<String>()
        newSnapshot.forEach { selectedId ->
            android.util.Log.d("CustomAlbumAdapter", "处理选中ID: $selectedId")
            
            // 如果直接ID匹配失败，尝试通过路径匹配
            if (!currentList.any { it.id == selectedId }) {
                android.util.Log.d("CustomAlbumAdapter", "直接ID匹配失败，尝试路径匹配")
                
                // 尝试从pathToIdMapping中找到对应的路径
                val path = pathToIdMapping.entries.find { it.value == selectedId }?.key
                android.util.Log.d("CustomAlbumAdapter", "从pathToIdMapping找到路径: $path")
                
                if (path != null && pathToIdMap.containsKey(path)) {
                    val matchedId = pathToIdMap[path]?.id
                    if (matchedId != null) {
                        matchedByPath.add(matchedId)
                        android.util.Log.d("CustomAlbumAdapter", "通过路径匹配: $selectedId -> $matchedId (路径: $path)")
                    }
                } else {
                    android.util.Log.d("CustomAlbumAdapter", "路径匹配失败，尝试ID格式转换")
                    
                    // 如果路径匹配也失败，尝试ID格式转换匹配
                    val convertedId = convertMediaIdFormat(selectedId)
                    android.util.Log.d("CustomAlbumAdapter", "ID格式转换结果: $selectedId -> $convertedId")
                    
                    if (convertedId != null && currentList.any { it.id == convertedId }) {
                        matchedByPath.add(convertedId)
                        android.util.Log.d("CustomAlbumAdapter", "通过ID格式转换匹配: $selectedId -> $convertedId")
                    } else {
                        android.util.Log.d("CustomAlbumAdapter", "ID格式转换匹配也失败")
                        android.util.Log.d("CustomAlbumAdapter", "转换后的ID $convertedId 不在当前列表中")
                        
                        // 最后的备选方案：遍历所有项目，尝试ID格式转换匹配
                        android.util.Log.d("CustomAlbumAdapter", "尝试遍历所有项目进行ID格式转换匹配")
                        var foundMatch = false
                        currentList.forEach { listItem ->
                            val itemConvertedId = convertMediaIdFormat(listItem.id)
                            android.util.Log.d("CustomAlbumAdapter", "检查项目: ${listItem.id} -> 转换后: $itemConvertedId")
                            if (itemConvertedId == selectedId) {
                                matchedByPath.add(listItem.id)
                                android.util.Log.d("CustomAlbumAdapter", "通过遍历匹配: $selectedId -> ${listItem.id}")
                                foundMatch = true
                                return@forEach
                            }
                        }
                        if (!foundMatch) {
                            android.util.Log.d("CustomAlbumAdapter", "遍历匹配也失败，没有找到匹配的项目")
                        }
                    }
                }
            } else {
                android.util.Log.d("CustomAlbumAdapter", "直接ID匹配成功")
            }
        }
        
        // 合并直接匹配和路径匹配的结果
        val finalSelected = newSnapshot + matchedByPath
        selectedIds = finalSelected
        
        // 仅刷新受影响的项（对称差集）
        val changed = (oldSelected + finalSelected) - (oldSelected.intersect(finalSelected))
        android.util.Log.d("CustomAlbumAdapter", "需要刷新的项目: ${changed.joinToString()}")
        if (changed.isEmpty()) {
            android.util.Log.d("CustomAlbumAdapter", "没有需要刷新的项目")
            return
        }
        changed.forEach { id ->
            val pos = currentList.indexOfFirst { it.id == id }
            android.util.Log.d("CustomAlbumAdapter", "项目ID: $id, 位置: $pos")
            if (pos >= 0) notifyItemChanged(pos)
        }
        android.util.Log.d("CustomAlbumAdapter", "=== 选中状态更新结束 ===")
    }
    
    /**
     * 转换MediaStore ID格式，处理video/media和file格式不一致的问题
     */
    private fun convertMediaIdFormat(originalId: String): String? {
        return try {
            // 处理 content://media/external/video/media/733 -> content://media/external/file/733
            if (originalId.contains("/video/media/")) {
                val id = originalId.substringAfterLast("/")
                "content://media/external/file/$id"
            }
            // 处理 content://media/external/file/733 -> content://media/external/video/media/733
            else if (originalId.contains("/file/")) {
                val id = originalId.substringAfterLast("/")
                "content://media/external/video/media/$id"
            }
            else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CustomAlbumAdapter", "ID格式转换失败: $originalId", e)
            null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding =
            ItemAlbumMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(binding: ItemAlbumMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val ivAlbum: ImageView = binding.ivAlbum
        private val tvAiTag: TextView = binding.tvAiTag
        private val ivVideo: ImageView = binding.ivVideo
        private val tvDuration: TextView = binding.tvDuration
        private val border: View = binding.ivCheck

        fun bind(item: AlbumMediaItem) {
            // 取消之前的请求，避免ANR
            Glide.with(ivAlbum.context).clear(ivAlbum)
            
            // 优化Glide加载，避免ANR
            Glide.with(ivAlbum.context)
                .load(item.path)
                .centerCrop()
                .override(200, 200) // 限制图片尺寸
                .placeholder(R.drawable.placeholder_image) // 添加占位图
                .error(R.drawable.error_image) // 添加错误图
                .into(ivAlbum)
            tvAiTag.visibility = if (item.aiTag) View.VISIBLE else View.GONE
            ivVideo.visibility =
                if (item.type == AlbumMediaItem.MediaType.VIDEO) View.VISIBLE else View.GONE
            tvDuration.visibility =
                if (item.type == AlbumMediaItem.MediaType.VIDEO) View.VISIBLE else View.GONE
            tvDuration.text =
                if (item.type == AlbumMediaItem.MediaType.VIDEO) formatDuration(item.duration) else ""
            if (mode == "select") {
                // 检查选中状态，支持ID格式转换
                val isSelected = isItemSelected(item.id)
                android.util.Log.d("CustomAlbumAdapter", "项目绑定: ID=${item.id}, 路径=${item.path}, 是否选中=$isSelected")
                border.visibility = if (isSelected) View.VISIBLE else View.GONE
            } else {
                border.visibility = View.GONE
            }
            itemView.setOnClickListener { onItemClick(item) }
        }

        /**
         * 检查项目是否被选中，支持ID格式转换
         */
        private fun isItemSelected(itemId: String): Boolean {
            // 直接检查
            if (selectedIds.contains(itemId)) {
                return true
            }
            
            // 尝试ID格式转换匹配
            val convertedId = convertMediaIdFormat(itemId)
            if (convertedId != null && selectedIds.contains(convertedId)) {
                android.util.Log.d("CustomAlbumAdapter", "通过ID格式转换匹配选中: $itemId -> $convertedId")
                return true
            }
            
            // 尝试反向转换匹配
            selectedIds.forEach { selectedId ->
                val reverseConverted = convertMediaIdFormat(selectedId)
                if (reverseConverted == itemId) {
                    android.util.Log.d("CustomAlbumAdapter", "通过反向ID格式转换匹配选中: $selectedId -> $itemId")
                    return true
                }
            }
            
            return false
        }

        private fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", min, sec)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AlbumMediaItem>() {
        override fun areItemsTheSame(oldItem: AlbumMediaItem, newItem: AlbumMediaItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AlbumMediaItem, newItem: AlbumMediaItem): Boolean {
            // 恢复基于数据类的结构比较，避免不必要的重绑引起抖动
            return oldItem == newItem
        }
    }
} 