package com.fuusy.hiddendanger.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderOptions
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.common.network.UserIdProvider
import com.fuusy.common.utils.LoadingUtils
import com.fuusy.common.utils.ToastUtil
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.SimpleAttachment
import com.fuusy.hiddendanger.databinding.ActivityOrderDetailBinding
import com.fuusy.hiddendanger.ui.adapter.SimpleAttachmentAdapter
import com.fuusy.hiddendanger.ui.adapter.WorkOrderOperationAdapter
import com.fuusy.hiddendanger.util.AppDialogHelper
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
    private val operationAdapter = WorkOrderOperationAdapter()

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
            bindOperationRecords()
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
        val (bgColor, textColor) = when (status) {
            WorkOrderStatus.DRAFT -> Color.parseColor("#33898FA0") to Color.parseColor("#898FA0")
            WorkOrderStatus.PENDING -> Color.parseColor("#33F97316") to Color.parseColor("#F97316")
            WorkOrderStatus.REJECT -> Color.parseColor("#1AD6413F") to Color.parseColor("#D6413F")
            WorkOrderStatus.PROCESSING -> Color.parseColor("#331365EC") to Color.parseColor("#1365EC")
            WorkOrderStatus.EVAL -> Color.parseColor("#338B5CF6") to Color.parseColor("#8B5CF6")
            WorkOrderStatus.COMPLETED -> Color.parseColor("#3300AA60") to Color.parseColor("#00AA60")
        }
        tvStatus.background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(bgColor)
        }
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
                id = att.id,
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
            },
            onLongClick = { att ->
                val attachmentId = att.id ?: return@SimpleAttachmentAdapter
                AppDialogHelper.showConfirm(
                    this,
                    title = "删除附件",
                    message = "确定删除该附件吗？",
                    confirmText = "删除"
                ) {
                    viewModel.deleteAttachment(item.id, attachmentId) { ok, msg ->
                        if (!ok) ToastUtil.showCustomToast(this@OrderDetailActivity, msg ?: "删除失败")
                    }
                }
            }
        )
        binding.rvSimpleAttachments.layoutManager = LinearLayoutManager(this)
        binding.rvSimpleAttachments.adapter = simpleAttachmentAdapter
    }

    private fun bindOperationRecords() {
        val records = viewModel.operationRecords()
        val hasRecords = records.isNotEmpty()
        binding.cardOperationRecords.isVisible = hasRecords
        if (!hasRecords) return
        if (binding.rvOperationRecords.adapter == null) {
            binding.rvOperationRecords.layoutManager = LinearLayoutManager(this)
            binding.rvOperationRecords.adapter = operationAdapter
        }
        operationAdapter.submitList(records)
    }

    private fun renderBottomActions(item: WorkOrderItem) {
        binding.bottomBar.removeAllViews()
        val me = UserIdProvider.current()?.toString()?.trim().orEmpty()
        val isHandler = me.isNotEmpty() &&
            !WorkOrderOptions.isPublicGrabPerson(item.rectificationPersonId) &&
            me == item.rectificationPersonId?.trim()
        val isCreator = me.isNotEmpty() && me == item.recordCreatorId?.trim()

        when (item.status) {
            WorkOrderStatus.DRAFT -> {
                if (isCreator) {
                    binding.bottomBar.isVisible = true
                    addActionButton("撤回", style = ButtonStyle.SECONDARY) { confirmWithdraw(item) }
                    addActionButton("提交", style = ButtonStyle.PRIMARY) { confirmSubmit(item) }
                } else {
                    binding.bottomBar.isVisible = false
                }
            }
            WorkOrderStatus.PENDING -> {
                binding.bottomBar.isVisible = true
                addActionButton("认领工单", style = ButtonStyle.PRIMARY, fullWidth = true) {
                    confirmClaim(item)
                }
            }
            WorkOrderStatus.REJECT -> {
                if (isCreator) {
                    binding.bottomBar.isVisible = true
                    addActionButton("修改后重新提交", style = ButtonStyle.PRIMARY, fullWidth = true) {
                        resubmit(item)
                    }
                } else {
                    binding.bottomBar.isVisible = false
                }
            }
            WorkOrderStatus.PROCESSING -> {
                if (isHandler) {
                    binding.bottomBar.isVisible = true
                    addActionButton("驳回", style = ButtonStyle.SECONDARY) { showRejectDialog(item) }
                    addActionButton("提交处理结果", style = ButtonStyle.PRIMARY) { showCompleteDialog(item) }
                } else {
                    binding.bottomBar.isVisible = false
                }
            }
            WorkOrderStatus.EVAL -> {
                if (isCreator) {
                    binding.bottomBar.isVisible = true
                    addActionButton("去评价", style = ButtonStyle.PRIMARY, fullWidth = true) {
                        showEvaluateDialog(item)
                    }
                } else {
                    binding.bottomBar.isVisible = false
                }
            }
            WorkOrderStatus.COMPLETED -> {
                binding.bottomBar.isVisible = false
            }
        }
    }

    private enum class ButtonStyle { SECONDARY, PRIMARY }

    private fun addActionButton(
        text: String,
        style: ButtonStyle,
        fullWidth: Boolean = false,
        onClick: () -> Unit
    ) {
        val isPrimary = style == ButtonStyle.PRIMARY
        val btn = TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(46),
                when {
                    fullWidth -> 1f
                    isPrimary -> 1.5f
                    else -> 1f
                }
            ).apply {
                if (binding.bottomBar.childCount > 0) marginStart = dp(8)
            }
            if (isPrimary) {
                setBackgroundResource(R.drawable.bg_goal_btn_primary)
                setTextColor(Color.WHITE)
            } else {
                setBackgroundResource(R.drawable.bg_goal_btn_secondary)
                setTextColor(Color.parseColor("#7F8495"))
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
        AppDialogHelper.showConfirm(
            this,
            title = "确认认领工单？",
            message = "认领后将进入处理中状态，请及时处理",
            confirmText = "确认认领"
        ) {
            viewModel.claim(item.id) { ok, msg ->
                if (ok) {
                    ToastUtil.showCustomToast(this@OrderDetailActivity, "认领成功")
                } else {
                    ToastUtil.showCustomToast(this@OrderDetailActivity, msg ?: "认领失败")
                }
            }
        }
    }

    private fun showRejectDialog(item: WorkOrderItem) {
        AppDialogHelper.showInput(
            context = this,
            title = "驳回工单",
            label = "请输入驳回原因：",
            hint = "请输入驳回原因...",
            required = true,
            confirmText = "确认"
        ) { reason ->
            viewModel.reject(item.id, reason) { ok, msg ->
                if (ok) {
                    ToastUtil.showCustomToast(this@OrderDetailActivity, "已驳回")
                } else {
                    ToastUtil.showCustomToast(this@OrderDetailActivity, msg ?: "驳回失败")
                }
            }
        }
    }

    private fun showCompleteDialog(item: WorkOrderItem) {
        AppDialogHelper.showInput(
            context = this,
            title = "提交处理结果",
            label = "请输入处理说明：",
            hint = "请描述处理情况...",
            required = true,
            confirmText = "提交"
        ) { desc ->
            runApprove(item, pass = true, opinion = desc)
        }
    }

    private fun showEvaluateDialog(item: WorkOrderItem) {
        val options = arrayOf("5星 - 非常满意", "4星 - 满意", "3星 - 一般", "2星 - 较差", "1星 - 很差")
        AlertDialog.Builder(this)
            .setTitle("评价工单")
            .setItems(options) { _, which ->
                val score = 5 - which
                AppDialogHelper.showInput(
                    context = this,
                    title = "评价说明",
                    label = "可选填写评价内容：",
                    hint = "处理质量、响应速度等...",
                    required = false,
                    confirmText = "提交评价"
                ) { content ->
                    viewModel.evaluate(item.id, score, content) { ok, msg ->
                        if (ok) {
                            ToastUtil.showCustomToast(this@OrderDetailActivity, "评价成功")
                        } else {
                            ToastUtil.showCustomToast(this@OrderDetailActivity, msg ?: "评价失败")
                        }
                    }
                }
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

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
