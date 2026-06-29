package com.fuusy.project.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.R

class ProjectImageAdapter : RecyclerView.Adapter<ProjectImageAdapter.ImageViewHolder>() {
    
    private var imageList = mutableListOf<String>()
    
    fun setData(list: List<String>) {
        imageList.clear()
        imageList.addAll(list)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project_image, parent, false)
        return ImageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageList[position])
    }
    
    override fun getItemCount(): Int = imageList.size
    
    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.iv_project_image)
        
        fun bind(imagePath: String) {
            // 这里可以使用Glide或其他图片加载库加载图片
            // Glide.with(itemView.context).load(imagePath).into(imageView)
            
            // 临时设置占位图
            imageView.setImageResource(R.mipmap.bg1)
        }
    }
}