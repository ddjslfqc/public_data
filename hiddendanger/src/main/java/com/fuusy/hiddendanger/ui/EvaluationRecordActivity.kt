package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.hiddendanger.databinding.ActivityEvaluationRecordBinding
import com.fuusy.hiddendanger.ui.adapter.EvaluationRecordAdapter
import com.fuusy.hiddendanger.ui.model.EvaluationRecordItem
import com.fuusy.hiddendanger.ui.model.EvaluationRecordSummary
import com.fuusy.project.workorder.MobileWorkOrderRepository
import kotlinx.coroutines.launch

@Route(path = "/hiddendanger/EvaluationRecordActivity")
class EvaluationRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvaluationRecordBinding
    private val repo = MobileWorkOrderRepository()
    private val adapter = EvaluationRecordAdapter { item -> openWorkOrder(item) }

    private var currentTab = TAB_RECEIVED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvaluationRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#EEF1F8")

        currentTab = intent.getIntExtra(EXTRA_DEFAULT_TAB, TAB_RECEIVED)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvEvaluations.layoutManager = LinearLayoutManager(this)
        binding.rvEvaluations.adapter = adapter

        binding.tabReceived.setOnClickListener { switchTab(TAB_RECEIVED) }
        binding.tabSent.setOnClickListener { switchTab(TAB_SENT) }

        binding.viewTabIndicator.post { updateTabIndicator() }
        switchTab(currentTab)
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        val received = tab == TAB_RECEIVED

        binding.tabReceived.setTextColor(Color.parseColor(if (received) "#000000" else "#686D79"))
        binding.tabReceived.paint.isFakeBoldText = received
        binding.tabSent.setTextColor(Color.parseColor(if (received) "#686D79" else "#000000"))
        binding.tabSent.paint.isFakeBoldText = !received
        updateTabIndicator()
        loadTabData(received)
    }

    private fun loadTabData(received: Boolean) {
        val type = if (received) "received" else "sent"
        lifecycleScope.launch {
            val summaryResult = repo.evaluationSummary(type)
            val listResult = repo.evaluationList(type)
            summaryResult.onSuccess { applySummary(WorkOrderEvaluationUiMapper.toSummary(it)) }
                .onFailure { applySummary(EvaluationRecordSummary("--", "0%", 0)) }
            listResult.onSuccess { list ->
                adapter.submitList(list.map { WorkOrderEvaluationUiMapper.toRecordItem(it) })
                binding.rvEvaluations.isVisible = list.isNotEmpty()
                binding.tvEmpty.isVisible = list.isEmpty()
            }.onFailure {
                adapter.submitList(emptyList())
                binding.rvEvaluations.isVisible = false
                binding.tvEmpty.isVisible = true
                Toast.makeText(this@EvaluationRecordActivity, it.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openWorkOrder(item: EvaluationRecordItem) {
        val id = item.workOrderId ?: return
        lifecycleScope.launch {
            repo.detail(id).onSuccess { order ->
                ARouter.getInstance()
                    .build("/hiddendanger/OrderDetailActivity")
                    .withSerializable("workOrder", order)
                    .navigation()
            }.onFailure {
                Toast.makeText(this@EvaluationRecordActivity, it.message ?: "加载工单失败", Toast.LENGTH_SHORT).show()
            }
        }
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
        const val EXTRA_DEFAULT_TAB = "default_tab"
        const val TAB_RECEIVED = 0
        const val TAB_SENT = 1
    }
}
