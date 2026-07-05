package com.fuusy.hiddendanger.ui

import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityCreateWorkOrderBinding
import com.fuusy.hiddendanger.ui.adapter.AttachmentAdapter
import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import com.fuusy.hiddendanger.ui.adapter.SimpleOptionAdapter
import android.util.Log
import java.io.File
import java.util.*
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.entity.LocalMedia
import com.fuusy.hiddendanger.ui.album.util.GridSpacingItemDecoration
import com.fuusy.hiddendanger.ui.adapter.AttachmentItem
import com.fuusy.hiddendanger.ui.album.AlbumMediaItem
import com.luck.picture.lib.interfaces.OnExternalPreviewEventListener
import com.fuusy.common.utils.ToastUtil
import android.widget.TextView
import android.content.Context
import android.app.Dialog
import android.view.View
import android.graphics.Color
import android.widget.LinearLayout
import com.bigkoo.pickerview.builder.TimePickerBuilder
import java.text.SimpleDateFormat

@Route(path = "/hiddendanger/CreateWorkOrderActivity")
class CreateWorkOrderActivity : BaseVmActivity<ActivityCreateWorkOrderBinding>() {

    private val viewModel: CreateWorkOrderViewModel by viewModels()
    private lateinit var attachmentAdapter: AttachmentAdapter

    private lateinit var formAdapter: DynamicFormAdapter

    private val MAX_ATTACHMENT_COUNT = 9

