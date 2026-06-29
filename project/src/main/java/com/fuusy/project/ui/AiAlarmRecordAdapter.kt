package com.fuusy.project.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fuusy.project.R
import com.fuusy.project.bean.AiAlarmRecord

class AiAlarmRecordAdapter(
    val onItemClick: (AiAlarmRecord) -> Unit
) : RecyclerView.Adapter<AiAlarmRecordAdapter.ViewHolder>() {
    var data: List<AiAlarmRecord> = emptyList()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_alarm_record, parent, false)
        return ViewHolder(view)
    }
    override fun getItemCount() = data.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.bind(item)
        holder.card.setOnClickListener { onItemClick(item) }
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardAiAlarm)
        val ivIcon: ImageView = view.findViewById(R.id.ivAiIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvAiTitle)
        val tvContent: TextView = view.findViewById(R.id.tvAiContent)
        val tvLocation: TextView = view.findViewById(R.id.tvAiLocation)
        val tvTime: TextView = view.findViewById(R.id.tvAiTime)
        val tvNew: TextView = view.findViewById(R.id.tvAiNew)
        fun bind(item: AiAlarmRecord) {
            Glide.with(ivIcon.context).load(item.image).placeholder(R.drawable.ic_ai_alarm).into(ivIcon)
            tvTitle.text = "AI告警"
            tvContent.text = "报警内容：${item.itemName}"
            tvLocation.text = "报警地点：${item.address}"
            tvTime.text = item.appendtime
            tvNew.visibility = View.VISIBLE // 可根据业务调整
        }
    }
} 