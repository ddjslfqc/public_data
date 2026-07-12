package com.fuusy.hiddendanger.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import com.fuusy.common.utils.ToastUtil
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderOptions
import com.fuusy.common.data.Attachment
import com.fuusy.hiddendanger.data.WorkOrderMockForm
import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import com.fuusy.common.data.WorkOrderStatus
import java.util.Locale
import com.fuusy.common.data.local.AppDatabase
import com.fuusy.common.data.local.WorkOrderRepository
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.utils.SpUtils
import com.fuusy.project.workorder.MobileWorkOrderRepository
import com.fuusy.project.workorder.OptionItemDto
import com.fuusy.project.workorder.WorkOrderMapper
import com.fuusy.project.workorder.WorkOrderOptionsDto

class CreateWorkOrderViewModel(application: Application) : AndroidViewModel(application) {

    private val _formItemsLiveData = MutableLiveData<List<DynamicFormAdapter.FormItem>>()
    val formItemsLiveData: LiveData<List<DynamicFormAdapter.FormItem>> = _formItemsLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>()
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    private val _attachments = MutableLiveData<List<String?>>(emptyList())
    val attachments: LiveData<List<String?>> = _attachments

    // 新增：分别管理已上传的URL和当前选择的文件
    private val uploadedUrls = mutableSetOf<String>() // 已上传成功的URL集合
    private val currentSelectedFiles = mutableListOf<String?>() // 当前选择的文件集合（包含本地路径和URL）
    
    // 新增：本地文件路径到URL的映射
    private val localPathToUrlMap = mutableMapOf<String, String>() // 本地路径 -> URL
    private val urlToLocalPathMap = mutableMapOf<String, String>() // URL -> 本地路径

    // 表单验证错误
    private val _validationErrors = MutableLiveData<List<String>>()
    val validationErrors: LiveData<List<String>> = _validationErrors

    // 提交状态
    private val _submitStatus = MutableLiveData<SubmitStatus>()
    val submitStatus: LiveData<SubmitStatus> = _submitStatus

    // 表单结构加载状态
    private val _formLoadingStatus = MutableLiveData<FormLoadingStatus>()
    val formLoadingStatus: LiveData<FormLoadingStatus> = _formLoadingStatus

    // 下拉选择器数据 (这些可以用来填充 FormItem 的 options)
    val categoryOptions = WorkOrderOptions.hiddenDangerCategories
    val responsibleDepartmentOptions = WorkOrderOptions.responsibleDepartments

    // 新增：用于保留表单内容的Map
    private val formValueMap = mutableMapOf<String, String>()
    
    // 新增：保存新拍摄的文件URI，用于相册预选
    private var newCapturedFileUri: String? = null

    // 重新提交 / 编辑草稿时携带的工单ID
    private var resubmitId: String? = null
    private var draftEditId: String? = null
    private var lastSavedOrder: WorkOrderItem? = null

    fun setResubmitId(id: String?) { resubmitId = id }
    fun setDraftEditId(id: String?) { draftEditId = id }
    fun getLastSavedOrder(): WorkOrderItem? = lastSavedOrder

    // 新版 mobile/workorder 接口
    private val mobileWorkOrderRepo = MobileWorkOrderRepository()
    private var cachedOptions: WorkOrderOptionsDto? = null

    // 新增：本地数据库Repository
    private val db = AppDatabase.getInstance(application)
    private val localRepository = WorkOrderRepository(db)
    fun getUserInfo(): Map<String, String> {
        val userInfo = mapOf(
            "username" to (SpUtils.getString("user_name") ?: ""),
            "company" to (SpUtils.getString("user_company") ?: ""),
            "department" to (SpUtils.getString("user_department") ?: "")
        )
        // 更新LiveData
        _userInfoLiveData.value = userInfo
        return userInfo
    }
    
    // 刷新用户信息
    fun refreshUserInfo() {
        getUserInfo()
    }
    
    // 设置新拍摄的文件URI
    fun setNewCapturedFile(uri: String) {
        newCapturedFileUri = uri
        android.util.Log.d("CreateWorkOrderVM", "设置新拍摄文件: $uri")
    }
    
    // 获取新拍摄的文件URI
    fun getNewCapturedFile(): String? {
        return newCapturedFileUri
    }
    
    fun getCurrentSelectedFiles(): List<String> {
        return currentSelectedFiles.filterNotNull()
    }
    
    fun getUploadedUrls(): List<String> {
        return uploadedUrls.toList()
    }
    
