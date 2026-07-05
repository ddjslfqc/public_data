package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
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
import com.fuusy.hiddendanger.databinding.ActivityOkrCommentListBinding
import com.fuusy.hiddendanger.ui.adapter.OkrInboxCommentAdapter
import com.fuusy.hiddendanger.viewmodel.OkrCommentListViewModel

@Route(path = "/hiddendanger/OkrCommentListActivity")
class OkrCommentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOkrCommentListBinding
    private val viewModel: OkrCommentListViewModel by viewModels()
    private val adapter = OkrInboxCommentAdapter()
    private var showingReceived = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOkrCommentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#FFFFFF")
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.tabReceived.setOnClickListener { switchTab(received = true) }
        binding.tabSent.setOnClickListener { switchTab(received = false) }

        binding.rvComments.layoutManager = LinearLayoutManager(this)
        binding.rvComments.adapter = adapter

        observeViewModel()
        viewModel.load()
    }

    private fun switchTab(received: Boolean) {
        showingReceived = received
        binding.tabReceived.apply {
            setBackgroundResource(
                if (received) com.fuusy.hiddendanger.R.drawable.bg_goal_kr_badge
                else com.fuusy.hiddendanger.R.drawable.bg_goal_input
            )
            setTextColor(if (received) Color.parseColor("#6A2DF6") else Color.parseColor("#686D79"))
        }
        binding.tabSent.apply {
            setBackgroundResource(
                if (!received) com.fuusy.hiddendanger.R.drawable.bg_goal_kr_badge
                else com.fuusy.hiddendanger.R.drawable.bg_goal_input
            )
            setTextColor(if (!received) Color.parseColor("#6A2DF6") else Color.parseColor("#686D79"))
        }
        renderList()
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { loading ->
            binding.progressLoading.isVisible = loading == true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.received.observe(this) {
            if (showingReceived) renderList()
        }
        viewModel.sent.observe(this) {
            if (!showingReceived) renderList()
        }
    }

    private fun renderList() {
        val list = if (showingReceived) {
            viewModel.received.value.orEmpty()
        } else {
            viewModel.sent.value.orEmpty()
        }
        adapter.submitList(list)
        binding.tvEmpty.isVisible = list.isEmpty()
        binding.rvComments.isVisible = list.isNotEmpty()
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.toolbar)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OkrCommentListActivity::class.java))
        }
    }
}
