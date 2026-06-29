package com.fuusy.project.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.R
import com.fuusy.project.bean.ProjectItem

class ProjectItemAdapter : ListAdapter<ProjectItem, ProjectItemAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ProjectItem>() {
            override fun areItemsTheSame(oldItem: ProjectItem, newItem: ProjectItem): Boolean {
                return oldItem.item == newItem.item && oldItem.device == newItem.device
            }

            override fun areContentsTheSame(oldItem: ProjectItem, newItem: ProjectItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    private var onSelectionChanged: ((List<ProjectItem>) -> Unit)? = null

    fun setItems(newItems: List<ProjectItem>) {
        submitList(newItems)
    }

    fun setOnSelectionChangedListener(listener: (List<ProjectItem>) -> Unit) {
        onSelectionChanged = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_project_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: LinearLayout = itemView.findViewById(R.id.cardView)
        private val tvItemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val tvItem: TextView = itemView.findViewById(R.id.tv_item)
        private val tvProjectUnit: TextView = itemView.findViewById(R.id.tv_project_unit)
        private val tvCharge: TextView = itemView.findViewById(R.id.tv_charge)
        private val tvAddress: TextView = itemView.findViewById(R.id.tv_address)
        private val tvJobContent: TextView = itemView.findViewById(R.id.tv_job_content)

        fun bind(item: ProjectItem, position: Int) {
            // 使用清理后的项目名称（去除多余空格）
            tvItemName.text = item.cleanItemName
            tvItem.text = item.item
            tvProjectUnit.text = item.unit ?: ""
            tvCharge.text = item.charge
            tvAddress.text = item.address
            tvJobContent.text = item.cleanContent ?: ""

            // 设置选中状态，让 selector 自动处理背景
            cardView.isSelected = item.isSelected

            // 颜色定义
            val titleColor = if (item.isSelected) {
                ContextCompat.getColor(itemView.context, android.R.color.white)
            } else {
                ContextCompat.getColor(itemView.context, android.R.color.black)
            }
            val labelColor = if (item.isSelected) {
                ContextCompat.getColor(itemView.context, android.R.color.white)
            } else {
                0x99000000.toInt() // 灰色
            }
            val contentColor = if (item.isSelected) {
                ContextCompat.getColor(itemView.context, android.R.color.white)
            } else {
                0xE6000000.toInt() // 深黑色
            }

            // 主标题
            tvItemName.setTextColor(titleColor)
            // 内容
            tvItem.setTextColor(contentColor)
            tvProjectUnit.setTextColor(contentColor)
            tvCharge.setTextColor(contentColor)
            tvAddress.setTextColor(contentColor)
            tvJobContent.setTextColor(contentColor)

            // 标签（没有 id 的 TextView）
            val allTextViews = mutableListOf<TextView>()
            fun findTextViews(view: View) {
                if (view is TextView && view.id == View.NO_ID) {
                    allTextViews.add(view)
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        findTextViews(view.getChildAt(i))
                    }
                }
            }
            findTextViews(itemView)
            allTextViews.forEach { textView ->
                textView.setTextColor(labelColor)
            }

            itemView.setOnClickListener {
                // 单选逻辑：使用item和device两个字段判断
                val currentList = currentList.mapIndexed { idx, proj ->
                    if (idx == position) {
                        proj.copy(isSelected = true)
                    } else {
                        // 只有当item和device都不同时，才设为未选中
                        if (proj.item == getItem(position).item && proj.device == getItem(position).device) {
                            proj.copy(isSelected = false)
                        } else {
                            proj.copy(isSelected = false)
                        }
                    }
                }
                submitList(currentList)
                onSelectionChanged?.invoke(listOf(getItem(position)))
            }
        }
    }
}