    // 获取当前选中的项目信息
    private fun getCurrentProject(): Map<String, String> {
        val projectJson = SpUtils.getString("selected_project")
        if (projectJson.isNullOrEmpty()) {
            return emptyMap()
        }
        
        return try {
            val gson = Gson()
            val project = gson.fromJson(projectJson, Map::class.java)
            mapOf(
                "itemName" to (project["itemName"]?.toString() ?: ""),
                "item" to (project["item"]?.toString() ?: ""),
                "projectUnit" to (project["projectUnit"]?.toString() ?: ""),
                "address" to (project["address"]?.toString() ?: ""),
                "charge" to (project["charge"]?.toString() ?: ""),
                "phone" to (project["phone"]?.toString() ?: ""),
                "device" to (project["device"]?.toString() ?: ""),
                "jobContent" to (project["jobContent"]?.toString() ?: "")
            )
        } catch (e: Exception) {
            android.util.Log.e("CreateWorkOrderVM", "解析项目信息失败", e)
            emptyMap()
        }
    }
    
    // 生成隐患编号
    fun generateHiddenDangerNumber(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val today = dateFormat.format(java.util.Date())
        val random = (1000..9999).random()
        return "JS${today}${random}"
    }
    
    // 获取当前时间
    private fun getCurrentTime(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }

    sealed class SubmitStatus {
        object Idle : SubmitStatus()
        object Loading : SubmitStatus()
        object Success : SubmitStatus()
        data class Error(val message: String) : SubmitStatus()
    }

    sealed class FormLoadingStatus {
        object Idle : FormLoadingStatus()
        object Loading : FormLoadingStatus()
        object Success : FormLoadingStatus()
        data class Error(val message: String) : FormLoadingStatus()
    }

    fun fetchFormStructure() {
        viewModelScope.launch {
            try {
                _formLoadingStatus.value = FormLoadingStatus.Loading
                val options = mobileWorkOrderRepo.options().getOrNull()
                cachedOptions = options
                val userInfo = getUserInfo()
                val projectInfo = getCurrentProject()
                val filledItems = fillFormWithUserAndProjectData(
                    WorkOrderMockForm.formItems(options),
                    userInfo,
                    projectInfo
                ).map { item ->
                    when {
                        item.key == "priority" && item.value.isBlank() -> {
                            val defaultPriority = options?.priorities?.firstOrNull()
                            val code = defaultPriority?.value ?: "P1"
                            val label = defaultPriority?.label ?: code
                            item.copy(value = label).also { formValueMap["priority"] = code }
                        }
                        item.key == "rectificationPerson" && !formValueMap.containsKey("rectificationPerson") -> {
                            item.copy(value = "不指定处理人（公开抢单）").also {
                                formValueMap["rectificationPerson"] = WorkOrderOptions.PUBLIC_GRAB_PERSON_ID
                            }
                        }
                        else -> item
                    }
                }
                projectInfo["itemName"]?.takeIf { it.isNotBlank() }?.let { formValueMap["project"] = it }
                _formItemsLiveData.value = filledItems
                _formLoadingStatus.value = FormLoadingStatus.Success
            } catch (e: Exception) {
                android.util.Log.e("表单结构", "异常: ${e.message}", e)
                _formLoadingStatus.value = FormLoadingStatus.Error(e.message ?: "加载选项失败，请检查 /mobile/workorder/options")
            }
        }
    }
    
    // 自动填充表单数据
    private fun fillFormWithUserAndProjectData(
        items: List<DynamicFormAdapter.FormItem>,
        userInfo: Map<String, String>,
        projectInfo: Map<String, String>
    ): List<DynamicFormAdapter.FormItem> {
        val filledItems = items.toMutableList()
        
        // 遍历表单项，根据字段名自动填充
        filledItems.forEachIndexed { index, item ->
            // 添加调试日志，查看字段的实际key
            android.util.Log.d("表单填充", "处理字段: ${item.key} (原始值: '${item.value}')")
            
            val newValue = when (item.key.lowercase()) {
                "隐患编号", "hiddendangernumber", "danger_number" -> generateHiddenDangerNumber()
                "填报人", "reporter", "submit_user" -> userInfo["username"] ?: ""
                "公司", "company", "company_name" -> userInfo["company"] ?: ""
                "发现时间", "discovery_time", "found_time", "foundtime" -> getCurrentTime()
                "填报部门", "department", "submit_department" -> userInfo["department"] ?: ""
                "项目名称", "project_name", "projectname", "project" -> projectInfo["itemName"] ?: ""
                "项目编号", "project_id", "projectid" -> projectInfo["item"] ?: ""
                "项目单位", "project_unit", "projectunit" -> projectInfo["projectUnit"] ?: ""
                "地址", "address", "location" -> projectInfo["address"] ?: ""
                "负责人", "charge", "responsible_person" -> projectInfo["charge"] ?: ""
                "电话", "phone", "contact_phone" -> projectInfo["phone"] ?: ""
                "设备编号", "device", "device_id" -> projectInfo["device"] ?: ""
                "作业内容", "job_content", "jobcontent" -> projectInfo["jobContent"] ?: ""
                else -> item.value // 保持原值
            }
            
            // 更新表单项的值
            filledItems[index] = item.copy(value = newValue)
            
            // 同时更新formValueMap
            if (newValue.isNotEmpty()) {
                formValueMap[item.key] = newValue
                android.util.Log.d("表单填充", "自动填充字段: ${item.key} = '$newValue'")
            }
        }
        
        return filledItems
    }

