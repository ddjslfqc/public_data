package com.fuusy.project.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fuusy.project.R

class HistoryImageAdapter(
    private var data: List<HistoryImageItem>,
    private val onDownloadClick: (HistoryImageItem) -> Unit,
    private val onItemClick: (HistoryImageItem, Int) -> Unit
) : RecyclerView.Adapter<HistoryImageAdapter.ViewHolder>() {

    fun updateData(newData: List<HistoryImageItem>) {
        data = newData
        notifyDataSetChanged()
    }

    fun updateDownloadProgress(url: String, progress: Int, downloadedSize: Long, totalSize: Long, isDownloading: Boolean) {
        val index = data.indexOfFirst { it.fileUrl == url }
        if (index != -1) {
            val item = data[index]
            val updatedItem = item.copy(
                downloadProgress = progress,
                downloadedSize = downloadedSize,
                totalSize = totalSize,
                isDownloading = isDownloading
            )
            data = data.toMutableList().apply { set(index, updatedItem) }
            notifyItemChanged(index)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivThumb)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        val ivDownload: ImageView = view.findViewById(R.id.ivDownload)
        val ivPlayIcon: ImageView = view.findViewById(R.id.play_icon)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_image, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.tvFileName.text = item.fileName
        
        // 显示文件大小或下载进度
        if (item.isDownloading) {
            val progressText = if (item.totalSize > 0) {
                val downloadedMB = item.downloadedSize / (1024 * 1024)
                val totalMB = item.totalSize / (1024 * 1024)
                "${item.downloadProgress}% (${downloadedMB}MB/${totalMB}MB)"
            } else {
                "${item.downloadProgress}%"
            }
            holder.tvFileSize.text = progressText
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = item.downloadProgress
            holder.ivDownload.visibility = View.GONE
        } else {
            holder.tvFileSize.text = item.fileSize
            holder.progressBar.visibility = View.GONE
            holder.ivDownload.visibility = View.VISIBLE
        }
        
        // 用 Glide 加载图片或视频首帧
        Glide.with(holder.ivThumb.context)
            .load(item.fileUrl)
            .placeholder(com.fuusy.common.R.mipmap.img_placeholder)
            .centerCrop()
            .into(holder.ivThumb)
        holder.ivPlayIcon.visibility = if (item.isVideo) View.VISIBLE else View.INVISIBLE
        holder.ivDownload.setOnClickListener { onDownloadClick(item) }
        holder.itemView.setOnClickListener { onItemClick(item, position) }
    }
} 