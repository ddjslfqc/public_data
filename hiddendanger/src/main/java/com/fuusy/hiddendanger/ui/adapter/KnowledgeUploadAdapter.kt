package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.KnowledgeUploadItem
import com.fuusy.hiddendanger.data.KnowledgeUploadStatus

class KnowledgeUploadAdapter : ListAdapter<KnowledgeUploadItem, KnowledgeUploadAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_knowledge_upload, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.tv_name)
        private val status: TextView = view.findViewById(R.id.tv_status)
        private val time: TextView = view.findViewById(R.id.tv_time)
        private val error: TextView = view.findViewById(R.id.tv_error)
        private val progress: ProgressBar = view.findViewById(R.id.pb_uploading)

        fun bind(item: KnowledgeUploadItem) {
            name.text = item.displayName
            time.text = item.time
            status.text = item.status.label
            val uploading = item.status == KnowledgeUploadStatus.UPLOADING ||
                item.status == KnowledgeUploadStatus.INGESTING
            progress.isVisible = uploading
            val statusColor = when (item.status) {
                KnowledgeUploadStatus.INDEXED,
                KnowledgeUploadStatus.CARRIER_DONE -> 0xFF059669.toInt()
                KnowledgeUploadStatus.FAILED -> 0xFFDC2626.toInt()
                KnowledgeUploadStatus.UPLOADING,
                KnowledgeUploadStatus.INGESTING -> 0xFF1465EB.toInt()
            }
            status.setTextColor(statusColor)
            val note = item.error
            error.isVisible = !note.isNullOrBlank()
            error.text = note
            error.setTextColor(
                if (item.status == KnowledgeUploadStatus.FAILED) 0xFFDC2626.toInt()
                else 0xFF6B7280.toInt()
            )
        }
    }

    class Diff : DiffUtil.ItemCallback<KnowledgeUploadItem>() {
        override fun areItemsTheSame(a: KnowledgeUploadItem, b: KnowledgeUploadItem) = a.id == b.id
        override fun areContentsTheSame(a: KnowledgeUploadItem, b: KnowledgeUploadItem) = a == b
    }
}
