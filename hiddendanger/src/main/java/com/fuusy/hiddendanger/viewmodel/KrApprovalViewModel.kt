package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.KrApproveRequest
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.repository.OkrRepository
import kotlinx.coroutines.launch

class KrApprovalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _items = MutableLiveData<List<PendingKrItem>>(emptyList())
    val items: LiveData<List<PendingKrItem>> = _items

    private val _approved = MutableLiveData<Boolean>()
    val approved: LiveData<Boolean> = _approved

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.getPendingKrs().fold(
                onSuccess = { _items.value = it },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            _loading.value = false
        }
    }

    fun approve(krId: Long, pass: Boolean, remark: String?) {
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
}
