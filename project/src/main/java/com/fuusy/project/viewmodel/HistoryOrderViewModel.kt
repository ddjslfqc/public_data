package com.fuusy.project.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.data.local.AppDatabase
import com.fuusy.common.data.local.WorkOrderRepository
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.google.gson.Gson
import kotlinx.coroutines.launch
import com.fuusy.project.bean.WorkOrderNetItem
import com.fuusy.project.bean.WorkOrderListResponse
import com.fuusy.project.bean.toWorkOrderItem
import java.text.SimpleDateFormat
import java.util.Locale
import com.fuusy.common.utils.LoadingStatus

class HistoryOrderViewModel(application: Application) : AndroidViewModel(application) {
    val orderList = MutableLiveData<List<WorkOrderItem>>()
    val searchKey = MutableLiveData<String>()
    val filterStatus = MutableLiveData<com.fuusy.common.data.WorkOrderStatus?>()
    val startTime = MutableLiveData<String>()
    val endTime = MutableLiveData<String>()
    val selectedProjectIds = MutableLiveData<List<String>>()
    val toastEvent = MutableLiveData<String>()

    // 添加loading状态
    val loadingStatus = MutableLiveData<LoadingStatus>()

    private val db = AppDatabase.getInstance(application)
    private val repository = WorkOrderRepository(db)
    private val gson = Gson()

    // 新增：全量数据缓存
    private val allOrders = mutableListOf<WorkOrderItem>()

    // 草稿箱专用：本地数据加载
    fun loadDraftOrders() {
        viewModelScope.launch {
            val allOrdersFromDb = repository.getWorkOrdersByStatus(WorkOrderStatus.DRAFT)
            val result = allOrdersFromDb.mapNotNull { (entity, attachments) ->
                try {
                    val item = gson.fromJson(entity.formJson, WorkOrderItem::class.java)
                    // 将数据库中的附件信息添加到WorkOrderItem中
                    val workOrderWithAttachments = item.copy(
                        attachments = attachments.map { attachment ->
                            com.fuusy.common.data.Attachment(
                                fileName = attachment.localPath.substringAfterLast("/"),
                                size = "未知",
                                url = attachment.localPath
                            )
                        }
                    )
                    workOrderWithAttachments
                } catch (e: Exception) {
                    null
                }
            }
            allOrders.clear()
            allOrders.addAll(result)
            orderList.postValue(result)
        }
    }

    // 历史工单：只用网络数据
    fun fetchOrderListFromNet() {
        viewModelScope.launch {
            try {
                // 设置loading状态
                loadingStatus.postValue(LoadingStatus.Loading)
                
                val projectRepo = com.fuusy.project.repo.ProjectNetRepo()
                val result = projectRepo.getWorkOrderList()
                if (result.isSuccess && result.getOrNull()?.code == 200 && result.getOrNull()?.data != null) {
                    val items = result.getOrNull()?.data?.map { it.toWorkOrderItem() } ?: emptyList()
                    allOrders.clear()
                    allOrders.addAll(items)
                    searchOrders() // 让筛选生效
                    
                    // 设置成功状态
                    loadingStatus.postValue(LoadingStatus.Success)
                } else {
                    allOrders.clear()
                    searchOrders()
                    loadingStatus.postValue(LoadingStatus.Error("获取工单列表失败"))
                }
            } catch (e: Exception) {
                println(e.stackTrace)
                allOrders.clear()
                searchOrders()
                loadingStatus.postValue(LoadingStatus.Error(e.message ?: "网络请求失败"))
            }
        }
    }

    private fun parseTime(str: String?): Long? {
        if (str.isNullOrEmpty()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                return SimpleDateFormat(fmt, Locale.getDefault()).parse(str)?.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun patchEndTime(str: String?): String? {
        if (str.isNullOrEmpty()) return null
        // 如果已经有时分秒，直接返回
        if (str.length > 10) return str
        // yyyy/MM/dd -> yyyy/MM/dd 23:59:59
        return "$str 23:59:59"
    }

    fun searchOrders() {
        val key = searchKey.value.orEmpty()
        val status = filterStatus.value
        val start = startTime.value
        val end = patchEndTime(endTime.value)
        val projectIds = selectedProjectIds?.value
        val startTs = parseTime(start)
        val endTs = parseTime(end)

        // 如果开始时间大于结束时间，只提示，不筛选
        if (startTs != null && endTs != null && startTs > endTs) {
            toastEvent.postValue("开始时间不能大于结束时间，请重新选择")
            return
        }

        val filtered = allOrders.filter { order ->
            val orderTs = parseTime(order.submitTime)
            (key.isBlank()
                    || order.hiddenDangerName?.contains(key) == true
                    || order.hiddenDangerDescription?.contains(key)==true
                    || order.id?.contains(key)==true
                    || order.hiddenDangerCategory?.contains(key) == true
                    || order.responsiblePerson?.contains(key) == true
                    )
                    && (status == null || order.status == status)
                    && (startTs == null || (orderTs != null && orderTs >= startTs))
                    && (endTs == null || (orderTs != null && orderTs <= endTs))
                    && (projectIds.isNullOrEmpty())
        }
        orderList.postValue(filtered)
    }
} 