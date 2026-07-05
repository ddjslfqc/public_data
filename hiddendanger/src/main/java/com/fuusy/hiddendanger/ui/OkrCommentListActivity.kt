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
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityOkrCommentListBinding
import com.fuusy.hiddendanger.ui.adapter.OkrInboxCommentAdapter
import com.fuusy.hiddendanger.viewmodel.OkrCommentListViewModel

@Route(path = "/hiddendanger/OkrCommentListActivity")
class OkrCommentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOkrCommentListBinding
    private val viewModel: OkrCommentListViewModel by viewModels()
    private lateinit var adapter: OkrInboxCommentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOkrCommentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#FFFFFF")
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.tabReceived.setOnClickListener { switchTab(received = true) }
        binding.tabSent.setOnClickListener { switchTab(received = false) }

        adapter = OkrInboxCommentAdapter(
            allowReply = { viewModel.isReceivedTab() },
            onToggleExpand = { viewModel.toggleExpand(it) },
            onReply = { krId, content -> viewModel.submitReply(krId, content) },
            onViewDetail = { viewModel.openKrDetail(it) }
        )
        binding.rvComments.layoutManager = LinearLayoutManager(this)
        binding.rvComments.adapter = adapter

        observeViewModel()
        viewModel.load()
    }

    private fun switchTab(received: Boolean) {
        viewModel.setTab(received)
        binding.tabReceived.setBackgroundResource(
            if (received) R.drawable.bg_goal_period_tab_selected
            else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabSent.setBackgroundResource(
            if (!received) R.drawable.bg_goal_period_tab_selected
            else R.drawable.bg_goal_period_tab_normal
        )
        binding.tabReceived.setTextColor(
            Color.parseColor(if (received) "#1365EC" else "#686D79")
        )
        binding.tabSent.setTextColor(
            Color.parseColor(if (!received) "#1365EC" else "#686D79")
        )
        renderList()
        refreshExpandState()
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { loading ->
            binding.progressLoading.isVisible = loading == true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.received.observe(this) {
            if (viewModel.isReceivedTab()) renderList()
        }
        viewModel.sent.observe(this) {
            if (!viewModel.isReceivedTab()) renderList()
        }
        viewModel.expandedKrId.observe(this) { refreshExpandState() }
        viewModel.threadStateVersion.observe(this) { refreshExpandState() }
        viewModel.openKrDetail.observe(this) { item ->
            item ?: return@observe
            viewModel.consumeOpenKrDetail()
            KrDetailActivity.start(this, item)
        }
    }

    private fun renderList() {
        val list = viewModel.currentGroups()
        adapter.submitList(list)
        binding.tvEmpty.isVisible = list.isEmpty()
        binding.rvComments.isVisible = list.isNotEmpty()
    }

    private fun refreshExpandState() {
        adapter.updateExpandState(
            expandedKrId = viewModel.expandedKrId.value,
            threads = viewModel.threadCacheSnapshot(),
            loadingKrIds = viewModel.loadingThreadsSnapshot()
        )
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
