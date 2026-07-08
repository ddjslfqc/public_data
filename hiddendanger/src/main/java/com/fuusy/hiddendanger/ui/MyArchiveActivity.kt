package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.fuusy.common.utils.SpUtils
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityMyArchiveBinding
import com.fuusy.hiddendanger.databinding.ItemArchiveDistributionRowBinding
import com.fuusy.hiddendanger.ui.adapter.ArchiveEvaluationAdapter
import com.fuusy.project.workorder.MobileWorkOrderRepository
import kotlinx.coroutines.launch

@Route(path = "/hiddendanger/MyArchiveActivity")
class MyArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyArchiveBinding
    private val repo = MobileWorkOrderRepository()
    private val adapter = ArchiveEvaluationAdapter { item ->
        item.workOrderId?.let { id ->
            lifecycleScope.launch {
                repo.detail(id).onSuccess { order ->
                    ARouter.getInstance()
                        .build("/hiddendanger/OrderDetailActivity")
                        .withSerializable("workOrder", order)
                        .navigation()
                }.onFailure {
                    Toast.makeText(this@MyArchiveActivity, it.message ?: "加载工单失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#BAD4FF")

        binding.btnBack.setOnClickListener { finish() }
        setupProfile()
        setupEvaluationList()
        loadArchive()
    }

    private fun setupProfile() {
        val name = SpUtils.getString("user_name")?.takeIf { it.isNotBlank() } ?: "用户"
        val department = SpUtils.getString("user_department")?.takeIf { it.isNotBlank() } ?: "软件研发部"
        binding.tvName.text = name
        binding.tvTagEmp.text = formatEmployeeId(name)
        binding.tvTagDept.text = department
        binding.tvTagRole.text = "研发工程师"
        Glide.with(this)
            .load("https://img.rongyuejiaoyu.com/uploads/20240728/02511242750.jpeg")
            .apply(RequestOptions().transform(CircleCrop()))
            .placeholder(R.mipmap.default_head_photo)
            .error(R.mipmap.default_head_photo)
            .into(binding.ivAvatar)
    }

    private fun setupEvaluationList() {
        binding.rvEvaluations.layoutManager = LinearLayoutManager(this)
        binding.rvEvaluations.adapter = adapter
        binding.tvEvalMore.setOnClickListener {
            ARouter.getInstance()
                .build("/hiddendanger/EvaluationRecordActivity")
                .navigation()
        }
        binding.cardStatCompleted.setOnClickListener { openCompletedOrders() }
        binding.cardStatRating.setOnClickListener {
            ARouter.getInstance()
                .build("/hiddendanger/EvaluationRecordActivity")
                .navigation()
        }
    }

    private fun loadArchive() {
        lifecycleScope.launch {
            repo.archive().onSuccess { data ->
                binding.tvCompletedCount.text = data.completedCount.toString()
                binding.tvAverageRating.text = data.averageRating ?: "--"
                renderDistribution(data.tagDistribution.orEmpty().map {
                    WorkOrderEvaluationUiMapper.toArchiveDistribution(it)
                })
                adapter.submitList(data.recentEvaluations.orEmpty().map {
                    WorkOrderEvaluationUiMapper.toArchiveEvaluation(it)
                })
            }.onFailure {
                Toast.makeText(this@MyArchiveActivity, it.message ?: "加载档案失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderDistribution(items: List<com.fuusy.hiddendanger.ui.model.ArchiveDistributionItem>) {
        binding.llDistribution.removeAllViews()
        if (items.isEmpty()) {
            val empty = ItemArchiveDistributionRowBinding.inflate(layoutInflater, binding.llDistribution, true)
            empty.tvLabel.text = "暂无评价数据"
            empty.tvCount.text = ""
            empty.viewBarFill.visibility = View.GONE
            return
        }
        val maxCount = items.maxOf { it.count }.coerceAtLeast(1)
        items.forEach { item ->
            val rowBinding = ItemArchiveDistributionRowBinding.inflate(layoutInflater, binding.llDistribution, true)
            rowBinding.tvLabel.text = item.label
            rowBinding.tvCount.text = "${item.count}次"
            rowBinding.viewBarFill.post {
                val trackWidth = (rowBinding.viewBarFill.parent as View).width
                rowBinding.viewBarFill.layoutParams.width =
                    (trackWidth * item.count / maxCount.toFloat()).toInt().coerceAtLeast(0)
                rowBinding.viewBarFill.requestLayout()
            }
        }
    }

    private fun openCompletedOrders() {
        ARouter.getInstance()
            .build("/project/HistoryOrderActivity")
            .withBoolean(com.fuusy.project.ui.activity.HistoryOrderActivity.EXTRA_SHOW_COMPLETED, true)
            .navigation()
    }

    private fun formatEmployeeId(username: String): String {
        val suffix = username.filter { it.isDigit() }.takeLast(3).padStart(3, '0')
        return if (suffix == "000") "EMP001" else "EMP$suffix"
    }
}
