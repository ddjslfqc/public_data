package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.repository.OkrRepository
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrNavHelper
import com.fuusy.hiddendanger.ui.model.OkrInboxGroup
import com.fuusy.hiddendanger.ui.model.OkrInboxGrouper
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

    private val _openKrDetail = MutableLiveData<GoalKrItem?>()
    val openKrDetail: LiveData<GoalKrItem?> = _openKrDetail

    private val _expandedKrId = MutableLiveData<Long?>(null)
    val expandedKrId: LiveData<Long?> = _expandedKrId

    private val threadCache = mutableMapOf<Long, List<OkrKrComment>>()
    private val loadingThreads = mutableSetOf<Long>()
    private val _threadStateVersion = MutableLiveData(0)
    val threadStateVersion: LiveData<Int> = _threadStateVersion

    private var showingReceived = true

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

    fun setTab(received: Boolean) {
        showingReceived = received
        _expandedKrId.value = null
    }

    fun isReceivedTab(): Boolean = showingReceived

    fun currentGroups(): List<OkrInboxGroup> {
        val list = if (showingReceived) _received.value.orEmpty() else _sent.value.orEmpty()
        return OkrInboxGrouper.group(list)
    }

    fun threadCacheSnapshot(): Map<Long, List<OkrKrComment>> = threadCache.toMap()

    fun loadingThreadsSnapshot(): Set<Long> = loadingThreads.toSet()

    fun toggleExpand(group: OkrInboxGroup) {
        val krId = group.krId
        if (_expandedKrId.value == krId) {
            _expandedKrId.value = null
        } else {
            _expandedKrId.value = krId
            loadThread(krId)
        }
    }

    private fun loadThread(krId: Long, force: Boolean = false) {
        if (!force && threadCache.containsKey(krId)) {
            bumpThreadState()
            return
        }
        viewModelScope.launch {
            reloadThread(krId)
        }
    }

    private suspend fun reloadThread(krId: Long) {
        loadingThreads.add(krId)
        bumpThreadState()
        repo.getKrCommentList(krId).fold(
            onSuccess = { threadCache[krId] = it },
            onFailure = { _error.value = it.message ?: "加载评论失败" }
        )
        loadingThreads.remove(krId)
        bumpThreadState()
    }

    fun submitReply(krId: Long, content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            _error.value = "请输入评论内容"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.createKrComment(krId, trimmed).fold(
                onSuccess = {
                    loadThread(krId, force = true)
                    refreshInboxLists()
                },
                onFailure = { _error.value = it.message ?: "回复失败" }
            )
            _loading.value = false
        }
    }

    private suspend fun refreshInboxLists() {
        repo.getReceivedComments().fold(
            onSuccess = { _received.value = it },
            onFailure = { }
        )
        repo.getSentComments().fold(
            onSuccess = { _sent.value = it },
            onFailure = { }
        )
    }

    fun openKrDetail(group: OkrInboxGroup) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val comment = group.preview
            val item = repo.findKrItem(comment.krId).getOrElse { KrNavHelper.fromComment(comment) }
            _openKrDetail.value = item
            _loading.value = false
        }
    }

    fun consumeOpenKrDetail() {
        _openKrDetail.value = null
    }

    private fun bumpThreadState() {
        _threadStateVersion.value = (_threadStateVersion.value ?: 0) + 1
    }
}
