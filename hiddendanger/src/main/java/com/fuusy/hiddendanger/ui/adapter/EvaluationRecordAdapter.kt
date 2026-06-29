package com.fuusy.hiddendanger.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemEvaluationRecordBinding
import com.fuusy.hiddendanger.ui.model.EvaluationRecordItem

class EvaluationRecordAdapter(
    private val onWorkOrderClick: (EvaluationRecordItem) -> Unit
) : ListAdapter<EvaluationRecordItem, EvaluationRecordAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemEvaluationRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEvaluationRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvReviewer.text = item.reviewerName
            tvAvatar.text = item.reviewerInitial
            tvAvatar.background = circleDrawable(item.avatarColor)
            tvDepartment.text = item.department
            tvDate.text = item.date
            tvRating.text = buildStars(item.rating)
            tvContent.text = item.content
            tvWorkOrder.text = item.workOrderTitle

            val hasWorkOrder = item.workOrderTitle.isNotBlank()
            rowWorkOrder.isVisible = hasWorkOrder
            if (hasWorkOrder) {
                rowWorkOrder.setOnClickListener {
                    if (item.workOrder != null) onWorkOrderClick(item)
                }
            } else {
                rowWorkOrder.setOnClickListener(null)
            }
        }
    }

    private fun circleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun buildStars(rating: Float): String {
        val full = rating.toInt().coerceIn(0, 5)
        return "★".repeat(full) + "☆".repeat(5 - full)
    }

    class DiffCallback : DiffUtil.ItemCallback<EvaluationRecordItem>() {
        override fun areItemsTheSame(oldItem: EvaluationRecordItem, newItem: EvaluationRecordItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EvaluationRecordItem, newItem: EvaluationRecordItem) =
            oldItem == newItem
    }
}
