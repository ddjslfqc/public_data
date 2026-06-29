package com.fuusy.project.adapter

import com.fuusy.project.R
import com.fuusy.project.bean.MonitoringData

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dp


class MonitoringDataAdapter : RecyclerView.Adapter<MonitoringDataAdapter.ViewHolder>() {

    private var dataList = mutableListOf<MonitoringData>()

    fun setData(data: List<MonitoringData>) {
        dataList.clear()
        dataList.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monitoring_data, parent, false)
        // 动态设置item宽度为(屏幕宽度-40)/当前数据数量，实现横向等宽分布
        val itemCount = dataList.size.takeIf { it > 0 } ?: 5 // 避免除0，默认5
        val screenWidth = parent.context.resources.displayMetrics.widthPixels
        val itemWidth = (screenWidth - 40.dp) / itemCount
        view.layoutParams =
            RecyclerView.LayoutParams(itemWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataList[position])
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_gas_name)
        private val tvValue: TextView = itemView.findViewById(R.id.tv_gas_value)
        private val tvUnit: TextView = itemView.findViewById(R.id.tv_gas_unit)

        fun bind(data: MonitoringData) {
            tvName.text = data.name
            // 当数据值为空或无效时显示"--"
            tvValue.text = if (data.value.isNullOrEmpty() || data.value == "null") "--" else data.value
            tvUnit.text = data.unit
        }
    }
}