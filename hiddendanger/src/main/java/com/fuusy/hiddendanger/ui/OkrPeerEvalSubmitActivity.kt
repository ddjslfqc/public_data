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
import com.fuusy.hiddendanger.data.PeerEvalTemplate
import com.fuusy.hiddendanger.databinding.ActivityOkrPeerEvalSubmitBinding
import com.fuusy.hiddendanger.ui.adapter.PeerEvalScoreFormAdapter
import com.fuusy.hiddendanger.viewmodel.PeerEvalSubmitViewModel

@Route(path = "/hiddendanger/OkrPeerEvalSubmitActivity")
class OkrPeerEvalSubmitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOkrPeerEvalSubmitBinding
    private val viewModel: PeerEvalSubmitViewModel by viewModels()
    private lateinit var scoreAdapter: PeerEvalScoreFormAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOkrPeerEvalSubmitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#FFFFFF")
        applyStatusBarPadding()

        viewModel.period = intent.getStringExtra(EXTRA_PERIOD).orEmpty()
        viewModel.targetUserId = intent.getLongExtra(EXTRA_TARGET_USER_ID, 0L)
        viewModel.targetUserName = intent.getStringExtra(EXTRA_TARGET_NAME).orEmpty()

        binding.tvTargetName.text = viewModel.targetUserName.ifBlank { "用户${viewModel.targetUserId}" }
        val dept = intent.getStringExtra(EXTRA_DEPT_NAME)
        binding.tvTargetDept.isVisible = !dept.isNullOrBlank()
        binding.tvTargetDept.text = dept

        scoreAdapter = PeerEvalScoreFormAdapter()
        binding.rvScoreItems.apply {
            layoutManager = LinearLayoutManager(this@OkrPeerEvalSubmitActivity)
            adapter = scoreAdapter
            itemAnimator = null
        }
        scoreAdapter.submitList(PeerEvalTemplate.formRows())

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener {
            viewModel.submit(
                scores = scoreAdapter.getScores(),
                highlight = binding.etHighlight.text?.toString().orEmpty(),
                suggestion = binding.etSuggestion.text?.toString().orEmpty()
            )
        }

        viewModel.loading.observe(this) { binding.progressLoading.isVisible = it == true }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.submitted.observe(this) { done ->
            if (done) {
                viewModel.consumeSubmitted()
                Toast.makeText(this, "评价已提交", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
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
                Intent(context, OkrPeerEvalSubmitActivity::class.java).apply {
                    putExtra(EXTRA_PERIOD, period)
                    putExtra(EXTRA_TARGET_USER_ID, targetUserId)
                    putExtra(EXTRA_TARGET_NAME, targetUserName)
                    deptName?.let { putExtra(EXTRA_DEPT_NAME, it) }
                }
            )
        }
    }
}
