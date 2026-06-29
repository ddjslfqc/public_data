package com.fuusy.project.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.R

class AlarmRecordAdapter : RecyclerView.Adapter<AlarmRecordAdapter.ViewHolder>() {
    var data: List<AlarmRecord> = emptyList()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm_record, parent, false)
        return ViewHolder(view)
    }
    override fun getItemCount() = data.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.tvAlarmContent.text = item.content
        holder.tvAlarmLocation.text = "报警地点：" + item.location
        holder.tvAlarmChannel.text = "报警通道：" + item.channel
        holder.tvAlarmTime.text = item.time
        holder.tvAlarmStatus.visibility = if (item.isNew) View.VISIBLE else View.GONE
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAlarmContent: TextView = view.findViewById(R.id.tvAlarmContent)
        val tvAlarmLocation: TextView = view.findViewById(R.id.tvAlarmLocation)
        val tvAlarmChannel: TextView = view.findViewById(R.id.tvAlarmChannel)
        val tvAlarmTime: TextView = view.findViewById(R.id.tvAlarmTime)
        val tvAlarmStatus: TextView = view.findViewById(R.id.tvAlarmStatus)
    }
}

data class AlarmRecord(
    val content: String,
    val location: String,
    val channel: String,
    val time: String,
    val isNew: Boolean
) 