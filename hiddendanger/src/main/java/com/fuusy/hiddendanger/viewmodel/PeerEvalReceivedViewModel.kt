package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.PeerEvalReceivedResponse
import com.fuusy.hiddendanger.repository.PeerEvalRepository
import kotlinx.coroutines.launch

class PeerEvalReceivedViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PeerEvalRepository(application)

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _received = MutableLiveData<PeerEvalReceivedResponse?>()
    val received: LiveData<PeerEvalReceivedResponse?> = _received

    fun load(period: String, cached: PeerEvalReceivedResponse? = null) {
        if (cached != null) {
            _received.value = cached
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.getReceivedEval(period, forceRefresh = false).fold(
                onSuccess = { _received.value = it },
                onFailure = {
                    _error.value = it.message ?: "加载失败"
                    _received.value = PeerEvalReceivedResponse(period = period)
                }
            )
            _loading.value = false
        }
    }
}
