package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.fuusy.common.network.UserIdProvider
import com.fuusy.hiddendanger.databinding.ActivityKrDetailBinding
import com.fuusy.hiddendanger.ui.adapter.KrCommentAdapter
import com.fuusy.hiddendanger.ui.adapter.KrUpdateRecordAdapter
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrNavHelper
import com.fuusy.hiddendanger.ui.model.KrProgressHelper
import com.fuusy.hiddendanger.util.AppDialogHelper
import com.fuusy.hiddendanger.viewmodel.KrDetailViewModel

class KrDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKrDetailBinding
    private val detailViewModel: KrDetailViewModel by viewModels()
    private lateinit var krItem: GoalKrItem
    private var readOnly: Boolean = false
    private lateinit var commentAdapter: KrCommentAdapter
    private val updateRecordAdapter = KrUpdateRecordAdapter()

    private val updateProgressLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        KrNavHelper.fromIntent(result.data ?: return@registerForActivityResult)?.let { updated ->
            krItem = updated
            setResult(RESULT_OK, Intent().apply { KrNavHelper.putExtra(this, updated) })
            bindSummary()
            detailViewModel.load(krItem)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val item = KrNavHelper.fromIntent(intent)
        if (item == null) {
            Toast.makeText(this, "KR 数据无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        krItem = item
        readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)

        binding = ActivityKrDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        applyStatusBarPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnUpdateProgress.setOnClickListener {
            updateProgressLauncher.launch(
                Intent(this, KrUpdateProgressActivity::class.java).apply {
                    KrNavHelper.putExtra(this, krItem)
                }
            )
        }
        binding.layoutCommentInput.isVisible = !readOnly
        setupComments()
        setupUpdateRecords()
        observeDetailViewModel()
        bindSummary()
        detailViewModel.load(krItem)
    }

    private fun bindSummary() {
        binding.tvKrTitle.text = krItem.title
        binding.tvObjectiveTitle.text = "所属目标：${krItem.objectiveTitle}"
        binding.tvKrValue.text = krItem.valueLabel
        binding.tvProgressPercent.text = "${krItem.progressPercent}%"

        val progressLabel = KrProgressHelper.progressStatusLabel(krItem)
        binding.tvProgressApproval.isVisible = progressLabel != null
        binding.tvProgressApproval.text = progressLabel

        val showKrApproval = !krItem.approvalLabel.isNullOrBlank() && krItem.approvalStatus != 1
        binding.tvApprovalStatus.isVisible = showKrApproval
        binding.tvApprovalStatus.text = krItem.pendingApproverHint ?: krItem.approvalLabel

        binding.flProgress.post {
            val trackWidth = binding.flProgress.width
            if (trackWidth > 0) {
                binding.viewProgress.layoutParams.width =
                    (trackWidth * krItem.progressPercent / 100f).toInt().coerceAtLeast(0)
                binding.viewProgress.requestLayout()
            }
        }

        val canUpdate = !readOnly && KrProgressHelper.canUpdateProgress(krItem)
        binding.btnUpdateProgress.isVisible = canUpdate

        val blocked = if (readOnly) null else KrProgressHelper.updateBlockedReason(krItem)
        binding.tvBlockedHint.isVisible = !canUpdate && blocked != null
        binding.tvBlockedHint.text = blocked
    }

    private fun setupComments() {
        commentAdapter = KrCommentAdapter(UserIdProvider.current()) { comment ->
            AppDialogHelper.showConfirm(
                context = this,
                title = "删除评论",
                message = "确定删除这条评论吗？",
                confirmText = "删除"
            ) {
                detailViewModel.deleteComment(comment.id, krItem.id)
            }
        }
        binding.rvComments.layoutManager = LinearLayoutManager(this)
        binding.rvComments.adapter = commentAdapter
        binding.btnSendComment.setOnClickListener { submitComment() }
        binding.etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitComment()
                true
            } else {
                false
            }
        }
    }

    private fun submitComment() {
        detailViewModel.submitComment(krItem.id, binding.etComment.text?.toString().orEmpty())
    }

    private fun setupUpdateRecords() {
        binding.rvUpdateRecords.layoutManager = LinearLayoutManager(this)
        binding.rvUpdateRecords.adapter = updateRecordAdapter
    }

    private fun observeDetailViewModel() {
        detailViewModel.loading.observe(this) { loading ->
            binding.progressLoading.isVisible = loading == true
        }
        detailViewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        detailViewModel.refreshedKr.observe(this) { refreshed ->
            refreshed ?: return@observe
            krItem = refreshed
            bindSummary()
        }
        detailViewModel.comments.observe(this) { list ->
            commentAdapter.submitList(list)
            binding.tvNoComments.isVisible = list.isEmpty()
            binding.rvComments.isVisible = list.isNotEmpty()
            binding.tvCommentTitle.text = if (list.isEmpty()) "评论" else "评论（${list.size}）"
        }
        detailViewModel.updateRecords.observe(this) { list ->
            binding.sectionUpdateRecords.isVisible = list.isNotEmpty()
            updateRecordAdapter.submitList(list)
        }
        detailViewModel.commentSubmitted.observe(this) { done ->
            if (done != true) return@observe
            binding.etComment.text = null
            binding.scrollContent.post {
                binding.scrollContent.fullScroll(android.view.View.FOCUS_DOWN)
            }
            Toast.makeText(this, "评论已发送", Toast.LENGTH_SHORT).show()
        }
        detailViewModel.commentDeleted.observe(this) { done ->
            if (done != true) return@observe
            Toast.makeText(this, "评论已删除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.toolbar.updatePadding(top = statusBar.top)
            binding.layoutCommentInput.updatePadding(
                bottom = maxOf(navigationBar.bottom, ime.bottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    companion object {
        const val EXTRA_READ_ONLY = "extra_read_only"

        fun start(context: Context, item: GoalKrItem, readOnly: Boolean = false) {
            context.startActivity(
                Intent(context, KrDetailActivity::class.java).apply {
                    KrNavHelper.putExtra(this, item)
                    putExtra(EXTRA_READ_ONLY, readOnly)
                }
            )
        }
    }
}