    // 更新表单项的值
    fun updateFormItemValue(key: String, value: String) {
        formValueMap[key] = value
    }

    /** 下拉选择：formValueMap 存 code，表单项 value 存展示文案 */
    fun onSelectorChanged(key: String, code: String, displayLabel: String) {
        formValueMap[key] = code
        val items = _formItemsLiveData.value?.toMutableList() ?: return
        val idx = items.indexOfFirst { it.key == key }
        if (idx == -1) return
        items[idx] = items[idx].copy(value = displayLabel)
        _formItemsLiveData.value = items
        if (key == "responsibleDepartment") {
            onDepartmentSelected(code)
        }
    }

    fun onDepartmentSelected(deptId: String) {
        viewModelScope.launch {
            val users = if (deptId.isNotBlank()) {
                mobileWorkOrderRepo.users(deptId).getOrNull().orEmpty()
            } else {
                emptyList()
            }
            val personOptions = listOf(
                OptionItemDto(WorkOrderOptions.PUBLIC_GRAB_PERSON_ID, "不指定处理人（公开抢单）")
            ) + users
            updateFormItemOptions("rectificationPerson", personOptions)
        }
    }

    private fun updateFormItemOptions(key: String, options: List<OptionItemDto>) {
        val items = _formItemsLiveData.value?.toMutableList() ?: return
        val idx = items.indexOfFirst { it.key == key }
        if (idx == -1) return
        val old = items[idx]
        val currentCode = formValueMap[key]
        val matched = options.firstOrNull { it.value == currentCode }
        val selected = matched ?: options.firstOrNull()
        items[idx] = old.copy(
            options = options.map { DynamicFormAdapter.OptionItem(it.value, it.label) },
            value = selected?.label ?: ""
        )
        formValueMap[key] = selected?.value.orEmpty()
        _formItemsLiveData.value = items
    }

