package com.fuusy.hiddendanger.ui.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.data.MyReportType
import com.fuusy.hiddendanger.data.ReportArchiveItem
import com.fuusy.hiddendanger.databinding.ItemMyReportBinding

class MyReportAdapter(
    private val onClick: (ReportArchiveItem) -> Unit
) : ListAdapter<ReportArchiveItem, MyReportAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMyReportBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemMyReportBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReportArchiveItem) {
            val type = MyReportType.fromApi(item.type)
            binding.tvPeriod.text = item.period.orEmpty().ifBlank { "—" }
            binding.tvType.text = type?.label ?: "报告"
            val (fg, bg) = when (type) {
                MyReportType.DAILY -> Color.parseColor("#1465EB") to Color.parseColor("#E8F0FE")
                MyReportType.WEEKLY -> Color.parseColor("#0F766E") to Color.parseColor("#DDF4F0")
                MyReportType.MONTHLY -> Color.parseColor("#7C3AED") to Color.parseColor("#EDE9FE")
                null -> Color.parseColor("#686D79") to Color.parseColor("#EEF1F8")
            }
            binding.tvType.setTextColor(fg)
            binding.tvType.background = GradientDrawable().apply {
                cornerRadius = 20f * binding.root.resources.displayMetrics.density
                setColor(bg)
            }
            binding.tvSummary.text = item.summary.orEmpty().ifBlank { "（无摘要）" }
            val time = item.submittedAt.orEmpty().ifBlank { "—" }
            binding.tvTime.text = if (time == "—") time else "提交于 $time"
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReportArchiveItem>() {
            override fun areItemsTheSame(
                oldItem: ReportArchiveItem,
                newItem: ReportArchiveItem
            ): Boolean = oldItem.id == newItem.id && oldItem.type == newItem.type

            override fun areContentsTheSame(
                oldItem: ReportArchiveItem,
                newItem: ReportArchiveItem
            ): Boolean = oldItem == newItem
        }
    }
}
