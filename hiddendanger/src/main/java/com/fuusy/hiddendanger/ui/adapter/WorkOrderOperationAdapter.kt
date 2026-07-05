package com.fuusy.hiddendanger.ui.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.common.data.WorkOrderOperationRecord
import com.fuusy.hiddendanger.databinding.ItemWorkOrderOperationBinding

class WorkOrderOperationAdapter :
    ListAdapter<WorkOrderOperationRecord, WorkOrderOperationAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemWorkOrderOperationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemWorkOrderOperationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvType.text = item.typeLabel
            val (bg, fg) = if (item.isReject) {
                Color.parseColor("#33EB1919") to Color.parseColor("#EB1919")
            } else {
                Color.parseColor("#331365EC") to Color.parseColor("#1365EC")
            }
            tvType.background = GradientDrawable().apply {
                cornerRadius = 4f * root.resources.displayMetrics.density
                setColor(bg)
            }
            tvType.setTextColor(fg)
            tvOperator.text = item.operatorName.ifBlank { "—" }
            tvTime.text = item.operationTime
            tvContent.text = item.content
            tvContent.isVisible = item.content.isNotBlank()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WorkOrderOperationRecord>() {
        override fun areItemsTheSame(
            oldItem: WorkOrderOperationRecord,
            newItem: WorkOrderOperationRecord
        ) = oldItem.id == newItem.id && oldItem.operationTime == newItem.operationTime

        override fun areContentsTheSame(
            oldItem: WorkOrderOperationRecord,
            newItem: WorkOrderOperationRecord
        ) = oldItem == newItem
    }
}