    private fun normalizeDateTimeDisplay(raw: String): String {
        if (raw.isBlank()) return ""
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm"
        )
        for (pattern in patterns) {
            try {
                val date = java.text.SimpleDateFormat(pattern, Locale.getDefault()).parse(raw.trim())
                if (date != null) {
                    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
                }
            } catch (_: Exception) {
            }
        }
        return raw
    }

    private fun refreshFormFieldValue(key: String, value: String) {
        val items = _formItemsLiveData.value ?: return
        _formItemsLiveData.value = items.map {
            if (it.key == key) it.copy(value = value) else it
        }
    }

    fun addAttachment(path: String) {
        android.util.Log.d("AttachmentDebug", "addAttachment: path=$path, before=$currentSelectedFiles")
        if (path.isBlank()) return
        if (!currentSelectedFiles.contains(path) && !uploadedUrls.contains(path)) {
            currentSelectedFiles.add(path)
            android.util.Log.d("AttachmentDebug", "addAttachment: after add, currentSelectedFiles=$currentSelectedFiles")
            updateAttachmentsDisplay()
        }
    }

    fun addAttachments(paths: List<String>) {
        android.util.Log.d("AttachmentDebug", "addAttachments: paths=$paths, before=$currentSelectedFiles")
        val uniquePaths = paths.filter { it.isNotBlank() && !currentSelectedFiles.contains(it) && !uploadedUrls.contains(it) }
        if (uniquePaths.isNotEmpty()) {
            currentSelectedFiles.addAll(uniquePaths)
            android.util.Log.d("AttachmentDebug", "addAttachments: after add, currentSelectedFiles=$currentSelectedFiles")
            updateAttachmentsDisplay()
        }
    }

    fun setAttachments(paths: List<String?>) {
        android.util.Log.d("AttachmentDebug", "setAttachments: paths=$paths, before=$currentSelectedFiles")
        
        // 获取所有已上传URL对应的本地路径
        val uploadedLocalPaths = uploadedUrls.mapNotNull { url -> urlToLocalPathMap[url] }.toSet()
        android.util.Log.d("AttachmentDebug", "setAttachments: uploadedLocalPaths=$uploadedLocalPaths")
        
        // 过滤：排除已上传的URL和已上传URL对应的本地路径
        val uniquePaths = paths.filter { it?.isNotBlank() == true }
            .distinct()
            .filter { path -> 
                !uploadedUrls.contains(path) && !uploadedLocalPaths.contains(path)
            }
        
        android.util.Log.d("AttachmentDebug", "setAttachments: filtered uniquePaths=$uniquePaths")
        
        currentSelectedFiles.clear()
        currentSelectedFiles.addAll(uniquePaths)
        android.util.Log.d("AttachmentDebug", "setAttachments: after set, currentSelectedFiles=$currentSelectedFiles")
        updateAttachmentsDisplay()
    }

    fun removeAttachment(path: String?) {
        android.util.Log.d("AttachmentDebug", "removeAttachment: path=$path, before currentSelectedFiles=$currentSelectedFiles uploadedUrls=$uploadedUrls")
        currentSelectedFiles.remove(path)
        uploadedUrls.remove(path)
        
        // 使用兼容的 API 21+ 方法替换 removeIf
        val localPathToUrlMapIterator = localPathToUrlMap.entries.iterator()
        while (localPathToUrlMapIterator.hasNext()) {
            val entry = localPathToUrlMapIterator.next()
            if (entry.key == path || entry.value == path) {
                localPathToUrlMapIterator.remove()
            }
        }
        
        val urlToLocalPathMapIterator = urlToLocalPathMap.entries.iterator()
        while (urlToLocalPathMapIterator.hasNext()) {
            val entry = urlToLocalPathMapIterator.next()
            if (entry.key == path || entry.value == path) {
                urlToLocalPathMapIterator.remove()
            }
        }
        
        android.util.Log.d("AttachmentDebug", "removeAttachment: after, currentSelectedFiles=$currentSelectedFiles uploadedUrls=$uploadedUrls")
        updateAttachmentsDisplay()
    }

    private fun updateAttachmentsDisplay() {
        val allAttachments = mutableListOf<String?>()
        
        // 1. 优先添加本地文件路径
        allAttachments.addAll(currentSelectedFiles)
        
        // 2. 对于已上传的URL，如果有对应的本地路径映射，使用本地路径；否则使用URL
        uploadedUrls.forEach { url ->
            val localPath = urlToLocalPathMap[url]
            if (localPath != null && !allAttachments.contains(localPath)) {
                // 有本地路径映射，使用本地路径
                allAttachments.add(localPath)
            } else if (!allAttachments.contains(url)) {
                // 没有本地路径映射，使用URL
                allAttachments.add(url)
            }
        }
        
        val finalAttachments = allAttachments.filter { it?.isNotBlank()==true }.distinct()
        android.util.Log.d("AttachmentDebug", "updateAttachmentsDisplay: currentSelectedFiles=$currentSelectedFiles uploadedUrls=$uploadedUrls finalAttachments=$finalAttachments")
        _attachments.value = finalAttachments
    }

    // 新增：标记文件为已上传成功
    fun markFileAsUploaded(localPath: String, uploadedUrl: String) {
        android.util.Log.d("AttachmentDebug", "markFileAsUploaded: localPath=$localPath, uploadedUrl=$uploadedUrl, before currentSelectedFiles=$currentSelectedFiles uploadedUrls=$uploadedUrls")
        currentSelectedFiles.remove(localPath)
        if (uploadedUrl.isNotBlank() && !uploadedUrls.contains(uploadedUrl)) {
            uploadedUrls.add(uploadedUrl)
        }
        localPathToUrlMap[localPath] = uploadedUrl
        urlToLocalPathMap[uploadedUrl] = localPath
        android.util.Log.d("AttachmentDebug", "markFileAsUploaded: after, currentSelectedFiles=$currentSelectedFiles uploadedUrls=$uploadedUrls")
        updateAttachmentsDisplay()
    }

    // 新增：获取所有附件（用于提交）
    fun getAllAttachments(): List<String?> {
        val allAttachments = mutableListOf<String?>()
        
        // 1. 优先添加本地文件路径
        allAttachments.addAll(currentSelectedFiles)
        
        // 2. 对于已上传的URL，如果有对应的本地路径映射，使用本地路径；否则使用URL
        uploadedUrls.forEach { url ->
            val localPath = urlToLocalPathMap[url]
            if (localPath != null && !allAttachments.contains(localPath)) {
                // 有本地路径映射，使用本地路径
                allAttachments.add(localPath)
            } else if (!allAttachments.contains(url)) {
                // 没有本地路径映射，使用URL
                allAttachments.add(url)
            }
        }

        val finalAttachments = allAttachments.filter { it?.isNotBlank() == true }.distinct()
        android.util.Log.d("AttachmentDebug", "getAllAttachments: result=$finalAttachments")
        return finalAttachments
    }

    // 新增：获取用于相册预选的本地文件路径
    fun getLocalPathsForAlbumSelection(): List<String> {
        val localPaths = mutableListOf<String>()
        
        // 添加当前选择的本地文件
        currentSelectedFiles.forEach { path ->
            if (path != null && !path.startsWith("http://") && !path.startsWith("https://")) {
                localPaths.add(path)
            }
        }
        
        // 添加已上传URL对应的本地文件路径
        uploadedUrls.forEach { url ->
            val localPath = urlToLocalPathMap[url]
            if (localPath != null) {
                localPaths.add(localPath)
            }
        }
        
        // 移除强制添加新拍摄文件的逻辑，让用户可以在相册中自由选择
        // 新拍摄的文件只有在用户主动选择时才会被包含
        
        val result = localPaths.distinct()
        android.util.Log.d("CreateWorkOrderVM", "相册预选路径列表: ${result.joinToString()}")
        return result
    }
    
    // 新增：预构建路径到ID的映射，避免在适配器中重复查询
    fun buildPathToIdMapping(): Map<String, String> {
        val mapping = mutableMapOf<String, String>()
        
        // 只对新拍摄的文件进行路径到ID的映射
        newCapturedFileUri?.let { newUri ->
            try {
                val uri = android.net.Uri.parse(newUri)
                val cursor = getApplication<Application>().contentResolver.query(
                    uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val path = it.getString(0)
                        mapping[path] = newUri
                        android.util.Log.d("CreateWorkOrderVM", "预构建映射: path=$path -> id=$newUri")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CreateWorkOrderVM", "预构建映射失败: ${e.message}")
            }
        }
        
        return mapping
    }

    // 新增：清理所有附件
    fun clearAllAttachments() {
        currentSelectedFiles.clear()
        uploadedUrls.clear()
        localPathToUrlMap.clear()
        urlToLocalPathMap.clear()
        updateAttachmentsDisplay()
    }

    // 新增：从草稿加载附件
    fun loadAttachmentsFromDraft(draft: WorkOrderItem) {
        clearAllAttachments()
        // 将草稿中的附件URL添加到已上传集合，过滤无效路径
        draft.attachments?.forEach { attachment ->
            val url = attachment?.url
            if (url != null && url.isNotBlank()) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // 过滤有效的URL
                    uploadedUrls.add(url)
                    // 对于草稿中的URL，我们可能没有对应的本地路径映射
                    // 这种情况下，URL会直接显示，但无法在相册中预选
                } else {
                    // 过滤有效的本地路径
                    val file = java.io.File(url)
                    if (file.exists() || url.startsWith("content://")) {
                        currentSelectedFiles.add(url)
                    }
                }
            }
        }
        updateAttachmentsDisplay()
    }

    private fun validateForm(): List<String> {
        val errors = mutableListOf<String>()
        
        // 获取当前表单结构，根据实际的isRequired配置进行验证
        val currentFormItems = _formItemsLiveData.value ?: emptyList()
        
        android.util.Log.d("表单验证", "开始验证表单，共${currentFormItems.size}个字段")
        
        // 创建字段名到标签的映射
        val labelMap = mapOf(
            "hiddenDangerName" to "工单名称",
            "workOrderType" to "类别",
            "hiddenDangerDescription" to "需求说明",
            "responsibleDepartment" to "处理部门",
            "rectificationPerson" to "处理人",
            "priority" to "优先级",
            "project" to "隶属项目",
            "expectedCompletionTime" to "期望完成时间"
        )
        
        // 根据表单的实际配置验证必填字段
        currentFormItems.forEach { formItem ->
            android.util.Log.d("表单验证", "检查字段: ${formItem.key} (${formItem.label}), isRequired=${formItem.isRequired}, value='${formValueMap[formItem.key]}'")
            
            if (formItem.isRequired) {
                val value = formValueMap[formItem.key]
                if (value.isNullOrBlank() || value == "请选择" || value == "请输入") {
                    val label = labelMap[formItem.key] ?: formItem.label
                    errors.add("${label}不能为空")
                    android.util.Log.d("表单验证", "字段 ${formItem.key} 验证失败: 值为空")
                }
            }
        }
        
        android.util.Log.d("表单验证", "验证完成，错误数量: ${errors.size}")
        return errors
    }

    private fun createWorkOrderFromFormItems(): WorkOrderItem? {
        val currentDate = SimpleDateFormat("yyyy/MM/dd").format(Date())
        return WorkOrderItem(
            hiddenDangerName = formValueMap["hiddenDangerName"] ?: "",
            hiddenDangerDescription = formValueMap["hiddenDangerDescription"] ?: "",
            hiddenDangerCategory = formValueMap["workOrderType"] ?: "",
            responsibleDepartment = formValueMap["responsibleDepartment"] ?: "",
            rectificationPersonId = formValueMap["rectificationPerson"] ?: "",
            projectName = formValueMap["project"] ?: "",
            submitTime = currentDate,
            status = WorkOrderStatus.PROCESSING
        )
    }

    // 仅从表单值构建提交JSON，确保字段与 /init 返回的 key 一致
    private fun buildPayloadJson(): String {
        val payload = mutableMapOf<String, Any>()
        // 直接使用当前的表单键值（值已在选择器中写入 code）
        formValueMap.forEach { (k, v) ->
            if (!v.isNullOrBlank()) payload[k] = v
        }
        // 重新提交时附带 id 字段
        resubmitId?.let { if (it.isNotBlank()) payload["id"] = it }
        return Gson().toJson(payload)
    }

    // 保存工单到本地数据库
    fun saveWorkOrderToLocal(context: Context, isDraft: Boolean = false, invoke: () -> Unit) {
        saveDraftToLocal(context, invoke)
    }

    private fun createWorkOrderFromFormItems(isDraft: Boolean = false): WorkOrderItem? {
        return WorkOrderItem(
            hiddenDangerName = formValueMap["hiddenDangerName"] ?: "",
            hiddenDangerDescription = formValueMap["hiddenDangerDescription"] ?: "",
            hiddenDangerCategory = formValueMap["workOrderType"] ?: "",
            responsibleDepartment = formValueMap["responsibleDepartment"] ?: "",
            rectificationPersonId = formValueMap["rectificationPerson"] ?: "",
            projectName = formValueMap["project"] ?: ""
        )
    }

    fun loadFormAndFillDraft(draft: WorkOrderItem) {
        viewModelScope.launch {
            draftEditId = draft.id.takeIf { it.isNotBlank() }
            val options = mobileWorkOrderRepo.options().getOrNull()
            cachedOptions = options

            val deptId = draft.responsibleDeptId ?: draft.responsibleDepartment.orEmpty()
            val users = if (deptId.isNotBlank()) {
                mobileWorkOrderRepo.users(deptId).getOrNull().orEmpty()
            } else {
                emptyList()
            }
            val personOptions = listOf(
                OptionItemDto(WorkOrderOptions.PUBLIC_GRAB_PERSON_ID, "不指定处理人（公开抢单）")
            ) + users
            val items = WorkOrderMockForm.formItems(options, personOptions)

            val userInfo = getUserInfo()
            val projectInfo = getCurrentProject()

            formValueMap["hiddenDangerName"] = draft.hiddenDangerName ?: ""
            formValueMap["workOrderType"] = draft.typeCode ?: draft.workOrderType ?: draft.hiddenDangerCategory ?: ""
            formValueMap["hiddenDangerDescription"] = draft.hiddenDangerDescription ?: ""
            formValueMap["responsibleDepartment"] = deptId
            formValueMap["rectificationPerson"] = when {
                WorkOrderOptions.isPublicGrabPerson(draft.rectificationPersonId) &&
                    WorkOrderOptions.isPublicGrabPerson(draft.responsiblePerson) ->
                    WorkOrderOptions.PUBLIC_GRAB_PERSON_ID
                draft.rectificationPersonId?.isNotBlank() == true &&
                    !WorkOrderOptions.isPublicGrabPerson(draft.rectificationPersonId) ->
                    draft.rectificationPersonId!!
                draft.responsiblePerson?.isNotBlank() == true &&
                    !WorkOrderOptions.isPublicGrabPerson(draft.responsiblePerson) ->
                    draft.responsiblePerson!!
                else -> WorkOrderOptions.PUBLIC_GRAB_PERSON_ID
            }
            formValueMap["project"] = draft.projectName ?: projectInfo["itemName"].orEmpty()
            formValueMap["priority"] = draft.priority?.takeIf { it.startsWith("P") }
                ?: WorkOrderOptions.priorityLabelToCode(draft.priority.orEmpty())
            formValueMap["expectedCompletionTime"] = draft.expectedCompleteTime ?: ""

            loadAttachmentsFromDraft(draft)

            val filledItems = fillFormWithUserAndProjectData(items, userInfo, projectInfo)
            val finalItems = filledItems.map { item ->
                when (item.key) {
                    "workOrderType" -> {
                        val code = formValueMap["workOrderType"].orEmpty()
                        val label = options?.types?.find { it.value == code }?.label ?: code
                        item.copy(value = label)
                    }
                    "responsibleDepartment" -> {
                        val code = formValueMap["responsibleDepartment"].orEmpty()
                        val label = options?.departments?.find { it.value == code }?.label ?: code
                        item.copy(value = label)
                    }
                    "rectificationPerson" -> {
                        val code = formValueMap["rectificationPerson"].orEmpty()
                        val label = personOptions.find { it.value == code }?.label
                            ?: if (code == WorkOrderOptions.PUBLIC_GRAB_PERSON_ID) {
                                "不指定处理人（公开抢单）"
                            } else {
                                code
                            }
                        item.copy(value = label)
                    }
                    "priority" -> {
                        val code = formValueMap["priority"].orEmpty()
                        val label = options?.priorities?.find { it.value == code }?.label
                            ?: WorkOrderOptions.priorityCodeToLabel(code)
                        item.copy(value = label)
                    }
                    "expectedCompletionTime" -> {
                        val raw = formValueMap["expectedCompletionTime"].orEmpty()
                        val display = normalizeDateTimeDisplay(raw)
                        item.copy(value = display)
                    }
                    else -> formValueMap[item.key]?.takeIf { it.isNotEmpty() }?.let { item.copy(value = it) } ?: item
                }
            }
            _formItemsLiveData.value = finalItems
        }
    }

