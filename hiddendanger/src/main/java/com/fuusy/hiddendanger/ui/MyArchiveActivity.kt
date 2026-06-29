package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.fuusy.common.utils.SpUtils
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityMyArchiveBinding
import com.fuusy.hiddendanger.databinding.ItemArchiveDistributionRowBinding
import com.fuusy.hiddendanger.ui.adapter.ArchiveEvaluationAdapter
import com.fuusy.hiddendanger.ui.model.ArchiveDistributionItem
import com.fuusy.hiddendanger.ui.model.ArchiveEvaluationItem

@Route(path = "/hiddendanger/MyArchiveActivity")
class MyArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyArchiveBinding
    private val adapter = ArchiveEvaluationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#BAD4FF")

        binding.btnBack.setOnClickListener { finish() }
        setupProfile()
        setupStats()
        setupDistribution()
        setupEvaluationList()
    }

    private fun setupProfile() {
        val name = SpUtils.getString("user_name")?.takeIf { it.isNotBlank() } ?: "常伟思"
        val department = SpUtils.getString("user_department")?.takeIf { it.isNotBlank() } ?: "软件研发部"
        val empId = formatEmployeeId(name)

        binding.tvName.text = name
        binding.tvTagEmp.text = empId
        binding.tvTagDept.text = department
        binding.tvTagRole.text = "研发工程师"

        Glide.with(this)
            .load("https://img.rongyuejiaoyu.com/uploads/20240728/02511242750.jpeg")
            .apply(RequestOptions().transform(CircleCrop()))
            .placeholder(R.mipmap.default_head_photo)
            .error(R.mipmap.default_head_photo)
            .into(binding.ivAvatar)
    }

    private fun setupStats() {
        binding.tvCompletedCount.text = "28"
        binding.tvAverageRating.text = "4.5"
    }

    private fun setupDistribution() {
        val items = ArchiveMockData.distribution
        val maxCount = items.maxOf { it.count }.coerceAtLeast(1)
        binding.llDistribution.removeAllViews()
        items.forEach { item ->
            val rowBinding = ItemArchiveDistributionRowBinding.inflate(layoutInflater, binding.llDistribution, true)
            rowBinding.tvLabel.text = item.label
            rowBinding.tvCount.text = "${item.count}次"
            rowBinding.viewBarFill.post {
                val trackWidth = rowBinding.viewBarFill.parent.let { parent ->
                    (parent as View).width
                }
                val fillWidth = (trackWidth * item.count / maxCount.toFloat()).toInt().coerceAtLeast(0)
                rowBinding.viewBarFill.layoutParams.width = fillWidth
                rowBinding.viewBarFill.requestLayout()
            }
        }
    }

    private fun setupEvaluationList() {
        binding.rvEvaluations.layoutManager = LinearLayoutManager(this)
        binding.rvEvaluations.adapter = adapter
        adapter.submitList(ArchiveMockData.evaluations)
    }

    private fun formatEmployeeId(username: String): String {
        val suffix = username.filter { it.isDigit() }.takeLast(3).padStart(3, '0')
        return if (suffix == "000") "EMP001" else "EMP$suffix"
    }
}

private object ArchiveMockData {
    val distribution = listOf(
        ArchiveDistributionItem("响应速度太慢", 4),
        ArchiveDistributionItem("沟通响应缓慢", 2),
        ArchiveDistributionItem("反复来回", 3),
        ArchiveDistributionItem("无", 9)
    )

    val evaluations = listOf(
        ArchiveEvaluationItem(
            reviewerName = "章北海",
            reviewerInitial = "章",
            avatarColor = Color.parseColor("#1365EC"),
            rating = 5f,
            tag = "无问题，很完美",
            tagTextColor = Color.parseColor("#00AA60"),
            tagBackgroundRes = R.drawable.bg_archive_tag_good,
            content = "客户门户体验优化需求完成得非常出色，响应迅速，沟通顺畅，专业能力很强！",
            meta = "WD202606180001·2026-06-18"
        ),
        ArchiveEvaluationItem(
            reviewerName = "汪淼",
            reviewerInitial = "汪",
            avatarColor = Color.parseColor("#00AA60"),
            rating = 4f,
            tag = "响应速度太慢",
            tagTextColor = Color.parseColor("#EA9300"),
            tagBackgroundRes = R.drawable.bg_archive_tag_warn,
            content = "整体完成质量不错，但过程中来回确认次数偏多，下次建议提前对齐需求细节。",
            meta = "WD202606180001·2026-06-18"
        )
    )
}
