package com.fuusy.hiddendanger.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityKrDetailBinding
import com.fuusy.hiddendanger.ui.adapter.AttachmentAdapter
import com.fuusy.hiddendanger.ui.adapter.AttachmentItem
import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import com.fuusy.hiddendanger.ui.adapter.SimpleOptionAdapter
import com.fuusy.hiddendanger.ui.album.AlbumMediaItem
import com.fuusy.hiddendanger.ui.album.util.GridSpacingItemDecoration
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrNavHelper
import com.fuusy.hiddendanger.ui.model.KrProgressHelper
import com.fuusy.hiddendanger.util.AppDialogHelper
import com.fuusy.hiddendanger.viewmodel.KrUpdateProgressViewModel
import kotlin.math.roundToInt

class KrDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKrDetailBinding
    private val viewModel: KrUpdateProgressViewModel by viewModels()
    private lateinit var krItem: GoalKrItem
    private lateinit var attachmentAdapter: AttachmentAdapter

    private val customAlbumLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val selectStateMap =
            result.data?.getSerializableExtra("select_state_map") as? HashMap<String, Boolean>
        val selectedItems =
            result.data?.getParcelableArrayExtra("selected")?.filterIsInstance<AlbumMediaItem>()
        if (selectStateMap != null && selectedItems != null) {
            viewModel.setAttachments(
                selectedItems.filter { selectStateMap[it.id] == true }.map { it.path }
            )
        } else if (selectedItems != null) {
            viewModel.addAttachments(selectedItems.map { it.path })
        }
    }

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra("captured_images")?.let {
                viewModel.addAttachments(it)
            }
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

        binding = ActivityKrDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStatusBarPadding()

        viewModel.init(krItem)
        binding.btnBack.setOnClickListener { finish() }
        setupAttachments()
        setupUpdateForm()
        observeViewModel()
        bindSummary()
    }

    private fun bindSummary() {
        binding.tvKrTitle.text = krItem.title
        binding.tvObjectiveTitle.text = "所属目标：${krItem.objectiveTitle}"
        binding.tvKrValue.text = krItem.valueLabel
        binding.tvProgressPercent.text = "${krItem.progressPercent}%"
        binding.tvApprovalStatus.text = krItem.approvalLabel ?: "—"
        binding.tvApprovalStatus.isVisible = !krItem.approvalLabel.isNullOrBlank()

        val progressLabel = KrProgressHelper.progressStatusLabel(krItem)
        binding.tvProgressApproval.isVisible = progressLabel != null
        binding.tvProgressApproval.text = progressLabel

        binding.flProgress.post {
            val trackWidth = binding.flProgress.width
            if (trackWidth > 0) {
                binding.viewProgress.layoutParams.width =
                    (trackWidth * krItem.progressPercent / 100f).toInt().coerceAtLeast(0)
                binding.viewProgress.requestLayout()
            }
        }

        val canUpdate = KrProgressHelper.canUpdateProgress(krItem)
        binding.sectionUpdate.isVisible = canUpdate

        val blocked = KrProgressHelper.updateBlockedReason(krItem)
        binding.tvBlockedHint.isVisible = !canUpdate && blocked != null
        binding.tvBlockedHint.text = blocked

        if (canUpdate) {
            binding.tvValueHint.text = "目标：${formatTarget(krItem)}"
            setupProgressSlider(krItem)
        }
    }

    private fun setupUpdateForm() {
        binding.btnSubmit.setOnClickListener {
            AppDialogHelper.showConfirm(
                context = this,
                title = "提交进度",
                message = "提交后将由目标创建人审批，审批通过后进度才会生效",
                confirmText = "提交"
            ) {
                viewModel.submit(this, binding.etRemark.text?.toString())
            }
        }
    }

    private fun setupProgressSlider(item: GoalKrItem) {
        val maxProgress = item.targetValue.roundToInt().coerceAtLeast(1)
        val initial = item.currentValue.roundToInt().coerceIn(0, maxProgress)
        binding.seekProgress.max = maxProgress
        binding.seekProgress.progress = initial
        updateSliderLabel(initial.toDouble(), item.unit)
        viewModel.currentValue = initial.toDouble()

        binding.seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                viewModel.currentValue = progress.toDouble()
                updateSliderLabel(progress.toDouble(), item.unit)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun updateSliderLabel(value: Double, unit: String?) {
        val display = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        binding.tvSliderValue.text = if (!unit.isNullOrBlank()) "$display$unit" else display
    }

    private fun setupAttachments() {
        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { path -> viewModel.removeAttachment(path) },
            onAddClick = { showAttachmentOptions() },
            onItemClick = { _, _ -> }
        )
        binding.rvAttachments.apply {
            layoutManager = GridLayoutManager(this@KrDetailActivity, 4)
            adapter = attachmentAdapter
            val spacing = resources.getDimensionPixelSize(R.dimen.grid_spacing)
            addItemDecoration(GridSpacingItemDecoration(4, spacing, true))
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(this) { loading ->
            binding.progressLoading.isVisible = loading == true
            binding.btnSubmit.isEnabled = loading != true
        }
        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        viewModel.submitted.observe(this) { done ->
            if (done != true) return@observe
            Toast.makeText(this, "进度已提交，等待目标创建人审批", Toast.LENGTH_SHORT).show()
            viewModel.updatedItem.value?.let { updated ->
                krItem = updated
                setResult(RESULT_OK, Intent().apply { KrNavHelper.putExtra(this, updated) })
            }
            bindSummary()
        }
        viewModel.attachments.observe(this) { paths ->
            val items = mutableListOf<AttachmentItem>()
            paths.forEach { path ->
                items.add(
                    AttachmentItem.Media(
                        path = path,
                        type = if (path.contains("video", ignoreCase = true)) {
                            AttachmentItem.MediaType.VIDEO
                        } else {
                            AttachmentItem.MediaType.IMAGE
                        }
                    )
                )
            }
            if (paths.size < KrUpdateProgressViewModel.MAX_ATTACHMENTS) {
                items.add(AttachmentItem.AddButton)
            }
            attachmentAdapter.submitList(items)
        }
    }

    private fun showAttachmentOptions() {
        val dialog = Dialog(this, R.style.CustomDialog)
        val view = layoutInflater.inflate(R.layout.dialog_selector, null)
        dialog.setContentView(view)
        dialog.window?.apply {
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
        view.setBackgroundResource(R.drawable.bottom_sheet_background)
        val options = listOf(
            DynamicFormAdapter.OptionItem("album", "相册"),
            DynamicFormAdapter.OptionItem("camera", "拍照")
        )
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvOptions)?.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            adapter = SimpleOptionAdapter(options, null) { selected ->
                when (selected.value) {
                    "album" -> {
                        val intent = Intent(
                            this@KrDetailActivity,
                            com.fuusy.hiddendanger.ui.album.CustomAlbumActivity::class.java
                        )
                        intent.putExtra("mode", "select")
                        customAlbumLauncher.launch(intent)
                    }
                    "camera" -> {
                        cameraResultLauncher.launch(
                            Intent(this@KrDetailActivity, CameraActivity::class.java)
                        )
                    }
                }
                dialog.dismiss()
            }
        }
        view.findViewById<android.widget.TextView>(R.id.tvTitle)?.text = "添加附件"
        view.findViewById<android.widget.ImageView>(R.id.ivClose)?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun formatTarget(item: GoalKrItem): String {
        val unit = item.unit.orEmpty()
        val value = if (item.targetValue == item.targetValue.toLong().toDouble()) {
            item.targetValue.toLong().toString()
        } else {
            item.targetValue.toString()
        }
        return if (unit.isNotBlank()) "$value$unit" else value
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
        fun start(context: Context, item: GoalKrItem) {
            context.startActivity(
                Intent(context, KrDetailActivity::class.java).apply {
                    KrNavHelper.putExtra(this, item)
                }
            )
        }
    }
}
