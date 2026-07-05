package com.fuusy.hiddendanger.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.data.PendingUpdateRecordItem
import com.fuusy.hiddendanger.databinding.ActivityKrApprovalBinding
import com.fuusy.hiddendanger.ui.adapter.PendingKrAdapter
import com.fuusy.hiddendanger.ui.adapter.PendingProgressAdapter
import com.fuusy.hiddendanger.util.AppDialogHelper
import com.fuusy.hiddendanger.viewmodel.KrApprovalViewModel

@Route(path = "/hiddendanger/KrApprovalActivity")
class KrApprovalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKrApprovalBinding
    private val viewModel: KrApprovalViewModel by viewModels()
    private lateinit var krAdapter: PendingKrAdapter
    private lateinit var progressAdapter: PendingProgressAdapter
    private var showProgressTab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKrApprovalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { finish() }

        krAdapter = PendingKrAdapter { item, pass -> confirmKrApprove(item, pass) }
        progressAdapter = PendingProgressAdapter { item, pass -> confirmProgressApprove(item, pass) }

        binding.rvPending.layoutManager = LinearLayoutManager(this)
        binding.rvPending.adapter = krAdapter

        binding.tabKrAssign.setOnClickListener { selectTab(false) }
        binding.tabProgress.setOnClickListener { selectTab(true) }

        viewModel.loading.observe(this) {
            binding.progressLoading.isVisible = it == true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.krItems.observe(this) { list ->
            updateEmptyState()
            if (!showProgressTab) krAdapter.submitList(list)
        }
        viewModel.progressItems.observe(this) { list ->
            updateEmptyState()
            if (showProgressTab) progressAdapter.submitList(list)
        }

        selectTab(false)
        viewModel.load()
    }

    private fun selectTab(progress: Boolean) {
        showProgressTab = progress
        binding.tabKrAssign.setBackgroundResource(
            if (progress) R.drawable.bg_goal_period_tab_normal
            else R.drawable.bg_goal_period_tab_selected
        )
        binding.tabProgress.setBackgroundResource(
            if (progress) R.drawable.bg_goal_period_tab_selected
            else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabKrAssign.setTextColor(
            Color.parseColor(if (progress) "#686D79" else "#1365EC")
        )
        binding.tabProgress.setTextColor(
            Color.parseColor(if (progress) "#1365EC" else "#686D79")
        )
        binding.rvPending.adapter = if (progress) progressAdapter else krAdapter
        if (progress) {
            progressAdapter.submitList(viewModel.progressItems.value.orEmpty())
        } else {
            krAdapter.submitList(viewModel.krItems.value.orEmpty())
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val list = if (showProgressTab) {
            viewModel.progressItems.value.orEmpty()
        } else {
            viewModel.krItems.value.orEmpty()
        }
        val empty = list.isEmpty()
        binding.tvEmpty.isVisible = empty
        binding.rvPending.isVisible = !empty
        binding.tvEmpty.text = if (showProgressTab) {
            "暂无待审批的进度更新"
        } else {
            "暂无待审批的关键结果"
        }
    }

    private fun confirmKrApprove(item: PendingKrItem, pass: Boolean) {
        AppDialogHelper.showInput(
            context = this,
            title = if (pass) "通过 KR" else "拒绝 KR",
            message = item.title,
            label = "审批意见：",
            hint = "审批意见（选填）",
            required = false,
            confirmText = if (pass) "通过" else "拒绝"
        ) { remark ->
            viewModel.approveKr(item.id, pass, remark)
        }
    }

    private fun confirmProgressApprove(item: PendingUpdateRecordItem, pass: Boolean) {
        AppDialogHelper.showInput(
            context = this,
            title = if (pass) "通过进度更新" else "拒绝进度更新",
            message = item.title.orEmpty(),
            label = "审批意见：",
            hint = "审批意见（选填）",
            required = false,
            confirmText = if (pass) "通过" else "拒绝"
        ) { remark ->
            viewModel.approveProgress(item.id, pass, remark)
        }
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.toolbar)
    }
}
