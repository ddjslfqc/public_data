package com.fuusy.hiddendanger.ui.adapter

import android.widget.TextView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.OkrUpdateRecordItem
import com.fuusy.hiddendanger.databinding.ItemKrUpdateRecordBinding

class KrUpdateRecordAdapter :
    ListAdapter<OkrUpdateRecordItem, KrUpdateRecordAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemKrUpdateRecordBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKrUpdateRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvContent.text = item.content?.takeIf { it.isNotBlank() } ?: "进度更新"
            tvTime.text = formatTime(item.createTime)
            tvStatus.text = statusLabel(item.status)
            bindStatusStyle(tvStatus, item.status)
            val value = item.currentValue
            tvValue.text = if (value != null) "提交进度：$value" else ""
        }
    }

    private fun statusLabel(status: Int?): String = when (status) {
        0 -> "待审核"
        1 -> "已通过"
        2 -> "已拒绝"
        else -> "—"
    }

    /** 与 OKR 模块通用标签色一致：待审 #EA9300、通过 #00AA60、拒绝 #D6413F */
    private fun bindStatusStyle(textView: TextView, status: Int?) {
        val (bgRes, textColor) = when (status) {
            0 -> R.drawable.bg_archive_tag_warn to 0xFFEA9300.toInt()
            1 -> R.drawable.bg_goal_status_processing to 0xFF00AA60.toInt()
            2 -> R.drawable.bg_status_reject to 0xFFD6413F.toInt()
            else -> R.drawable.bg_archive_tag_warn to 0xFF686D79.toInt()
        }
        textView.setBackgroundResource(bgRes)
        textView.setTextColor(textColor)
    }

    private fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace('T', ' ').take(16)
    }

    class DiffCallback : DiffUtil.ItemCallback<OkrUpdateRecordItem>() {
        override fun areItemsTheSame(oldItem: OkrUpdateRecordItem, newItem: OkrUpdateRecordItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: OkrUpdateRecordItem, newItem: OkrUpdateRecordItem) =
            oldItem == newItem
    }
}
