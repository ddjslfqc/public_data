package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.repository.OkrRepository
import kotlinx.coroutines.launch

class OkrCommentListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _received = MutableLiveData<List<OkrKrComment>>(emptyList())
    val received: LiveData<List<OkrKrComment>> = _received

    private val _sent = MutableLiveData<List<OkrKrComment>>(emptyList())
    val sent: LiveData<List<OkrKrComment>> = _sent

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.getReceivedComments().fold(
                onSuccess = { _received.value = it },
                onFailure = { _received.value = emptyList() }
            )
            repo.getSentComments().fold(
                onSuccess = { _sent.value = it },
                onFailure = { _sent.value = emptyList() }
            )
            _loading.value = false
        }
    }
}
