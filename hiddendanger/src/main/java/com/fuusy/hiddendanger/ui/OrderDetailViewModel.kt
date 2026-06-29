package com.fuusy.hiddendanger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.project.workorder.MobileWorkOrderRepository
import kotlinx.coroutines.launch

class OrderDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MobileWorkOrderRepository()

    private val _workOrder = MutableLiveData<WorkOrderItem>()
    val workOrder: LiveData<WorkOrderItem> = _workOrder

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    val title = MutableLiveData<String>()
    val status = MutableLiveData<String>()
    val metaCode = MutableLiveData<String>()
    val metaDept = MutableLiveData<String>()
    val metaPriority = MutableLiveData<String>()
    val metaStay = MutableLiveData<String>()
    val showMetaStay = MutableLiveData(false)

    val basicCode = MutableLiveData<String>()
    val basicName = MutableLiveData<String>()
    val basicType = MutableLiveData<String>()
    val basicPriority = MutableLiveData<String>()
    val basicHandlerDept = MutableLiveData<String>()
    val basicHandler = MutableLiveData<String>()

    val submitUserName = MutableLiveData<String>()
    val submitUserDept = MutableLiveData<String>()
    val dispatchTime = MutableLiveData<String>()
    val projectName = MutableLiveData<String>()
    val expectedCompleteTime = MutableLiveData<String>()

    val desc = MutableLiveData<String>()
    val remark = MutableLiveData<String>()

    val rejectReason = MutableLiveData<String>()
    val showRejectTip = MutableLiveData(false)

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.detail(id).fold(
                onSuccess = { setWorkOrder(it) },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            _loading.value = false
        }
    }

    fun approve(
        workOrderId: String,
        pass: Boolean,
        opinion: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _loading.value = true
            repo.approve(workOrderId, pass, opinion).fold(
                onSuccess = {
                    loadDetail(workOrderId)
                    onResult(true, null)
                },
                onFailure = { onResult(false, it.message) }
            )
            _loading.value = false
        }
    }

    fun setWorkOrder(item: WorkOrderItem) {
        _workOrder.value = item

        title.value = item.hiddenDangerName.orEmpty()
        status.value = item.nodeName?.takeIf { it.isNotBlank() } ?: item.status.displayName

        metaCode.value = item.workOrderNo?.takeIf { it.isNotBlank() } ?: item.id
        metaDept.value = item.responsibleDepartment?.takeIf { it.isNotBlank() } ?: "--"
        metaPriority.value = item.priority.orEmpty()
        val stay = item.stayDuration?.takeIf { it.isNotBlank() }
        showMetaStay.value = item.status == WorkOrderStatus.PROCESSING && stay != null
        metaStay.value = stay?.let { "滞留 $it" }.orEmpty()

        basicCode.value = item.workOrderNo?.takeIf { it.isNotBlank() } ?: item.id
        basicName.value = formatEmptyValue(item.hiddenDangerName)
        basicType.value = formatEmptyValue(item.workOrderType ?: item.hiddenDangerCategory)
        basicPriority.value = formatEmptyValue(item.priority)
        basicHandlerDept.value = formatEmptyValue(item.responsibleDepartment)
        basicHandler.value = formatHandler(item.responsiblePerson)

        submitUserName.value = formatEmptyValue(item.submitUser)
        submitUserDept.value = formatEmptyValue(item.submitDepartment)
        dispatchTime.value = formatEmptyValue(item.submitTime)
        projectName.value = formatEmptyValue(item.projectName)
        expectedCompleteTime.value = formatEmptyValue(item.expectedCompleteTime)

        desc.value = formatEmptyValue(item.hiddenDangerDescription)
        remark.value = "暂无备注"

        val rejected = item.status == WorkOrderStatus.REJECT
        showRejectTip.value = rejected
        rejectReason.value = if (rejected) {
            item.rejectionReason?.takeIf { it.isNotBlank() } ?: "未填写驳回原因"
        } else {
            ""
        }
    }

    private fun formatHandler(person: String?): String {
        if (person.isNullOrBlank() || person == "公共") return "公开抢单"
        return person
    }

    private fun formatEmptyValue(value: String?): String {
        return if (value.isNullOrBlank()) "--" else value
    }
}
