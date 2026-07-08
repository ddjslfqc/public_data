package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.data.OkrUpdateRecordItem
import com.fuusy.hiddendanger.repository.OkrRepository
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.ui.model.KrNavHelper
import kotlinx.coroutines.launch

class KrDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _comments = MutableLiveData<List<OkrKrComment>>(emptyList())
    val comments: LiveData<List<OkrKrComment>> = _comments

    private val _updateRecords = MutableLiveData<List<OkrUpdateRecordItem>>(emptyList())
    val updateRecords: LiveData<List<OkrUpdateRecordItem>> = _updateRecords

    private val _refreshedKr = MutableLiveData<GoalKrItem?>()
    val refreshedKr: LiveData<GoalKrItem?> = _refreshedKr

    private val _commentSubmitted = MutableLiveData(false)
    val commentSubmitted: LiveData<Boolean> = _commentSubmitted

    private val _commentDeleted = MutableLiveData(false)
    val commentDeleted: LiveData<Boolean> = _commentDeleted

    fun load(krItem: GoalKrItem) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _commentSubmitted.value = false
            _commentDeleted.value = false
            reloadDetail(krItem.id, krItem.periodEndDate)
            _loading.value = false
        }
    }

    fun submitComment(krId: Long, content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            _error.value = "请输入评论内容"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val periodEnd = _refreshedKr.value?.periodEndDate
            repo.createKrComment(krId, trimmed).fold(
                onSuccess = {
                    _commentSubmitted.value = true
                    reloadDetail(krId, periodEnd)
                },
                onFailure = { _error.value = it.message ?: "评论失败" }
            )
            _loading.value = false
        }
    }

    fun deleteComment(commentId: Long, krId: Long) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val periodEnd = _refreshedKr.value?.periodEndDate
            repo.deleteKrComment(commentId).fold(
                onSuccess = {
                    _commentDeleted.value = true
                    reloadDetail(krId, periodEnd)
                },
                onFailure = { _error.value = it.message ?: "删除失败" }
            )
            _loading.value = false
        }
    }

    private suspend fun reloadDetail(krId: Long, periodEndDate: String?) {
        repo.getKrDetail(krId).fold(
            onSuccess = { detail ->
                _refreshedKr.value = KrNavHelper.goalKrItem(detail, periodEndDate)
                _comments.value = detail.comments.orEmpty()
                _updateRecords.value = detail.updateRecords.orEmpty()
            },
            onFailure = { e ->
                _error.value = e.message
                _comments.value = emptyList()
                _updateRecords.value = emptyList()
            }
        )
    }
}
