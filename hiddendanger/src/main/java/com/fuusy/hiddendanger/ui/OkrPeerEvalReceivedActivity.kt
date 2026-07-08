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
import com.fuusy.hiddendanger.data.PeerEvalReceivedResponse
import com.fuusy.hiddendanger.databinding.ActivityOkrPeerEvalReceivedBinding
import com.fuusy.hiddendanger.ui.adapter.PeerEvalScoreBreakdownAdapter
import com.fuusy.hiddendanger.viewmodel.PeerEvalReceivedViewModel

class OkrPeerEvalReceivedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOkrPeerEvalReceivedBinding
    private val viewModel: PeerEvalReceivedViewModel by viewModels()
    private val breakdownAdapter = PeerEvalScoreBreakdownAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOkrPeerEvalReceivedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#FFFFFF")
        applyStatusBarPadding()

        val period = intent.getStringExtra(EXTRA_PERIOD).orEmpty()

        binding.rvBreakdown.apply {
            layoutManager = LinearLayoutManager(this@OkrPeerEvalReceivedActivity)
            adapter = breakdownAdapter
            itemAnimator = null
        }

        binding.btnBack.setOnClickListener { finish() }

        viewModel.loading.observe(this) { binding.progressLoading.isVisible = it == true }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.received.observe(this) { data ->
            bindReceived(data)
        }

        viewModel.load(period)
    }

    private fun bindReceived(data: PeerEvalReceivedResponse?) {
        val empty = data == null || data.evaluatorCount <= 0
        binding.tvEmpty.isVisible = empty
        binding.layoutContent.isVisible = !empty
        if (empty || data == null) {
            binding.tvEmpty.text = "暂无同事评价，互评结束后将在此汇总展示"
            return
        }
        binding.tvEvaluatorCount.text = "${data.evaluatorCount} 位同事已完成评价"
        binding.tvAverageScore.text = "%.1f".format(data.averageScore)
        binding.tvPublishedAt.isVisible = !data.publishedAt.isNullOrBlank()
        binding.tvPublishedAt.text = "汇总于 ${data.publishedAt}"

        breakdownAdapter.submitList(data.scoreBreakdown)

        val highlights = data.highlights.filter { it.isNotBlank() }
        binding.cardHighlights.isVisible = highlights.isNotEmpty()
        binding.tvHighlights.text = highlights.joinToString("\n") { "• $it" }

        val suggestions = data.suggestions.filter { it.isNotBlank() }
        binding.cardSuggestions.isVisible = suggestions.isNotEmpty()
        binding.tvSuggestions.text = suggestions.joinToString("\n") { "• $it" }
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
        private const val EXTRA_PERIOD = "period"

        fun start(context: Context, period: String) {
            context.startActivity(
                Intent(context, OkrPeerEvalReceivedActivity::class.java).apply {
                    putExtra(EXTRA_PERIOD, period)
                }
            )
        }
    }
}
