package com.fuusy.project.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.common.network.UserIdProvider
import com.fuusy.project.R
import com.fuusy.project.databinding.ItemHistoryOrderBinding

class HistoryOrderAdapter(
    private val onEvalClick: ((WorkOrderItem) -> Unit)? = null
) : ListAdapter<WorkOrderItem, HistoryOrderAdapter.VH>(DiffCallback()) {

    class VH(val binding: ItemHistoryOrderBinding) : RecyclerView.ViewHolder(binding.root)

    private fun isCreator(item: WorkOrderItem): Boolean {
        val me = UserIdProvider.current()?.toString()?.trim().orEmpty()
        return me.isNotEmpty() && me == item.recordCreatorId?.trim()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val binding = holder.binding

        binding.tvTitle.text = item.hiddenDangerName.orEmpty()
        binding.tvDesc.text = item.hiddenDangerDescription.orEmpty()
        binding.tvMetaId.text = item.id

        val dept = item.responsibleDepartment.orEmpty()
        binding.tvMetaUser.text = item.submitUser
        binding.tvMetaDept.text = dept
        binding.tvMetaTime.text = item.submitTime

        bindPriorityTag(binding, item.priority)
        bindStatus(binding, item)
        binding.tvFooter.text = footerText(item)

        binding.tvAction.visibility = View.GONE
        when (item.status) {
            WorkOrderStatus.EVAL -> {
                if (isCreator(item)) {
                    binding.tvAction.visibility = View.VISIBLE
                    binding.tvAction.text = "⭐ 去评价"
                    binding.tvAction.setOnClickListener {
                        onEvalClick?.invoke(item) ?: openDetail(item)
                    }
                } else {
                    binding.tvAction.setOnClickListener(null)
                }
            }
            WorkOrderStatus.REJECT -> {
                if (isCreator(item)) {
                    binding.tvAction.visibility = View.VISIBLE
                    binding.tvAction.text = "重新提交"
                    binding.tvAction.setOnClickListener {
                        openResubmit(item)
                    }
                } else {
                    binding.tvAction.setOnClickListener(null)
                }
            }
            else -> binding.tvAction.setOnClickListener(null)
        }

        binding.footerBar.setOnClickListener {
            when (item.status) {
                WorkOrderStatus.REJECT -> {
                    if (isCreator(item)) openResubmit(item) else openDetail(item)
                }
                WorkOrderStatus.EVAL -> {
                    if (isCreator(item)) {
                        onEvalClick?.invoke(item) ?: openDetail(item)
                    } else {
                        openDetail(item)
                    }
                }
                else -> openDetail(item)
            }
        }

        holder.itemView.setOnClickListener {
            if (item.status == WorkOrderStatus.DRAFT) {
                if (isCreator(item)) {
                    ARouter.getInstance()
                        .build("/hiddendanger/CreateWorkOrderActivity")
                        .withSerializable("draft_data", item)
                        .navigation()
                } else {
                    openDetail(item)
                }
            } else {
                openDetail(item)
            }
        }
    }

    private fun openResubmit(item: WorkOrderItem) {
        ARouter.getInstance()
            .build("/hiddendanger/CreateWorkOrderActivity")
            .withSerializable("draft_data", item)
            .withBoolean("is_resubmit", true)
            .withString("resubmit_id", item.id)
            .navigation()
    }

    private fun bindPriorityTag(binding: ItemHistoryOrderBinding, priority: String?) {
        val tv = binding.tvPriority
        if (priority.isNullOrBlank()) {
            tv.visibility = View.GONE
            return
        }
        tv.visibility = View.VISIBLE
        tv.text = priority
        val (bg, color) = when (priority) {
            "P0" -> R.drawable.bg_wo_priority_p0 to Color.parseColor("#EB1919")
            "P1" -> R.drawable.bg_wo_priority_p1 to Color.parseColor("#F97316")
            "P2" -> R.drawable.bg_wo_priority_p2 to Color.parseColor("#F59E0B")
            else -> R.drawable.bg_wo_priority_p2 to Color.parseColor("#898FA0")
        }
        tv.setBackgroundResource(bg)
        tv.setTextColor(color)
    }

    private fun openDetail(item: WorkOrderItem) {
        ARouter.getInstance()
            .build("/hiddendanger/OrderDetailActivity")
            .withSerializable("workOrder", item)
            .navigation()
    }

    private fun footerText(item: WorkOrderItem): String = when (item.status) {
        WorkOrderStatus.PENDING -> "期望：${item.expectedCompleteTime ?: item.submitTime}"
        WorkOrderStatus.PROCESSING -> "期望：${item.expectedCompleteTime ?: "--"}"
        WorkOrderStatus.COMPLETED -> "完成于 ${item.submitTime}"
        WorkOrderStatus.REJECT -> if (isCreator(item)) "↩ 撤回修改后重新提交" else "已驳回"
        WorkOrderStatus.DRAFT -> "隶属项目：${item.projectName ?: "--"}"
        WorkOrderStatus.EVAL -> if (isCreator(item)) "待评价 · 点击去评价" else "待提报人评价"
        else -> item.projectName?.let { "隶属项目：$it" } ?: item.submitTime
    }

    private fun bindStatus(binding: ItemHistoryOrderBinding, item: WorkOrderItem) {
        val tvStatus = binding.tvStatus
        val label = item.nodeName?.takeIf { it.isNotBlank() } ?: item.status.displayName
        tvStatus.text = label

        val (bgRes, textColor) = when (item.status) {
            WorkOrderStatus.DRAFT -> R.drawable.bg_order_status_draft to Color.parseColor("#898FA0")
            WorkOrderStatus.PENDING -> R.drawable.bg_wo_status_pending to Color.parseColor("#F97316")
            WorkOrderStatus.REJECT -> R.drawable.bg_status_reject to Color.parseColor("#EB1919")
            WorkOrderStatus.PROCESSING -> R.drawable.bg_status_processing to Color.parseColor("#6366F1")
            WorkOrderStatus.EVAL -> R.drawable.bg_wo_status_eval to Color.parseColor("#8B5CF6")
            WorkOrderStatus.COMPLETED -> R.drawable.bg_status_done to Color.parseColor("#00AA60")
        }
        tvStatus.setBackgroundResource(bgRes)
        tvStatus.setTextColor(textColor)
    }

    class DiffCallback : DiffUtil.ItemCallback<WorkOrderItem>() {
        override fun areItemsTheSame(oldItem: WorkOrderItem, newItem: WorkOrderItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: WorkOrderItem, newItem: WorkOrderItem) =
            oldItem == newItem
    }
}
