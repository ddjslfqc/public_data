package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.hiddendanger.databinding.ActivityEvaluationRecordBinding
import com.fuusy.hiddendanger.ui.adapter.EvaluationRecordAdapter
import com.fuusy.hiddendanger.ui.model.EvaluationRecordItem
import com.fuusy.hiddendanger.ui.model.EvaluationRecordSummary

@Route(path = "/hiddendanger/EvaluationRecordActivity")
class EvaluationRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvaluationRecordBinding
    private val adapter = EvaluationRecordAdapter { item ->
        item.workOrder?.let { order ->
            ARouter.getInstance()
                .build("/hiddendanger/OrderDetailActivity")
                .withSerializable("workOrder", order)
                .navigation()
        }
    }

    private var currentTab = TAB_RECEIVED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvaluationRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#EEF1F8")

        binding.btnBack.setOnClickListener { finish() }
        binding.rvEvaluations.layoutManager = LinearLayoutManager(this)
        binding.rvEvaluations.adapter = adapter

        binding.tabReceived.setOnClickListener { switchTab(TAB_RECEIVED) }
        binding.tabSent.setOnClickListener { switchTab(TAB_SENT) }

        binding.viewTabIndicator.post { updateTabIndicator() }
        switchTab(TAB_RECEIVED)
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        val received = tab == TAB_RECEIVED

        binding.tabReceived.setTextColor(
            Color.parseColor(if (received) "#000000" else "#686D79")
        )
        binding.tabReceived.paint.isFakeBoldText = received
        binding.tabSent.setTextColor(
            Color.parseColor(if (received) "#686D79" else "#000000")
        )
        binding.tabSent.paint.isFakeBoldText = !received

        updateTabIndicator()

        val summary = if (received) {
            EvaluationRecordMockData.receivedSummary
        } else {
            EvaluationRecordMockData.sentSummary
        }
        applySummary(summary)

        val list = if (received) {
            EvaluationRecordMockData.receivedList
        } else {
            EvaluationRecordMockData.sentList
        }
        adapter.submitList(list)
        binding.rvEvaluations.isVisible = list.isNotEmpty()
        binding.tvEmpty.isVisible = list.isEmpty()
    }

    private fun updateTabIndicator() {
        val container = binding.viewTabIndicator.parent as View
        val halfWidth = container.width / 2f
        val params = binding.viewTabIndicator.layoutParams as FrameLayout.LayoutParams
        params.width = (halfWidth - 4).toInt()
        params.marginStart = if (currentTab == TAB_RECEIVED) 2 else (halfWidth + 2).toInt()
        binding.viewTabIndicator.layoutParams = params
    }

    private fun applySummary(summary: EvaluationRecordSummary) {
        binding.tvAvgRating.text = summary.averageRating
        binding.tvPositiveRate.text = summary.positiveRate
        binding.tvTotalCount.text = summary.totalCount.toString()
    }

    companion object {
        private const val TAB_RECEIVED = 0
        private const val TAB_SENT = 1
    }
}

private object EvaluationRecordMockData {
    val receivedSummary = EvaluationRecordSummary("4.6", "96%", 18)
    val sentSummary = EvaluationRecordSummary("4.8", "100%", 6)

    private fun workOrder(id: String, name: String): WorkOrderItem {
        return WorkOrderItem(
            id = id,
            hiddenDangerName = name,
            hiddenDangerDescription = "巡检现场发现的设备维护工单，已完成处理。",
            hiddenDangerCategory = "设备安全隐患",
            submitUser = "常伟思",
            responsiblePerson = "常伟思",
            submitTime = "2026-06-15 10:30",
            status = WorkOrderStatus.COMPLETED,
            nodeName = "已处理"
        )
    }

    val receivedList = listOf(
        EvaluationRecordItem(
            id = "recv_1",
            reviewerName = "章北海",
            reviewerInitial = "章",
            avatarColor = Color.parseColor("#1365EC"),
            department = "检修部",
            date = "2026-06-15",
            rating = 5f,
            content = "处理速度很快，专业能力强，设备故障很快就解决了，非常满意！",
            workOrderTitle = "主控室空调滤网清洗",
            workOrder = workOrder("WD202606150001", "主控室空调滤网清洗")
        ),
        EvaluationRecordItem(
            id = "recv_2",
            reviewerName = "云天明",
            reviewerInitial = "云",
            avatarColor = Color.parseColor("#6A2DF6"),
            department = "安监部",
            date = "2026-06-12",
            rating = 5f,
            content = "响应及时，服务态度好，下次有需要还会找你。",
            workOrderTitle = "2号循环水泵轴承更换",
            workOrder = workOrder("WD202606120002", "2号循环水泵轴承更换")
        ),
        EvaluationRecordItem(
            id = "recv_3",
            reviewerName = "汪淼",
            reviewerInitial = "汪",
            avatarColor = Color.parseColor("#00AA60"),
            department = "运行部",
            date = "2026-06-12",
            rating = 5f,
            content = "非常专业，讲解清楚，对设备问题分析到位，工单完成质量很高。",
            workOrderTitle = "硫化氢报警器校准",
            workOrder = workOrder("WD202606120003", "硫化氢报警器校准")
        )
    )

    val sentList = listOf(
        EvaluationRecordItem(
            id = "sent_1",
            reviewerName = "张工程师",
            reviewerInitial = "张",
            avatarColor = Color.parseColor("#EA9300"),
            department = "检修部",
            date = "2026-06-10",
            rating = 5f,
            content = "现场配合积极，问题定位准确，处理结果符合预期。",
            workOrderTitle = "输煤皮带防护栏修复",
            workOrder = workOrder("WD202606100001", "输煤皮带防护栏修复")
        ),
        EvaluationRecordItem(
            id = "sent_2",
            reviewerName = "李班长",
            reviewerInitial = "李",
            avatarColor = Color.parseColor("#1365EC"),
            department = "运行部",
            date = "2026-06-08",
            rating = 4f,
            content = "整体不错，建议后续提前沟通检修窗口。",
            workOrderTitle = "给水泵振动异常排查",
            workOrder = workOrder("WD202606080001", "给水泵振动异常排查")
        )
    )
}
