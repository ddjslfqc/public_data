package com.fuusy.project.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.databinding.ItemVideoGridBinding
import com.yourpackage.vlc.VlcPlayerHelper
import com.fuusy.project.ui.model.VideoChannelInfo
import android.util.Log

class ProjectVideoAdapter(
    private var videoList: List<VideoChannelInfo>,
    private val onVideoClick: (VideoChannelInfo) -> Unit
) : RecyclerView.Adapter<ProjectVideoAdapter.VideoVH>() {

    class VideoVH(val binding: ItemVideoGridBinding) : RecyclerView.ViewHolder(binding.root) {
        var vlcPlayerHelper: VlcPlayerHelper? = null
        var currentUrl: String? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoVH {
        val binding = ItemVideoGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        val spanCount = 2 // GridLayoutManager(2)
        val totalSpacing = (spanCount + 1) * 2 // 1dp margin * (spanCount+1)
        val itemWidth = (screenWidth - totalSpacing) / spanCount
        val itemHeight = (itemWidth * 9f / 16f).toInt()
        binding.root.layoutParams = RecyclerView.LayoutParams(itemWidth, itemHeight)
        return VideoVH(binding)
    }

    override fun onBindViewHolder(holder: VideoVH, position: Int) {
        val item = videoList[position]
        holder.binding.ivPlaceholder.visibility = android.view.View.VISIBLE

        // 先移除旧的监听，防止多次触发
        val oldListener = holder.binding.vlcVideoLayout.getTag(com.fuusy.project.R.id.vlc_layout_listener) as? android.view.ViewTreeObserver.OnGlobalLayoutListener
        if (oldListener != null) {
            holder.binding.vlcVideoLayout.viewTreeObserver.removeOnGlobalLayoutListener(oldListener)
        }

        val layoutListener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val width = holder.binding.vlcVideoLayout.width
                val height = holder.binding.vlcVideoLayout.height
                if (width > 0 && height > 0) {
                    holder.binding.vlcVideoLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    holder.vlcPlayerHelper?.let { helper ->
                        // 将释放操作放到子线程
                        Thread {
                            try {
                                helper.release()
                            } catch (e: Exception) {
                                Log.e("VLCPlayer", "子线程释放VlcPlayerHelper异常: ${e.message}")
                            }
                        }.start()
                    }
                    holder.vlcPlayerHelper = VlcPlayerHelper(holder.itemView.context, holder.binding.vlcVideoLayout)
                    holder.vlcPlayerHelper?.setOnPlayingListener {
                        holder.binding.ivPlaceholder.post {
                            holder.binding.ivPlaceholder.visibility = android.view.View.GONE
                        }
                    }
                    holder.vlcPlayerHelper?.play(item.streamUrl)
                    holder.currentUrl = item.streamUrl
                }
            }
        }
        holder.binding.vlcVideoLayout.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        holder.binding.vlcVideoLayout.setTag(com.fuusy.project.R.id.vlc_layout_listener, layoutListener)

        holder.itemView.setOnClickListener { onVideoClick(item) }
    }

    override fun onViewRecycled(holder: VideoVH) {
        super.onViewRecycled(holder)
        holder.vlcPlayerHelper?.release()
        holder.currentUrl = null
    }

    override fun getItemCount() = videoList.size

    fun updateData(newList: List<VideoChannelInfo>) {
        videoList = newList
        notifyDataSetChanged()
    }
} 