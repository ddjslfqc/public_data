package com.fuusy.hiddendanger.ui.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemArchiveEvaluationBinding
import com.fuusy.hiddendanger.ui.model.ArchiveEvaluationItem

class ArchiveEvaluationAdapter(
    private val onItemClick: (ArchiveEvaluationItem) -> Unit = {}
) : ListAdapter<ArchiveEvaluationItem, ArchiveEvaluationAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemArchiveEvaluationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemArchiveEvaluationBinding.inflate(
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
            tvRating.text = buildStars(item.rating)
            tvContent.text = item.content
            tvMeta.text = item.meta

            if (item.tag.isNullOrBlank()) {
                tvTag.isVisible = false
            } else {
                tvTag.isVisible = true
                tvTag.text = item.tag
                tvTag.setTextColor(item.tagTextColor)
                tvTag.setBackgroundResource(item.tagBackgroundRes)
            }
            root.setOnClickListener {
                if (item.workOrderId != null) onItemClick(item)
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

    class DiffCallback : DiffUtil.ItemCallback<ArchiveEvaluationItem>() {
        override fun areItemsTheSame(oldItem: ArchiveEvaluationItem, newItem: ArchiveEvaluationItem) =
            oldItem.meta == newItem.meta && oldItem.reviewerName == newItem.reviewerName

        override fun areContentsTheSame(oldItem: ArchiveEvaluationItem, newItem: ArchiveEvaluationItem) =
            oldItem == newItem
    }
}
