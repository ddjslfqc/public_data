package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.auth.DeptRoleHelper
import com.fuusy.hiddendanger.data.KrApproveRequest
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.data.PendingUpdateRecordItem
import com.fuusy.hiddendanger.repository.OkrRepository
import kotlinx.coroutines.launch

class KrApprovalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _krItems = MutableLiveData<List<PendingKrItem>>(emptyList())
    val krItems: LiveData<List<PendingKrItem>> = _krItems

    private val _progressItems = MutableLiveData<List<PendingUpdateRecordItem>>(emptyList())
    val progressItems: LiveData<List<PendingUpdateRecordItem>> = _progressItems

    private val _approved = MutableLiveData<Boolean>()
    val approved: LiveData<Boolean> = _approved

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            DeptRoleHelper.refreshFromLocalUser()
            if (!DeptRoleHelper.isDeptLeader()) {
                _krItems.value = emptyList()
                _progressItems.value = emptyList()
                _error.value = "仅部门负责人可审批"
                _loading.value = false
                return@launch
            }
            repo.getPendingKrs().fold(
                onSuccess = { list ->
                    _krItems.value = list.filter { DeptRoleHelper.isRdDept(it.krOwnerDeptName) }
                },
                onFailure = { _krItems.value = emptyList() }
            )
            repo.getPendingUpdateRecords().fold(
                onSuccess = { list ->
                    _progressItems.value = list.filter { DeptRoleHelper.isRdDept(it.submitterDeptName) }
                },
                onFailure = { _progressItems.value = emptyList() }
            )
            _loading.value = false
        }
    }

    fun approveKr(krId: Long, pass: Boolean, remark: String?) {
        viewModelScope.launch {
            _loading.value = true
            repo.approveKr(
                KrApproveRequest(
                    id = krId,
                    approvalStatus = if (pass) 1 else 2,
                    approvalRemark = remark?.trim()?.ifBlank { null }
                )
            ).fold(
                onSuccess = {
                    _approved.value = true
                    load()
                },
                onFailure = { _error.value = it.message ?: "审批失败" }
            )
            _loading.value = false
        }
    }

    fun approveProgress(recordId: Long, pass: Boolean, remark: String?) {
        viewModelScope.launch {
            _loading.value = true
            val result = if (pass) {
                repo.approveUpdateRecord(recordId, remark?.trim()?.ifBlank { null })
            } else {
                repo.rejectUpdateRecord(recordId, remark?.trim()?.ifBlank { null })
            }
            result.fold(
                onSuccess = {
                    _approved.value = true
                    load()
                },
                onFailure = { _error.value = it.message ?: "审批失败" }
            )
            _loading.value = false
        }
    }
}
