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
import com.fuusy.hiddendanger.data.PeerEvalSubmissionDetail
import com.fuusy.hiddendanger.data.PeerEvalTemplate
import com.fuusy.hiddendanger.databinding.ActivityOkrPeerEvalDetailBinding
import com.fuusy.hiddendanger.ui.adapter.PeerEvalScoreFormAdapter
import com.fuusy.hiddendanger.viewmodel.PeerEvalDetailViewModel

class OkrPeerEvalDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOkrPeerEvalDetailBinding
    private val viewModel: PeerEvalDetailViewModel by viewModels()
    private lateinit var scoreAdapter: PeerEvalScoreFormAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOkrPeerEvalDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#FFFFFF")
        applyStatusBarPadding()

        val period = intent.getStringExtra(EXTRA_PERIOD).orEmpty()
        val targetUserId = intent.getLongExtra(EXTRA_TARGET_USER_ID, 0L)
        val targetUserName = intent.getStringExtra(EXTRA_TARGET_NAME).orEmpty()
        val deptName = intent.getStringExtra(EXTRA_DEPT_NAME)

        binding.tvTargetName.text = targetUserName.ifBlank { "用户$targetUserId" }
        binding.tvTargetDept.isVisible = !deptName.isNullOrBlank()
        binding.tvTargetDept.text = deptName

        scoreAdapter = PeerEvalScoreFormAdapter(readOnly = true)
        binding.rvScoreItems.apply {
            layoutManager = LinearLayoutManager(this@OkrPeerEvalDetailActivity)
            adapter = scoreAdapter
            itemAnimator = null
        }
        scoreAdapter.submitList(PeerEvalTemplate.formRows())

        binding.btnBack.setOnClickListener { finish() }

        viewModel.loading.observe(this) { binding.progressLoading.isVisible = it == true }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.detail.observe(this) { detail ->
            detail?.let { bindDetail(it) }
        }

        viewModel.load(period, targetUserId, targetUserName, deptName)
    }

    private fun bindDetail(detail: PeerEvalSubmissionDetail) {
        val name = detail.targetUserName?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(EXTRA_TARGET_NAME)?.takeIf { it.isNotBlank() }
        if (!name.isNullOrBlank()) {
            binding.tvTargetName.text = name
        }
        val dept = detail.deptName?.takeIf { it.isNotBlank() }
        binding.tvTargetDept.isVisible = !dept.isNullOrBlank()
        binding.tvTargetDept.text = dept

        binding.tvSubmittedAt.isVisible = !detail.submittedAt.isNullOrBlank()
        binding.tvSubmittedAt.text = "提交于 ${detail.submittedAt}"

        val avg = detail.averageScore
        binding.tvAverageScore.isVisible = avg != null && avg > 0
        binding.tvAverageScore.text = "平均分 %.1f".format(avg)

        val scoreMap = detail.scores.associate { it.itemId to it.score }
        scoreAdapter.setScores(scoreMap)

        val highlight = detail.highlight?.trim().orEmpty()
        val suggestion = detail.suggestion?.trim().orEmpty()
        binding.tvHighlight.text = highlight.ifBlank { "未填写" }
        binding.tvSuggestion.text = suggestion.ifBlank { "未填写" }
        binding.cardComments.isVisible = true
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
        private const val EXTRA_TARGET_USER_ID = "target_user_id"
        private const val EXTRA_TARGET_NAME = "target_name"
        private const val EXTRA_DEPT_NAME = "dept_name"

        fun start(
            context: Context,
            period: String,
            targetUserId: Long,
            targetUserName: String,
            deptName: String?
        ) {
            context.startActivity(
                Intent(context, OkrPeerEvalDetailActivity::class.java).apply {
                    putExtra(EXTRA_PERIOD, period)
                    putExtra(EXTRA_TARGET_USER_ID, targetUserId)
                    putExtra(EXTRA_TARGET_NAME, targetUserName)
                    deptName?.let { putExtra(EXTRA_DEPT_NAME, it) }
                }
            )
        }
    }
}
