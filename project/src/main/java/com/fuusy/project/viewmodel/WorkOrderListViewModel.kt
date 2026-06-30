package com.fuusy.project.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.project.workorder.MobileWorkOrderRepository
import kotlinx.coroutines.launch

class WorkOrderListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MobileWorkOrderRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** 当前 Tab 展示的列表（按 status 调 /mobile/workorder/list） */
    private val _orders = MutableLiveData<List<WorkOrderItem>>(emptyList())
    val orders: LiveData<List<WorkOrderItem>> = _orders

    /** 用于 Tab 计数：GET /list 不传 status */
    private val _allForCount = MutableLiveData<List<WorkOrderItem>>(emptyList())
    val allForCount: LiveData<List<WorkOrderItem>> = _allForCount

    fun load(status: WorkOrderStatus? = null) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.list(status).fold(
                onSuccess = { _orders.value = it },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            _loading.value = false
        }
    }

    fun loadAll() = load(null)

    fun refreshTabCounts() {
        viewModelScope.launch {
            repo.list(null).fold(
                onSuccess = { _allForCount.value = it },
                onFailure = { /* Tab 计数失败不影响列表 */ }
            )
        }
    }

    fun countByStatus(status: WorkOrderStatus): Int =
        _allForCount.value.orEmpty().count { it.status == status }
}