    private val customAlbumLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectStateMap =
                result.data?.getSerializableExtra("select_state_map") as? HashMap<String, Boolean>
            val selectedItems =
                result.data?.getParcelableArrayExtra("selected")?.filterIsInstance<AlbumMediaItem>()
            if (selectStateMap != null && selectedItems != null) {
                // 只保留选中为true的item
                val selectedPaths =
                    selectedItems.filter { selectStateMap[it.id] == true }.map { it.path }
                android.util.Log.d(
                    "CreateWorkOrderActivity", "相册返回的选中路径: ${selectedPaths.joinToString()}"
                )

                // 使用新的逻辑：设置当前选择的文件，但保留已上传的URL
                viewModel.setAttachments(selectedPaths)
            } else if (selectedItems != null) {
                val paths = selectedItems.map { it.path }
                android.util.Log.d(
                    "CreateWorkOrderActivity", "相册返回的路径: ${paths.joinToString()}"
                )
                viewModel.addAttachments(paths)
            }
        }
    }

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val images = result.data?.getStringArrayListExtra("captured_images")
            val isNewCaptured = result.data?.getBooleanExtra("is_new_captured", false) ?: false
            val capturedUri = result.data?.getStringExtra("captured_uri")

            images?.let {
                viewModel.addAttachments(it)

                // 如果是新拍摄的文件，保存到ViewModel中，用于相册预选
                if (isNewCaptured && capturedUri != null) {
                    viewModel.setNewCapturedFile(capturedUri)
                }
            }
        }
    }

    override fun getLayoutId(): Int = R.layout.activity_create_work_order

    override fun initData() {
        val draft =
            intent.getSerializableExtra("draft_data") as? com.fuusy.common.data.WorkOrderItem
        val isResubmit = intent.getBooleanExtra("is_resubmit", false)
        val resubmitIdExtra = intent.getStringExtra("resubmit_id")

        if (draft != null) {
            // 只回显有效的图片/视频路径，防止无效路径导致闪退
            val validAttachments = draft.attachments?.mapNotNull { it?.url }
                ?.filter { it.startsWith("content://") || File(it).exists() } ?: emptyList()
            viewModel.setAttachments(validAttachments)
            viewModel.loadFormAndFillDraft(draft)
            if (isResubmit) {
                viewModel.setResubmitId(resubmitIdExtra)
            }

            // 如果是重新提交，修改标题并显示驳回理由
            if (isResubmit) {
                showRejectionReasonCard(draft)
            }
        } else {
            viewModel.fetchFormStructure()
        }

        // 根据是否重新提交调整提交按钮文案
        mBinding.btnSubmit.text = if (isResubmit) "重新提交" else "提交工单"

        // 设置基本信息（无论是否从草稿加载都需要设置）
        setupBasicInfo()
        mBinding.viewModel = viewModel
        mBinding.lifecycleOwner = this

        // 刷新用户信息
        viewModel.refreshUserInfo()

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupToolbar() {
        mBinding.btnBack.setOnClickListener {
            handleBackAction()
        }
        mBinding.draftIcon.setOnClickListener {
            startActivity(Intent(this@CreateWorkOrderActivity, DraftBoxActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        attachmentAdapter = AttachmentAdapter(onDeleteClick = { path ->
            viewModel.removeAttachment(path)
        }, onAddClick = {
            showAttachmentOptions()
        }, onItemClick = { item, position ->
            // 统一用PictureSelector预览图片和视频
            val attachments = viewModel.attachments.value?.filterNotNull() ?: emptyList()
            openPictureSelectorPreview(attachments, position)
        })

        mBinding.rvAttachments.apply {
            layoutManager = GridLayoutManager(this@CreateWorkOrderActivity, 4)
            adapter = attachmentAdapter
            setHasFixedSize(true)
            val spacingInPixels = resources.getDimensionPixelSize(R.dimen.grid_spacing)
            addItemDecoration(GridSpacingItemDecoration(4, spacingInPixels, true))
        }

        formAdapter = DynamicFormAdapter(mutableListOf(),
            onSelectorClick = { formItem, _ ->
                when (formItem.key) {
                    "expectedCompletionTime" -> showDateTimePicker(formItem)
                    else -> showSelectorDialog(formItem)
                }
            },
            onInputChanged = { key, value -> viewModel.updateFormItemValue(key, value) })
        mBinding.rvDynamicForm.apply {
            layoutManager = LinearLayoutManager(this@CreateWorkOrderActivity)
            adapter = formAdapter
        }
    }

    private fun setupBasicInfo() {
        mBinding.cardBasicInfo.visibility = View.GONE
    }

    private fun updateBasicInfo(userInfo: Map<String, String>) {
        // 更新基本信息显示
        mBinding.tvReporter.text = userInfo["username"] ?: "admin"
        mBinding.tvCompany.text = userInfo["company"] ?: "悦城"
        mBinding.tvDepartment.text = userInfo["department"] ?: "安监管理部"
    }

    private fun setupSpinners() {
        // 替换为获取动态表单结构
        viewModel.fetchFormStructure()
    }

    private fun setupObservers() {
        // 观察表单数据变化 - 只在筛选器变化时刷新
        viewModel.formItemsLiveData.observe(this) { formItems ->
            android.util.Log.d("CreateWorkOrder", "表单数据更新: ${formItems.size} 项")
            formItems.forEach { item ->
                android.util.Log.d("CreateWorkOrder", "表单项: ${item.label} = '${item.value}'")
            }
            formAdapter.updateData(formItems)
        }

        // 观察用户信息变化，更新基本信息
        viewModel.userInfoLiveData.observe(this) { userInfo ->
            updateBasicInfo(userInfo)
        }

        // 观察附件列表变化
        viewModel.attachments.observe(this) { attachments ->
            val attachmentItems = mutableListOf<AttachmentItem>()
            attachments?.filterNotNull()?.forEach { path ->
                val type = getMediaType(path)
                Log.d("AttachmentType", "path=$path, type=$type")
                attachmentItems.add(AttachmentItem.Media(path, type))
            }
            if (attachmentItems.size < MAX_ATTACHMENT_COUNT) {
                attachmentItems.add(AttachmentItem.AddButton)
            }
            attachmentAdapter.submitList(attachmentItems)
        }

        // 观察表单验证结果
        viewModel.validationErrors.observe(this) { errors ->
            showValidationErrors(errors)
        }

        // 观察提交状态
        viewModel.submitStatus.observe(this) { status ->
            when (status) {
                is CreateWorkOrderViewModel.SubmitStatus.Idle -> {
                    dismissLoading()
                }

                is CreateWorkOrderViewModel.SubmitStatus.Loading -> {
                    showLoading()
                }

                is CreateWorkOrderViewModel.SubmitStatus.Success -> {
                    dismissLoading()
                }

                is CreateWorkOrderViewModel.SubmitStatus.Error -> {
                    dismissLoading()
                    ToastUtil.showCustomToast(this, "工单创建失败：${status.message}")
                }
            }
        }

        // 观察表单结构加载状态
        viewModel.formLoadingStatus.observe(this) { status ->
            when (status) {
                is CreateWorkOrderViewModel.FormLoadingStatus.Idle -> {
                    dismissLoading()
                }

                is CreateWorkOrderViewModel.FormLoadingStatus.Loading -> {
                    showLoading()
                }

                is CreateWorkOrderViewModel.FormLoadingStatus.Success -> {
                    dismissLoading()
                }

                is CreateWorkOrderViewModel.FormLoadingStatus.Error -> {
                    dismissLoading()
                    ToastUtil.showCustomToast(this, "加载表单结构失败：${status.message}")
                }
            }
        }
    }

    private fun setupClickListeners() {
        mBinding.btnDraftSave.setOnClickListener {
            viewModel.saveDraftToLocal(this) {
                navigateAfterSave(isDraft = true)
            }
        }

        mBinding.btnSubmit.setOnClickListener {
            val localPaths = viewModel.attachments.value?.filterNotNull() ?: emptyList()
            viewModel.submitWorkOrderWithAttachments(
                this, localPaths, isDraft = false
            ) { success, msg ->
                if (success) {
                    navigateAfterSave(isDraft = false)
                } else {
                    ToastUtil.showCustomToast(this, msg ?: "提交失败")
                }
            }
        }
    }

    private fun navigateAfterSave(isDraft: Boolean) {
        val saved = viewModel.getLastSavedOrder() ?: run {
            finish()
            return
        }
        if (isDraft) {
            finish()
            return
        }
        com.alibaba.android.arouter.launcher.ARouter.getInstance()
            .build("/hiddendanger/OrderDetailActivity")
            .withSerializable("workOrder", saved)
            .navigation()
        finish()
    }

    /**
     * 显示驳回理由卡片
     */
    private fun showRejectionReasonCard(draft: com.fuusy.common.data.WorkOrderItem) {
        // 显示驳回理由卡片
        mBinding.cardRejectionReason.visibility = android.view.View.VISIBLE

        // 获取驳回理由（这里需要从工单数据中获取，暂时使用示例数据）
        val rejectionReason = getRejectionReason(draft)
        mBinding.tvRejectionReason.text = rejectionReason

        android.util.Log.d("CreateWorkOrderActivity", "显示驳回理由卡片，理由: $rejectionReason")
    }

    /**
     * 获取驳回理由
     * 这里需要根据实际的工单数据结构来获取驳回理由
     */
    private fun getRejectionReason(draft: com.fuusy.common.data.WorkOrderItem): String {
        return draft.rejectionReason?.takeIf { it.isNotBlank() } ?: "未填写驳回原因"
    }

    private fun toLocalMedia(item: String): LocalMedia? {
        // 检查是否是URL
        val isUrl = item.startsWith("http://") || item.startsWith("https://")
        val isContentUri = item.startsWith("content://")

        if (!isUrl && !isContentUri) {
            // 对于本地文件路径，检查文件是否存在
            val file = java.io.File(item)
            if (!file.exists()) {
                Log.e("ToLocalMedia", "File not exist: $item")
                return null
            }
        }

        val mimeType = when {
            isUrl -> {
                // 对于URL，根据文件扩展名判断类型
                when {
                    item.endsWith(".mp4", true) || item.endsWith(
                        ".mov", true
                    ) || item.endsWith(".avi", true) -> "video/mp4"

                    item.endsWith(".jpg", true) || item.endsWith(
                        ".jpeg", true
                    ) || item.endsWith(".png", true) -> "image/jpeg"

                    else -> "image/jpeg" // 默认为图片
                }
            }

            isContentUri -> {
                try {
                    contentResolver.getType(Uri.parse(item)) ?: "image/jpeg"
                } catch (e: Exception) {
                    "image/jpeg"
                }
            }

            else -> {
                if (item.endsWith(".mp4", true) || item.endsWith(
                        ".mov", true
                    )
                ) "video/mp4" else "image/jpeg"
            }
        }

        val localMedia = LocalMedia()
        localMedia.path = item
        localMedia.mimeType = mimeType

        // 对于视频，避免在主线程使用 MediaMetadataRetriever 获取时长，避免卡顿
        if (mimeType.startsWith("video")) {
            localMedia.duration = 0L
        }

        Log.d(
            "ToLocalMedia",
            "Converting: $item, isUrl=$isUrl, LocalMedia -> path: ${localMedia.path}, mimeType: ${localMedia.mimeType}, duration: ${localMedia.duration}"
        )
        return localMedia
    }

    private fun openPictureSelectorPreview(mediaList: List<String>, position: Int) {
        Log.d(
            "PictureSelector",
            "openPictureSelectorPreview, position=$position, totalItems=${mediaList.size}"
        )
        Log.d("PictureSelector", "所有媒体列表: ${mediaList.joinToString()}")

        val currentItem = mediaList.getOrNull(position)
        Log.d("PictureSelector", "当前选中项: $currentItem")

        // 验证URL是否可访问
        val urlItems = mediaList.filter { it.startsWith("http://") || it.startsWith("https://") }
        if (urlItems.isNotEmpty()) {
            Log.d("PictureSelector", "发现URL项目: ${urlItems.joinToString()}")
            // 可以在这里添加URL可访问性检查
        }

        // 转换所有媒体项
        val localMediaList = mediaList.mapNotNull { path ->
            val localMedia = toLocalMedia(path)
            if (localMedia == null) {
                Log.e("PictureSelector", "无法转换媒体项: $path")
            } else {
                Log.d("PictureSelector", "成功转换媒体项: $path -> ${localMedia.path}")
            }
            localMedia
        }

        Log.d("PictureSelector", "转换后的LocalMedia列表大小: ${localMediaList.size}")
        localMediaList.forEachIndexed { index, media ->
            Log.d(
                "PictureSelector",
                "LocalMedia[$index]: path=${media.path}, mimeType=${media.mimeType}"
            )
        }

        if (localMediaList.isEmpty()) {
            ToastUtil.showCustomToast(this, "没有可预览的图片或视频")
            return
        }

        // 确保position在有效范围内
        val adjustedPosition = if (position >= localMediaList.size) 0 else position
        Log.d("PictureSelector", "调整后的position: $adjustedPosition")

        PictureSelector.create(this).openPreview()
            .setImageEngine(com.fuusy.hiddendanger.util.GlideEngine())
            .setExternalPreviewEventListener(object : OnExternalPreviewEventListener {
                override fun onPreviewDelete(currentPosition: Int) {
                    // 处理在预览界面删除图片/视频的逻辑
                    val updatedList = viewModel.attachments.value?.filterNotNull()?.toMutableList()
                        ?: mutableListOf()
                    if (currentPosition >= 0 && currentPosition < updatedList.size) {
                        updatedList.removeAt(currentPosition)
                        viewModel.setAttachments(updatedList)
                    }
                }

                override fun onLongPressDownload(
                    context: android.content.Context?, media: LocalMedia?
                ): Boolean {
                    // 长按下载事件，这里可以根据需要实现
                    return false
                }
            }).startActivityPreview(
                adjustedPosition, true, localMediaList.toMutableList() as ArrayList<LocalMedia>
            )
    }

    private fun showAttachmentOptions() {
        // 使用普通 Dialog 而不是 AlertDialog，与 showSelectorDialog 保持一致
        val dialog = Dialog(this, R.style.CustomDialog)
        val view = layoutInflater.inflate(R.layout.dialog_selector, null)
        dialog.setContentView(view)

        // 设置对话框位置和大小，与 showSelectorDialog 保持一致
        dialog.window?.apply {
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        // 设置自定义背景，确保圆角生效
        view.setBackgroundResource(R.drawable.bottom_sheet_background)

        // 获取视图组件
        val rvOptions = view.findViewById<RecyclerView>(R.id.rvOptions)
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvTitle)
        val ivClose = view.findViewById<android.widget.ImageView>(R.id.ivClose)

        // 设置标题
        tvTitle?.text = "添加附件"

        // 创建附件选项数据
        val attachmentOptions = listOf(
            DynamicFormAdapter.OptionItem("album", "相册"),
            DynamicFormAdapter.OptionItem("camera", "拍照")
        )

        // 设置适配器
        val adapter = SimpleOptionAdapter(
            options = attachmentOptions,
            selectedValue = null,
            onItemClick = { selectedOption ->
                when (selectedOption.value) {
                    "album" -> {
                        // 相册逻辑
                        val localPaths = viewModel.getLocalPathsForAlbumSelection()
                        android.util.Log.d(
                            "CreateWorkOrderActivity",
                            "用于相册预选的本地文件路径: ${localPaths.joinToString()}"
                        )

                        // 统一所有附件id为MediaStore content uri
                        val selectedIds = localPaths.mapNotNull { path ->
                            if (path.startsWith("content://")) {
                                // 如果是content URI，需要转换为file格式以匹配相册列表
                                if (path.contains("/video/media/")) {
                                    // 将video/media格式转换为file格式
                                    val id = path.substringAfterLast("/")
                                    "content://media/external/file/$id"
                                } else {
                                    path
                                }
                            } else {
                                getMediaStoreContentUri(this, path)
                            }
                        }.toTypedArray()

                        android.util.Log.d("CreateWorkOrderActivity", "=== 相册预选调试信息 ===")
                        android.util.Log.d(
                            "CreateWorkOrderActivity",
                            "localPaths原始列表: ${localPaths.joinToString()}"
                        )
                        android.util.Log.d(
                            "CreateWorkOrderActivity",
                            "传递给相册的selectedIds: ${selectedIds.joinToString()}"
                        )
                        android.util.Log.d(
                            "CreateWorkOrderActivity",
                            "新拍摄文件URI: ${viewModel.getNewCapturedFile()}"
                        )
                        android.util.Log.d(
                            "CreateWorkOrderActivity",
                            "当前已选文件: ${viewModel.getCurrentSelectedFiles().joinToString()}"
                        )
                        android.util.Log.d(
                            "CreateWorkOrderActivity",
                            "已上传URL: ${viewModel.getUploadedUrls().joinToString()}"
                        )
                        android.util.Log.d("CreateWorkOrderActivity", "=== 调试信息结束 ===")
                        // 预构建路径到ID的映射，避免在适配器中重复查询
                        val pathToIdMapping = viewModel.buildPathToIdMapping()
                        android.util.Log.d(
                            "CreateWorkOrderActivity", "预构建的路径映射: $pathToIdMapping"
                        )

                        val intent = Intent(
                            this, com.fuusy.hiddendanger.ui.album.CustomAlbumActivity::class.java
                        )
                        intent.putExtra("mode", "select")
                        intent.putExtra("selected_ids", selectedIds)
                        intent.putExtra(
                            "path_to_id_mapping", pathToIdMapping as HashMap<String, String>
                        )
                        customAlbumLauncher.launch(intent)
                    }
                    "camera" -> {
                        startCamera()
                    }
                }
                dialog.dismiss()
            }
        )

        rvOptions?.apply {
            layoutManager = LinearLayoutManager(this@CreateWorkOrderActivity)
            this.adapter = adapter
        }

        // 设置关闭按钮
        ivClose?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun startCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraResultLauncher.launch(intent)
    }

    private fun showValidationErrors(errors: List<String>) {
        if (errors.isNotEmpty()) {
            val errorMessage = errors.joinToString("\n")
            ToastUtil.showCustomToast(this, errorMessage)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackAction()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        handleBackAction()
    }

    private fun showDraftDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_draft_tip, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialog)
            .setView(dialogView).setCancelable(false).create()

        dialogView.findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialogView.findViewById<TextView>(R.id.btnSave).setOnClickListener {
            dialog.dismiss()
            viewModel.saveDraftToLocal(this) {
                finish()
            }
        }
        dialog.show()
    }

    /**
     * 检查是否有数据需要保存
     * @return true 如果有表单数据或附件，false 如果没有数据
     */
    private fun hasDataToSave(): Boolean {
        val formItems = viewModel.formItemsLiveData.value
        val attachments = viewModel.attachments.value

        android.util.Log.d("hasDataToSave", "=== 开始检查是否有数据需要保存 ===")
        android.util.Log.d("hasDataToSave", "formItems: $formItems")
        android.util.Log.d("hasDataToSave", "attachments: $attachments")

        val hasFormData = formItems?.any {
            val hasValue = it.value.isNotEmpty() && it.value != "请选择" && it.value != "请输入"
            android.util.Log.d(
                "hasDataToSave",
                "表单项 ${it.key} (${it.label}): 值='${it.value}', 是否有效=$hasValue"
            )
            hasValue
        } == true

        val hasAttachments = attachments?.isNotEmpty() == true

        android.util.Log.d("hasDataToSave", "hasFormData: $hasFormData")
        android.util.Log.d("hasDataToSave", "hasAttachments: $hasAttachments")
        android.util.Log.d("hasDataToSave", "最终结果: ${hasFormData || hasAttachments}")
        android.util.Log.d("hasDataToSave", "=== 检查结束 ===")

        return hasFormData || hasAttachments
    }

    /**
     * 根据数据状态决定是显示弹窗还是直接退出
     */
    private fun handleBackAction() {
        android.util.Log.d("handleBackAction", "=== 开始处理返回操作 ===")
        val hasData = hasDataToSave()
        android.util.Log.d("handleBackAction", "hasDataToSave() 返回: $hasData")

        if (hasData) {
            // 有数据时显示保存提示弹窗
            android.util.Log.d("handleBackAction", "有数据，显示保存提示弹窗")
            showDraftDialog()
        } else {
            // 没有数据时直接退出
            android.util.Log.d("handleBackAction", "没有数据，直接退出")
            finish()
        }
        android.util.Log.d("handleBackAction", "=== 处理返回操作结束 ===")
    }

    private fun showDateTimePicker(formItem: DynamicFormAdapter.FormItem) {
        val calendar = parseDateTime(formItem.value)
        buildWheelDateTimePicker(
            title = "选择期望完成时间",
            calendar = calendar
        ) { picked ->
            val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(picked.time)
            viewModel.onSelectorChanged(formItem.key, formatted, formatted)
        }.show()
    }

    private fun buildWheelDateTimePicker(
        title: String,
        calendar: Calendar,
        onConfirm: (Calendar) -> Unit
    ) = TimePickerBuilder(this) { date, _ ->
        val picked = Calendar.getInstance().apply {
            time = date
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        onConfirm(picked)
    }
        .setType(booleanArrayOf(true, true, true, true, true, false))
        .setLabel("年", "月", "日", "时", "分", "")
        .setCancelText("取消")
        .setSubmitText("确定")
        .setTitleText(title)
        .setOutSideCancelable(true)
        .isCyclic(true)
        .setTitleColor(Color.BLACK)
        .setSubmitColor(Color.parseColor("#1465EB"))
        .setCancelColor(Color.parseColor("#666666"))
        .setTitleBgColor(Color.WHITE)
        .setBgColor(Color.WHITE)
        .setDate(calendar)
        .isCenterLabel(false)
        .setTextColorCenter(Color.parseColor("#1465EB"))
        .setTextColorOut(Color.parseColor("#999999"))
        .setContentTextSize(20)
        .setSubCalSize(14)
        .setTitleSize(17)
        .setLineSpacingMultiplier(2.2f)
        .build()

    private fun parseDateTime(raw: String): Calendar {
        val calendar = Calendar.getInstance()
        val value = raw.trim()
        if (value.isBlank() || value == "请选择期望完成时间") return calendar
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm"
        )
        for (pattern in patterns) {
            try {
                SimpleDateFormat(pattern, Locale.getDefault()).parse(value)?.let {
                    calendar.time = it
                    return calendar
                }
            } catch (_: Exception) {
            }
        }
        return calendar
    }

    private fun showSelectorDialog(formItem: DynamicFormAdapter.FormItem) {
        if (formItem.options.isNullOrEmpty()) {
            ToastUtil.showCustomToast(this, "暂无选项")
            return
        }

        // 使用普通 Dialog 而不是 BottomSheetDialog
        val dialog = Dialog(this, R.style.CustomDialog)
        val view = layoutInflater.inflate(R.layout.dialog_selector, null)
        dialog.setContentView(view)

        // 设置对话框位置和大小
        dialog.window?.apply {
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        // 设置自定义背景，确保圆角生效
        view.setBackgroundResource(R.drawable.bottom_sheet_background)

        // 获取视图组件
        val rvOptions = view.findViewById<RecyclerView>(R.id.rvOptions)
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvTitle)
        val ivClose = view.findViewById<android.widget.ImageView>(R.id.ivClose)

        // 设置标题
        tvTitle?.text = formItem.label

        // 设置适配器，传入当前选中的值
        val adapter = SimpleOptionAdapter(options = formItem.options,
            selectedValue = formItem.options.firstOrNull { it.value == formItem.value || it.label == formItem.value },
            onItemClick = { selectedOption ->
                viewModel.onSelectorChanged(
                    formItem.key,
                    selectedOption.value,
                    selectedOption.label
                )
                dialog.dismiss()
            })

        rvOptions?.apply {
            layoutManager = LinearLayoutManager(this@CreateWorkOrderActivity)
            this.adapter = adapter
        }

        // 设置关闭按钮
        ivClose?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // 新增：根据路径和MIME type判断图片/视频类型
    private fun getMediaType(path: String): AttachmentItem.MediaType {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> {
                // 对于URL，根据文件扩展名判断类型
                when {
                    path.endsWith(".mp4", true) || path.endsWith(
                        ".mov", true
                    ) || path.endsWith(".avi", true) -> AttachmentItem.MediaType.VIDEO

                    path.endsWith(".jpg", true) || path.endsWith(
                        ".jpeg", true
                    ) || path.endsWith(".png", true) || path.endsWith(
                        ".gif", true
                    ) -> AttachmentItem.MediaType.IMAGE

                    else -> AttachmentItem.MediaType.IMAGE // 默认为图片
                }
            }

            path.startsWith("content://") -> {
                val mimeType = try {
                    contentResolver.getType(Uri.parse(path)) ?: ""
                } catch (e: Exception) {
                    ""
                }
                if (mimeType.startsWith("video")) AttachmentItem.MediaType.VIDEO else AttachmentItem.MediaType.IMAGE
            }

            else -> {
                if (path.endsWith(".mp4", true) || path.endsWith(
                        ".mov", true
                    )
                ) AttachmentItem.MediaType.VIDEO else AttachmentItem.MediaType.IMAGE
            }
        }
    }

    // 新增：file path转MediaStore content uri
    private fun getMediaStoreContentUri(context: Context, filePath: String): String? {
        val projection = arrayOf(
            android.provider.MediaStore.Files.FileColumns._ID
        )
        val selection = android.provider.MediaStore.Files.FileColumns.DATA + "=?"
        val cursor = context.contentResolver.query(
            android.provider.MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            arrayOf(filePath),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id =
                    it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns._ID))
                return android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Files.getContentUri(
                        "external"
                    ), id
                ).toString()
            }
        }
        // 回退方案：按文件名+修改时间匹配最近的一条
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) return null
            val name = file.name
            val projection2 = arrayOf(
                android.provider.MediaStore.Files.FileColumns._ID,
                android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
            )
            val selection2 = android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + "=?"
            val cursor2 = context.contentResolver.query(
                android.provider.MediaStore.Files.getContentUri("external"),
                projection2,
                selection2,
                arrayOf(name),
                android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            )
            var bestId: Long? = null
            var bestDiff = Long.MAX_VALUE
            val targetSec = file.lastModified() / 1000
            cursor2?.use { c ->
                while (c.moveToNext()) {
                    val id =
                        c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns._ID))
                    val modified =
                        c.getLong(c.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED))
                    val diff = kotlin.math.abs(modified - targetSec)
                    if (diff < bestDiff) {
                        bestDiff = diff
                        bestId = id
                    }
                    // 提前停止：在阈值内直接返回
                    if (diff <= 120) break
                }
            }
            bestId?.let { id ->
                android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Files.getContentUri(
                        "external"
                    ), id
                ).toString()
            }
        } catch (_: Exception) {
            null
        }
    }
}