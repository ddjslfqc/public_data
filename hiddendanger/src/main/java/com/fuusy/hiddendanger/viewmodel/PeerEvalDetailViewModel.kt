package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.PeerEvalSubmissionDetail
import com.fuusy.hiddendanger.repository.PeerEvalRepository
import kotlinx.coroutines.launch

class PeerEvalDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PeerEvalRepository(application)

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _detail = MutableLiveData<PeerEvalSubmissionDetail?>()
    val detail: LiveData<PeerEvalSubmissionDetail?> = _detail

    fun load(
        period: String,
        targetUserId: Long,
        targetUserName: String?,
        deptName: String?
    ) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.getSubmissionDetail(period, targetUserId, targetUserName, deptName).fold(
                onSuccess = { _detail.value = it },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            _loading.value = false
        }
    }
}