//    fun fillFromDraft(draft: WorkOrderItem) {
//        formValueMap["hiddenDangerName"] = draft.hiddenDangerName
//        formValueMap["hiddenDangerDescription"] = draft.hiddenDangerDescription
//        formValueMap["reasonAnalysis"] = draft.reasonAnalysis
//        formValueMap["treatmentRequirement"] = draft.treatmentRequirement
//        formValueMap["hiddenDangerCategory"] = draft.hiddenDangerCategory
//        formValueMap["hiddenDangerLevel"] = draft.hiddenDangerLevel
//        formValueMap["affiliatedMajor"] = draft.profession
//        formValueMap["controlLevel"] = draft.controlLevel
//        formValueMap["unitSystem"] = draft.unitSystem
//        formValueMap["hazardConsequence"] = draft.hazardConsequence
//        formValueMap["possibility"] = draft.possibility
//        formValueMap["treatmentDifficulty"] = draft.treatmentDifficulty
//        formValueMap["responsibleDepartment"] = draft.responsibleDepartment
//        formValueMap["affiliatedMajor"] = draft.affiliatedMajor
//        formValueMap["responsiblePerson"] = draft.responsiblePerson
//        _attachments.value = draft.attachments.map { it.url }
//    }

//    fun deleteDraft(draft: WorkOrderItem) {
//        viewModelScope.launch {
//            remoteRepository.deleteWorkOrderById(draft.id)
//        }
//    }

