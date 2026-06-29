package com.fuusy.hiddendanger.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.databinding.ActivityKrApprovalBinding
import com.fuusy.hiddendanger.ui.adapter.PendingKrAdapter
import com.fuusy.hiddendanger.viewmodel.KrApprovalViewModel

@Route(path = "/hiddendanger/KrApprovalActivity")
class KrApprovalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKrApprovalBinding
    private val viewModel: KrApprovalViewModel by viewModels()
    private lateinit var adapter: PendingKrAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKrApprovalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { finish() }

        adapter = PendingKrAdapter { item, pass -> confirmApprove(item, pass) }
        binding.rvPending.layoutManager = LinearLayoutManager(this)
        binding.rvPending.adapter = adapter

        viewModel.loading.observe(this) {
            binding.progressLoading.isVisible = it == true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.items.observe(this) { list ->
            adapter.submitList(list)
            val empty = list.isNullOrEmpty()
            binding.tvEmpty.isVisible = empty
            binding.rvPending.isVisible = !empty
        }

        viewModel.load()
    }

    private fun confirmApprove(item: PendingKrItem, pass: Boolean) {
        val remarkInput = EditText(this).apply {
            hint = "审批意见（选填）"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(if (pass) "通过 KR" else "拒绝 KR")
            .setMessage(item.title)
            .setView(remarkInput)
            .setPositiveButton(if (pass) "通过" else "拒绝") { _, _ ->
                viewModel.approve(item.id, pass, remarkInput.text?.toString())
            }
            .setNegativeButton("取消", null)
            .show()
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
