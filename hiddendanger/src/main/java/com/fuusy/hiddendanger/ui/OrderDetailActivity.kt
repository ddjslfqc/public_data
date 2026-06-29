package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.common.utils.LoadingUtils
import com.fuusy.common.utils.ToastUtil
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.SimpleAttachment
import com.fuusy.hiddendanger.databinding.ActivityOrderDetailBinding
import com.fuusy.hiddendanger.ui.adapter.SimpleAttachmentAdapter
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.entity.LocalMedia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Route(path = "/hiddendanger/OrderDetailActivity")
class OrderDetailActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private val viewModel: OrderDetailViewModel by viewModels()
    private var simpleAttachmentAdapter: SimpleAttachmentAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        binding.btnBack.setOnClickListener { finish() }

        viewModel.workOrder.observe(this) { item ->
            bindStatusStyle(item.status)
            bindPriorityTags(item.priority)
            bindAttachments(item)
            renderBottomActions(item)
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { ToastUtil.showCustomToast(this, it) }
        }
        viewModel.loading.observe(this) { loading ->
            if (loading == true) LoadingUtils.showLoading(this) else LoadingUtils.hideLoading()
        }

        val incoming = intent.getSerializableExtra("workOrder") as? WorkOrderItem
        if (incoming == null) {
            finish()
            return
        }
        loadWorkOrder(incoming.id)
    }

    override fun onResume() {
        super.onResume()
        viewModel.workOrder.value?.id?.let { loadWorkOrder(it) }
    }

    private fun loadWorkOrder(id: String) {
        viewModel.loadDetail(id)
    }

    private fun bindStatusStyle(status: WorkOrderStatus) {
        val tvStatus = binding.tvStatus
        val (bgRes, textColor) = when (status) {
            WorkOrderStatus.DRAFT -> R.drawable.bg_status_tag to Color.parseColor("#898FA0")
            WorkOrderStatus.PENDING -> R.drawable.bg_status_tag to Color.parseColor("#F97316")
            WorkOrderStatus.SUBMITTED -> R.drawable.bg_status_tag to Color.parseColor("#1465EB")
            WorkOrderStatus.REJECT -> R.drawable.bg_status_reject to Color.parseColor("#D6413F")
            WorkOrderStatus.PROCESSING -> R.drawable.bg_status_tag_processing to Color.parseColor("#6366F1")
            WorkOrderStatus.EVAL -> R.drawable.bg_status_tag to Color.parseColor("#8B5CF6")
            WorkOrderStatus.COMPLETED -> R.drawable.bg_status_done to Color.parseColor("#00AA60")
            WorkOrderStatus.CANCELLED -> R.drawable.bg_status_tag to Color.parseColor("#898FA0")
        }
        tvStatus.setBackgroundResource(bgRes)
        tvStatus.setTextColor(textColor)
    }

    private fun bindPriorityTags(priority: String?) {
        val tagViews = listOf(binding.tvMetaPriority, binding.tvBasicPriority)
        if (priority.isNullOrBlank()) {
            tagViews.forEach { it.visibility = View.GONE }
            return
        }
        val (bgColor, textColor) = when (priority) {
            "P0" -> Color.parseColor("#33EB1919") to Color.parseColor("#EB1919")
            "P1" -> Color.parseColor("#33EA9300") to Color.parseColor("#EA9300")
            "P2" -> Color.parseColor("#33F59E0B") to Color.parseColor("#F59E0B")
            "P3" -> Color.parseColor("#3300AA60") to Color.parseColor("#00AA60")
            else -> Color.parseColor("#33898FA0") to Color.parseColor("#898FA0")
        }
        tagViews.forEach { tv ->
            tv.visibility = View.VISIBLE
            tv.background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(bgColor)
            }
            tv.setTextColor(textColor)
        }
    }

    private fun bindAttachments(item: WorkOrderItem) {
        val simpleAttachmentList = item.attachments?.mapNotNull { att ->
            att ?: return@mapNotNull null
            SimpleAttachment(
                fileName = att.fileName.orEmpty(),
                fileSize = att.size.orEmpty(),
                fileUrl = att.url.orEmpty(),
                isVideo = att.fileName?.endsWith(".mp4", true) == true
                    || att.fileName?.endsWith(".mov", true) == true
            )
        }.orEmpty()

        val hasAttachments = simpleAttachmentList.isNotEmpty()
        binding.tvAttachmentTitle.isVisible = hasAttachments
        binding.cardView.isVisible = hasAttachments
        if (!hasAttachments) return

        simpleAttachmentAdapter = SimpleAttachmentAdapter(
            simpleAttachmentList,
            onDownloadClick = { },
            onItemClick = { _, position ->
                val mediaList = simpleAttachmentList.map {
                    LocalMedia().apply {
                        path = it.fileUrl
                        mimeType = if (it.isVideo) "video/mp4" else "image/jpeg"
                    }
                }
                PictureSelector.create(this).openPreview()
                    .setImageEngine(com.fuusy.hiddendanger.util.GlideEngine())
                    .startActivityPreview(position, false, ArrayList(mediaList))
            }
        )
        binding.rvSimpleAttachments.layoutManager = LinearLayoutManager(this)
        binding.rvSimpleAttachments.adapter = simpleAttachmentAdapter
    }

    private fun renderBottomActions(item: WorkOrderItem) {
        binding.bottomBar.removeAllViews()
        when (item.status) {
            WorkOrderStatus.DRAFT -> {
                binding.bottomBar.isVisible = true
                addActionButton("撤回", style = ButtonStyle.GHOST) { confirmWithdraw(item) }
                addActionButton("提交", style = ButtonStyle.PRIMARY) { confirmSubmit(item) }
            }
            WorkOrderStatus.PENDING, WorkOrderStatus.SUBMITTED -> {
                binding.bottomBar.isVisible = true
                addActionButton("认领工单", style = ButtonStyle.ORANGE, fullWidth = true) {
                    confirmClaim(item)
                }
            }
            WorkOrderStatus.REJECT -> {
                binding.bottomBar.isVisible = true
                addActionButton("修改后重新提交", style = ButtonStyle.PRIMARY, fullWidth = true) {
                    resubmit(item)
                }
            }
            WorkOrderStatus.PROCESSING -> {
                binding.bottomBar.isVisible = true
                addActionButton("驳回", style = ButtonStyle.GHOST) { showRejectDialog(item) }
                addActionButton("完成", style = ButtonStyle.SUCCESS) { showCompleteDialog(item) }
            }
            WorkOrderStatus.EVAL -> {
                binding.bottomBar.isVisible = true
                addActionButton("去评价", style = ButtonStyle.PURPLE, fullWidth = true) {
                    showEvaluateDialog(item)
                }
            }
            WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED -> {
                binding.bottomBar.isVisible = false
            }
        }
    }

    private enum class ButtonStyle { GHOST, PRIMARY, ORANGE, SUCCESS, PURPLE }

    private fun addActionButton(
        text: String,
        style: ButtonStyle,
        fullWidth: Boolean = false,
        onClick: () -> Unit
    ) {
        val btn = TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                if (fullWidth) 1f else 1f
            ).apply {
                if (binding.bottomBar.childCount > 0) marginStart = dp(8)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                when (style) {
                    ButtonStyle.GHOST -> {
                        setColor(Color.WHITE)
                        setStroke(dp(1), Color.parseColor("#E5E7EB"))
                        setTextColor(Color.parseColor("#666666"))
                    }
                    ButtonStyle.PRIMARY -> {
                        setColor(Color.parseColor("#1465EB"))
                        setTextColor(Color.WHITE)
                    }
                    ButtonStyle.ORANGE -> {
                        setColor(Color.parseColor("#F97316"))
                        setTextColor(Color.WHITE)
                    }
                    ButtonStyle.SUCCESS -> {
                        setColor(Color.parseColor("#00AA60"))
                        setTextColor(Color.WHITE)
                    }
                    ButtonStyle.PURPLE -> {
                        setColor(Color.parseColor("#8B5CF6"))
                        setTextColor(Color.WHITE)
                    }
                }
            }
            setOnClickListener { onClick() }
        }
        binding.bottomBar.addView(btn)
    }

    private fun confirmSubmit(item: WorkOrderItem) {
        resubmit(item)
    }

    private fun confirmWithdraw(item: WorkOrderItem) {
        ToastUtil.showCustomToast(this, "撤回请在后端配置后接入")
    }

    private fun confirmClaim(item: WorkOrderItem) {
        AlertDialog.Builder(this)
            .setTitle("确认认领工单？")
            .setMessage("认领后将进入处理中状态，请及时处理")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认认领") { _, _ ->
                runApprove(item, pass = true, opinion = "认领工单")
            }
            .show()
    }

    private fun showRejectDialog(item: WorkOrderItem) {
        showInputDialog("驳回工单", "驳回原因", required = true) { reason ->
            runApprove(item, pass = false, opinion = reason)
        }
    }

    private fun showCompleteDialog(item: WorkOrderItem) {
        showInputDialog("完成工单", "完成说明", required = true) { desc ->
            runApprove(item, pass = true, opinion = desc)
        }
    }

    private fun showEvaluateDialog(item: WorkOrderItem) {
        AlertDialog.Builder(this)
            .setTitle("评价工单")
            .setMessage("确认提交评价？")
            .setNegativeButton("取消", null)
            .setPositiveButton("提交评价") { _, _ ->
                runApprove(item, pass = true, opinion = "评价通过")
            }
            .show()
    }

    private fun runApprove(item: WorkOrderItem, pass: Boolean, opinion: String?) {
        viewModel.approve(item.id, pass, opinion) { ok, msg ->
            if (ok) {
                ToastUtil.showCustomToast(this@OrderDetailActivity, "操作成功")
            } else {
                ToastUtil.showCustomToast(this@OrderDetailActivity, msg ?: "操作失败")
            }
        }
    }

    private fun resubmit(item: WorkOrderItem) {
        val intent = Intent(this, CreateWorkOrderActivity::class.java)
        intent.putExtra("draft_data", item)
        intent.putExtra("is_resubmit", true)
        intent.putExtra("resubmit_id", item.id)
        startActivity(intent)
    }

    private fun showInputDialog(
        title: String,
        label: String,
        required: Boolean = false,
        onConfirm: (String) -> Unit
    ) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reject_reason, null)
        val editText = view.findViewById<EditText>(R.id.et_reject_reason)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                val text = editText.text?.toString()?.trim().orEmpty()
                if (required && text.isBlank()) {
                    ToastUtil.showCustomToast(this, "请填写内容")
                    return@setPositiveButton
                }
                onConfirm(text)
            }
            .show()
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