//    // 批量上传附件，支持图片和视频，最多9条
//    fun uploadAttachments(paths: List<String>, onResult: (List<String>) -> Unit) {
//        viewModelScope.launch {
//            val resultUrls = mutableListOf<String>()
//            val limitedPaths = paths.take(9)
//
//            for (path in limitedPaths) {
//                if (path.startsWith("http://") || path.startsWith("https://")) {
//                    // 已经是URL，直接加入
//                    resultUrls.add(path)
//                } else {
//                    // 本地文件，需要上传
//                    try {
//                        val url = remoteRepository.uploadAttachment(path)
//                        if (!url.isNullOrBlank()) {
//                            resultUrls.add(url)
//                            // 标记文件为已上传成功
//                            markFileAsUploaded(path, url)
//                        }
//                    } catch (e: Exception) {
//                        // 可根据需要处理单个文件上传失败
//                        android.util.Log.e("UploadAttachments", "上传失败: $path, 错误: ${e.message}")
//                    }
//                }
//            }
//            onResult(resultUrls)
//        }
//    }

    // 工具方法：将 content:// uri 拷贝为本地临时文件
    private fun copyUriToTempFile(context: Context, uri: android.net.Uri, suffix: String = ""): java.io.File? {
        return try {
            val resolver = context.contentResolver
            val nameFromCursor = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val ext = when {
                !suffix.isNullOrBlank() -> suffix
                nameFromCursor?.substringAfterLast('.', missingDelimiterValue = "").orEmpty().isNotBlank() ->
                    "." + nameFromCursor!!.substringAfterLast('.')
                else -> {
                    val mime = resolver.getType(uri) ?: ""
                    when {
                        mime.startsWith("image/") -> ".jpg"
                        mime == "video/avi" -> ".avi"
                        mime == "video/mp4" -> ".mp4"
                        mime == "video/mp2t" -> ".ts"
                        mime == "video/x-matroska" -> ".mkv"
                        else -> ".dat"
                    }
                }
            }
            val tempFile = java.io.File.createTempFile("upload_", ext, context.cacheDir)
            resolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 自动串联：先上传附件，全部成功后再提交工单
    fun submitWorkOrderWithAttachments(context: Context, localPaths: List<String>, isDraft: Boolean = false, onResult: (Boolean, String?) -> Unit) {
        _submitStatus.value = SubmitStatus.Loading
        viewModelScope.launch {
            try {
                if (isDraft) {
                    saveDraftToLocal(context) { onResult(true, null) }
                    return@launch
                }

                val errors = validateForm()
                if (errors.isNotEmpty()) {
                    _validationErrors.value = errors
                    _submitStatus.value = SubmitStatus.Idle
                    onResult(false, errors.joinToString("\n"))
                    return@launch
                }

                submitViaMobileApi(context, localPaths, onResult)
            } catch (e: Exception) {
                _submitStatus.value = SubmitStatus.Error(e.message ?: "提交失败")
                onResult(false, e.message)
            }
        }
    }

    private suspend fun submitViaMobileApi(
        context: Context,
        localPaths: List<String>,
        onResult: (Boolean, String?) -> Unit
    ) {
        val projectInfo = getCurrentProject()
        if (formValueMap["project"].isNullOrBlank()) {
            projectInfo["itemName"]?.takeIf { it.isNotBlank() }?.let { formValueMap["project"] = it }
        }
        val createBody = WorkOrderMapper.buildCreateRequest(
            form = formValueMap,
            projectName = projectInfo["itemName"]
        )
        if (createBody.title.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("工单名称不能为空")
            onResult(false, "工单名称不能为空")
            return
        }
        if (createBody.brief.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("需求说明不能为空")
            onResult(false, "需求说明不能为空")
            return
        }
        if (createBody.typeCode.isBlank() || createBody.responsibleDept.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("请选择类别和处理部门")
            onResult(false, "请选择类别和处理部门")
            return
        }

        val attachmentFiles = mutableListOf<java.io.File>()
        val allAttachments = getAllAttachments().take(9)
        for (path in allAttachments.filterNotNull()) {
            if (path.startsWith("http://") || path.startsWith("https://")) continue
            val file = if (path.startsWith("content://")) {
                copyUriToTempFile(context, android.net.Uri.parse(path))
            } else {
                java.io.File(path).takeIf { it.exists() }
            }
            if (file != null) attachmentFiles.add(file)
        }

        val createResult = mobileWorkOrderRepo.create(createBody, attachmentFiles)
        if (createResult.isFailure) {
            val msg = createResult.exceptionOrNull()?.message ?: "创建失败"
            _submitStatus.value = SubmitStatus.Error(msg)
            onResult(false, msg)
            return
        }
        val workOrderId = createResult.getOrNull()?.id.orEmpty()
        if (workOrderId.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("创建失败：未返回工单ID")
            onResult(false, "创建失败")
            return
        }

        _submitStatus.value = SubmitStatus.Success
        val msg = if (attachmentFiles.isEmpty()) "工单提交成功" else "工单提交成功（含 ${attachmentFiles.size} 个附件）"
        ToastUtil.showCustomToast(context, msg)
        onResult(true, null)
    }

    fun saveDraftToLocal(context: Context, onSaved: () -> Unit) {
        fun getCurrentTimeString(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        }
        val workOrderItem = createWorkOrderFromFormItems(isDraft = true)?.copy(
            status = WorkOrderStatus.DRAFT,
            submitTime = getCurrentTimeString()
        ) ?: return
        val formJson = Gson().toJson(workOrderItem)
        val attachments = _attachments.value.orEmpty().filterNotNull().map {
            val type = if (it.endsWith(".mp4", true) || it.endsWith(".mov", true)) "video" else "image"
            it to type
        }
        viewModelScope.launch {
            localRepository.saveWorkOrderWithStatus(formJson, attachments, WorkOrderStatus.DRAFT)
            ToastUtil.showCustomToast(context, "草稿已保存至本地")
            onSaved()
        }
    }
}