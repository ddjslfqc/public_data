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

    private val _orders = MutableLiveData<List<WorkOrderItem>>(emptyList())
    val orders: LiveData<List<WorkOrderItem>> = _orders

    fun loadAll() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.listAll().fold(
                onSuccess = { _orders.value = it },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            _loading.value = false
        }
    }

    fun countByStatus(status: WorkOrderStatus): Int =
        _orders.value.orEmpty().count { it.status == status }
}
