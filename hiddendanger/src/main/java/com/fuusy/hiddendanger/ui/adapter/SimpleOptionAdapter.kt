package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.R

class SimpleOptionAdapter(
    private val options: List<DynamicFormAdapter.OptionItem>,
    private val selectedValue: DynamicFormAdapter.OptionItem?, // 添加当前选中的值
    private val onItemClick: (DynamicFormAdapter.OptionItem) -> Unit
) : RecyclerView.Adapter<SimpleOptionAdapter.OptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selector_option, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(options[position])
    }

    override fun getItemCount(): Int = options.size

    inner class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvOption)
        private val checkIcon: ImageView = itemView.findViewById(R.id.ivCheck)

        fun bind(option: DynamicFormAdapter.OptionItem) {
            textView.text = option.label

            // 设置选中状态
            val isSelected = option == selectedValue
            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE

            // 设置文本颜色 - 使用兼容性方法
            val textColor = if (isSelected) {
                ContextCompat.getColor(itemView.context, R.color.red)
            } else {
                ContextCompat.getColor(itemView.context, R.color.text_primary)
            }
            textView.setTextColor(textColor)

            itemView.setOnClickListener {
                onItemClick(option)
            }
        }
    }
}