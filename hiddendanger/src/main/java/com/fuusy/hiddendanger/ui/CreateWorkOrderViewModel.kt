package com.fuusy.hiddendanger.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import com.fuusy.common.utils.ToastUtil
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderMockStore
import com.fuusy.common.data.WorkOrderOptions
import com.fuusy.common.data.Attachment
import com.fuusy.hiddendanger.data.RemoteWork
import com.fuusy.hiddendanger.data.WorkOrderMockForm
import com.fuusy.hiddendanger.data.WorkOrderApiService
import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import com.fuusy.hiddendanger.ui.adapter.FormItemDeserializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import com.fuusy.common.data.WorkOrderStatus
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import com.fuusy.common.data.local.AppDatabase
import com.fuusy.common.data.local.WorkOrderRepository
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.utils.SpUtils
import com.google.gson.reflect.TypeToken
import com.fuusy.project.workorder.CreateWorkOrderRequest
import com.fuusy.project.workorder.MobileWorkOrderRepository

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
    val levelOptions = WorkOrderOptions.hiddenDangerLevels
    val professionOptions = WorkOrderOptions.professions
    val controlLevelOptions = WorkOrderOptions.controlLevels
    val unitSystemOptions = WorkOrderOptions.unitSystems
    val harmConsequenceOptions = WorkOrderOptions.harmConsequences
    val possibilityOptions = WorkOrderOptions.possibilities
    val treatmentDifficultyOptions = WorkOrderOptions.treatmentDifficulties
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

    // 只用网络Repository
    private val remoteRepository: RemoteWork
    
    // 获取用户信息
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

    // 新增：本地数据库Repository
    private val db = AppDatabase.getInstance(application)
    private val localRepository = WorkOrderRepository(db)

    init {
        val gson = GsonBuilder()
            .registerTypeAdapter(DynamicFormAdapter.FormItem::class.java, FormItemDeserializer())
            .create()
        
        // 创建 OkHttp 客户端，禁用重定向
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(false) // 禁用重定向，解决 "Too many follow-up requests" 问题
            .addInterceptor(DetailedLoggingInterceptor())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerConfig.getWorkOrderBaseUrl()) // 使用全局服务器配置
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val api = retrofit.create(WorkOrderApiService::class.java)
        remoteRepository = RemoteWork(api)
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
                val userInfo = getUserInfo()
                val projectInfo = getCurrentProject()
                val filledItems = fillFormWithUserAndProjectData(
                    WorkOrderMockForm.formItems(options),
                    userInfo,
                    projectInfo
                ).map { item ->
                    if (item.key == "priority" && item.value.isBlank()) {
                        val defaultPriority = options?.priorities?.firstOrNull()?.value ?: "P1"
                        item.copy(value = defaultPriority).also { formValueMap["priority"] = defaultPriority }
                    } else item
                }
                _formItemsLiveData.value = filledItems
                _formLoadingStatus.value = FormLoadingStatus.Success
            } catch (e: Exception) {
                android.util.Log.e("表单结构", "异常: ${e.message}", e)
                _formItemsLiveData.value = WorkOrderMockForm.formItems()
                _formLoadingStatus.value = FormLoadingStatus.Error(e.message ?: "加载表单结构失败")
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
                "项目名称", "project_name", "projectname" -> projectInfo["itemName"] ?: ""
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
        if (key == "workOrderType" && ServerConfig.isMockData) {
            WorkOrderOptions.typeToDepartment[value]?.let { dept ->
                formValueMap["responsibleDepartment"] = dept
                refreshFormFieldValue("responsibleDepartment", dept)
            }
        }
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

    suspend fun submitWorkOrder() {
        _submitStatus.value = SubmitStatus.Loading

        val errors = validateForm()
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            _submitStatus.value = SubmitStatus.Idle // 或者 SubmitStatus.Error("请检查表单填写")
            return
        }

        try {
            // 模拟网络请求
            delay(2000)

            val workOrder = createWorkOrderFromFormItems() // 修改为从 FormItems 创建
            // 这里应该调用实际的API提交数据
            // repository.submitWorkOrder(workOrder)

            _submitStatus.value = SubmitStatus.Success
        } catch (e: Exception) {
            _submitStatus.value = SubmitStatus.Error(e.message ?: "提交失败")
        }
    }

    private fun validateForm(): List<String> {
        val errors = mutableListOf<String>()
        
        // 获取当前表单结构，根据实际的isRequired配置进行验证
        val currentFormItems = _formItemsLiveData.value ?: emptyList()
        
        android.util.Log.d("表单验证", "开始验证表单，共${currentFormItems.size}个字段")
        
        // 创建字段名到标签的映射
        val labelMap = mapOf(
            "hiddenDangerName" to "工单名称",
            "workOrderType" to "类型",
            "hiddenDangerDescription" to "需求说明",
            "responsibleDepartment" to "处理人部门",
            "responsiblePerson" to "处理人",
            "priority" to "优先级",
            "reasonAnalysis" to "原因简析",
            "treatmentRequirement" to "治理要求",
            "hiddenDangerCategory" to "类型",
            "hiddenDangerLevel" to "优先级",
            "affiliatedMajor" to "所属专业",
            "controlLevel" to "管控等级",
            "unitSystem" to "机组/系统",
            "hazardConsequence" to "危害后果",
            "possibility" to "可能性",
            "treatmentDifficulty" to "治理难度",
            "responsibleDepartment" to "负责部门",
            "responsiblePerson" to "负责人",
            "approvalModel" to "审批类型"
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
        // 直接用formValueMap组装WorkOrderItem
        val currentDate = SimpleDateFormat("yyyy/MM/dd").format(Date())
        return WorkOrderItem(
            hiddenDangerName = formValueMap["hiddenDangerName"] ?: "",
            hiddenDangerDescription = formValueMap["hiddenDangerDescription"] ?: "",
            reasonAnalysis = formValueMap["reasonAnalysis"] ?: "",
            treatmentRequirement = formValueMap["treatmentRequirement"] ?: "",
            hiddenDangerCategory = formValueMap["hiddenDangerCategory"] ?: "",
            hiddenDangerLevel = formValueMap["hiddenDangerLevel"] ?: "",
            profession = formValueMap["affiliatedMajor"] ?: "",
            controlLevel = formValueMap["controlLevel"] ?: "",
            unitSystem = formValueMap["unitSystem"] ?: "",
            hazardConsequence = formValueMap["hazardConsequence"] ?: "",
            possibility = formValueMap["possibility"] ?: "",
            treatmentDifficulty = formValueMap["treatmentDifficulty"] ?: "",
            responsibleDepartment = formValueMap["responsibleDepartment"] ?: "",
            responsiblePerson = formValueMap["responsiblePerson"] ?: "",
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
        val errors = if (isDraft) emptyList<String>() else validateForm()
        if (errors.isNotEmpty()) {
            ToastUtil.showCustomToast(context, errors.joinToString("\n"))
            return
        }
        val formJson = buildPayloadJson()
        val attachments = _attachments.value.orEmpty().filterNotNull().map {
            val type =
                if (it.endsWith(".mp4", true) || it.endsWith(".mov", true)) "video" else "image"
            it to type
        }
        viewModelScope.launch {
            remoteRepository.saveWorkOrder(formJson, attachments)
            ToastUtil.showCustomToast(context, if (isDraft) "草稿已保存" else "工单已保存")
            invoke.invoke()
        }
    }

    private fun createWorkOrderFromFormItems(isDraft: Boolean = false): WorkOrderItem? {
        val currentDate = SimpleDateFormat("yyyy/MM/dd").format(Date())
        return WorkOrderItem(
            hiddenDangerName = formValueMap["hiddenDangerName"] ?: "",
            hiddenDangerDescription = formValueMap["hiddenDangerDescription"] ?: "",
            reasonAnalysis = formValueMap["reasonAnalysis"] ?: "",
            treatmentRequirement = formValueMap["treatmentRequirement"] ?: "",
            hiddenDangerCategory = formValueMap["hiddenDangerCategory"] ?: "",
            hiddenDangerLevel = formValueMap["hiddenDangerLevel"] ?: "",
            // profession 参数在接口返回数据中不存在，注释掉
            // profession = formValueMap["profession"] ?: "",
            controlLevel = formValueMap["controlLevel"] ?: "",
            unitSystem = formValueMap["unitSystem"] ?: "",
            // 将 harmConsequence 修正为 hazardConsequence
            hazardConsequence = formValueMap["hazardConsequence"] ?: "",
            possibility = formValueMap["possibility"] ?: "",
            treatmentDifficulty = formValueMap["treatmentDifficulty"] ?: "",
            responsibleDepartment = formValueMap["responsibleDepartment"] ?: "",
            responsiblePerson = formValueMap["responsiblePerson"] ?: "",
            // submitTime 参数在接口返回数据中不存在，注释掉
            // submitTime = currentDate,
            // status 参数在接口返回数据中不存在，注释掉
            // status = if (isDraft) WorkOrderStatus.DRAFT else WorkOrderStatus.PROCESSING
            // 添加接口返回的 affiliatedMajor 参数
            affiliatedMajor = formValueMap["affiliatedMajor"] ?: "",
        )
    }

    fun loadFormAndFillDraft(draft: WorkOrderItem) {
        viewModelScope.launch {
            draftEditId = draft.id.takeIf { it.isNotBlank() }
            val items = if (ServerConfig.isMockData) {
                WorkOrderMockForm.formItems()
            } else {
                remoteRepository.getFormItems()
            }
            
            // 获取用户信息和项目信息
            val userInfo = getUserInfo()
            val projectInfo = getCurrentProject()
            
            // 先回填 formValueMap
            formValueMap["hiddenDangerName"] = draft.hiddenDangerName ?: ""
            formValueMap["workOrderType"] = draft.workOrderType ?: draft.hiddenDangerCategory ?: ""
            formValueMap["hiddenDangerDescription"] = draft.hiddenDangerDescription ?: ""
            formValueMap["responsibleDepartment"] = draft.responsibleDepartment ?: ""
            formValueMap["responsiblePerson"] = draft.responsiblePerson ?: ""
            formValueMap["priority"] = WorkOrderOptions.priorityCodeToLabel(draft.priority)
            formValueMap["reasonAnalysis"] = draft.reasonAnalysis ?: ""
            formValueMap["treatmentRequirement"] = draft.treatmentRequirement ?: ""
            formValueMap["hiddenDangerCategory"] = draft.hiddenDangerCategory ?: ""
            formValueMap["hiddenDangerLevel"] = draft.hiddenDangerLevel ?: ""
            formValueMap["affiliatedMajor"] = draft.profession ?: ""
            formValueMap["controlLevel"] = draft.controlLevel ?: ""
            formValueMap["unitSystem"] = draft.unitSystem ?: ""
            formValueMap["hazardConsequence"] = draft.hazardConsequence ?: ""
            formValueMap["possibility"] = draft.possibility ?: ""
            formValueMap["treatmentDifficulty"] = draft.treatmentDifficulty ?: ""
            formValueMap["responsibleDepartment"] = draft.responsibleDepartment ?: ""
            formValueMap["affiliatedMajor"] = draft.affiliatedMajor ?: ""
            formValueMap["responsiblePerson"] = draft.responsiblePerson ?: ""
            
            // 使用新的附件管理方法
            loadAttachmentsFromDraft(draft)
            
            // 自动填充用户和项目信息（如果草稿中没有这些信息）
            val filledItems = fillFormWithUserAndProjectData(items, userInfo, projectInfo)
            
            // 用formValueMap回填表单项的value（优先使用草稿数据）
            val finalItems = filledItems.map { item ->
                val draftValue = formValueMap[item.key]
                if (!draftValue.isNullOrEmpty()) {
                    item.copy(value = draftValue)
                } else {
                    item
                }
            }
            
            _formItemsLiveData.value = finalItems // 强制刷新，确保第一次进入页面也能回显
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
                    if (ServerConfig.isMockData) {
                        submitToMockStore(context, isDraft = true, onResult)
                    } else {
                        saveDraftToLocal(context) { onResult(true, null) }
                    }
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
        val typeCode = formValueMap["workOrderType"].orEmpty()
        val deptId = formValueMap["responsibleDepartment"].orEmpty()
        if (typeCode.isBlank() || deptId.isBlank()) {
            _submitStatus.value = SubmitStatus.Error("请选择类型和处理部门")
            onResult(false, "请选择类型和处理部门")
            return
        }
        val handlerId = formValueMap["responsiblePerson"].orEmpty().takeIf { it.isNotBlank() }
        val priority = formValueMap["priority"].orEmpty().takeIf { it.isNotBlank() }
        val projectName = getCurrentProject()["itemName"]?.takeIf { it.isNotBlank() }

        val createResult = mobileWorkOrderRepo.create(
            CreateWorkOrderRequest(
                title = formValueMap["hiddenDangerName"].orEmpty(),
                brief = formValueMap["hiddenDangerDescription"].orEmpty(),
                typeCode = typeCode,
                responsibleDept = deptId,
                rectificationPerson = handlerId,
                priority = priority,
                project = projectName
            )
        )
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

        val allAttachments = getAllAttachments().take(9)
        var uploadFailed = 0
        for (path in allAttachments.filterNotNull()) {
            if (path.startsWith("http://") || path.startsWith("https://")) continue
            val file = if (path.startsWith("content://")) {
                copyUriToTempFile(context, android.net.Uri.parse(path))
            } else {
                java.io.File(path).takeIf { it.exists() }
            } ?: run {
                uploadFailed++
                continue
            }
            val uploaded = mobileWorkOrderRepo.uploadAttachment(workOrderId, file).isSuccess
            if (!uploaded) uploadFailed++
        }

        _submitStatus.value = SubmitStatus.Success
        val msg = if (uploadFailed > 0) "工单已创建，${uploadFailed} 个附件上传失败" else "工单提交成功"
        ToastUtil.showCustomToast(context, msg)
        onResult(true, null)
    }

    // 新增：本地保存草稿方法
    fun saveDraftToLocal(context: Context, onSaved: () -> Unit) {
        if (ServerConfig.isMockData) {
            _submitStatus.value = SubmitStatus.Loading
            viewModelScope.launch {
                submitToMockStore(context, isDraft = true) { success, msg ->
                    if (success) onSaved() else ToastUtil.showCustomToast(context, msg ?: "保存失败")
                }
            }
            return
        }
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
            ToastUtil.showCustomToast(context, "草稿已保存")
            onSaved()
        }
    }

    private suspend fun submitToMockStore(
        context: Context,
        isDraft: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        delay(300)
        val errors = if (isDraft) emptyList() else validateForm()
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            _submitStatus.value = SubmitStatus.Idle
            onResult(false, errors.joinToString("\n"))
            return
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val user = SpUtils.getString("user_name")?.takeIf { it.isNotBlank() } ?: "张三"
        val dept = SpUtils.getString("user_department")?.takeIf { it.isNotBlank() } ?: "软件研发部"
        val projectName = getCurrentProject()["itemName"]?.takeIf { it.isNotBlank() }
        val type = formValueMap["workOrderType"].orEmpty()
        val priorityLabel = formValueMap["priority"].orEmpty()
        val priorityCode = WorkOrderOptions.priorityLabelToCode(priorityLabel)
        val handlerDept = formValueMap["responsibleDepartment"].orEmpty()
        val existingId = resubmitId?.takeIf { it.isNotBlank() }
            ?: draftEditId?.takeIf { it.isNotBlank() }
        val id = existingId ?: WorkOrderMockStore.nextId()
        val status = if (isDraft) WorkOrderStatus.DRAFT else WorkOrderStatus.PENDING
        val attachmentList = _attachments.value.orEmpty().filterNotNull().map {
            Attachment(
                fileName = it.substringAfterLast('/'),
                size = "未知",
                url = it
            )
        }
        val isResubmit = !existingId.isNullOrBlank() && !isDraft
        val item = WorkOrderItem(
            id = id,
            hiddenDangerName = formValueMap["hiddenDangerName"].orEmpty(),
            hiddenDangerDescription = formValueMap["hiddenDangerDescription"].orEmpty(),
            workOrderType = type,
            hiddenDangerCategory = type,
            responsibleDepartment = handlerDept,
            responsiblePerson = formValueMap["responsiblePerson"].orEmpty().takeIf { it.isNotBlank() },
            submitUser = user,
            submitDepartment = dept,
            submitTime = time,
            status = if (isResubmit) WorkOrderStatus.PENDING else status,
            nodeName = if (isResubmit) WorkOrderStatus.PENDING.displayName else status.displayName,
            priority = priorityCode,
            projectName = projectName,
            attachments = attachmentList,
            rejectionReason = if (isResubmit) null else null,
            rejectionUser = if (isResubmit) null else null,
            rejectionTime = if (isResubmit) null else null
        )
        WorkOrderMockStore.addOrUpdate(item)
        lastSavedOrder = item
        _submitStatus.value = SubmitStatus.Success
        val msg = if (isDraft) "草稿已保存" else "工单提交成功"
        ToastUtil.showCustomToast(context, msg)
        onResult(true, null)
    }
}