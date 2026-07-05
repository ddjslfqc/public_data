package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.CreateUpdateRecordRequest
import com.fuusy.hiddendanger.repository.OkrRepository
import com.fuusy.hiddendanger.ui.model.GoalKrItem
import com.fuusy.hiddendanger.util.OkrFileHelper
import kotlinx.coroutines.launch

class KrUpdateProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _submitted = MutableLiveData(false)
    val submitted: LiveData<Boolean> = _submitted

    private val _updatedItem = MutableLiveData<GoalKrItem?>()
    val updatedItem: LiveData<GoalKrItem?> = _updatedItem

    private val _attachments = MutableLiveData<List<String>>(emptyList())
    val attachments: LiveData<List<String>> = _attachments

    var krItem: GoalKrItem? = null
    var currentValue: Double = 0.0

    fun init(item: GoalKrItem) {
        krItem = item
        currentValue = item.currentValue
    }

    fun setAttachments(paths: List<String>) {
        _attachments.value = paths.take(MAX_ATTACHMENTS)
    }

    fun addAttachments(paths: List<String>) {
        val merged = _attachments.value.orEmpty().toMutableList()
        for (path in paths) {
            if (merged.size >= MAX_ATTACHMENTS) break
            if (!merged.contains(path)) merged.add(path)
        }
        _attachments.value = merged
    }

    fun removeAttachment(path: String) {
        _attachments.value = _attachments.value.orEmpty().filter { it != path }
    }

    fun submit(context: Context, remark: String?) {
        val item = krItem ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val attachmentIds = mutableListOf<Long>()
            for (path in _attachments.value.orEmpty()) {
                if (path.startsWith("http://") || path.startsWith("https://")) continue
                val file = OkrFileHelper.resolveUploadFile(context, path)
                if (file == null) {
                    _loading.value = false
                    _error.value = "附件读取失败"
                    return@launch
                }
                val uploadResult = repo.uploadKrAttachment(item.id, file)
                if (uploadResult.isFailure) {
                    _loading.value = false
                    _error.value = uploadResult.exceptionOrNull()?.message ?: "附件上传失败"
                    return@launch
                }
                uploadResult.getOrNull()?.id?.let { attachmentIds.add(it) }
            }

            val body = CreateUpdateRecordRequest(
                okrType = "kr",
                okrId = item.id,
                updateContent = remark?.trim()?.ifBlank { null },
                oldValue = item.currentValue,
                newValue = currentValue,
                attachments = attachmentIds.takeIf { it.isNotEmpty() }
            )
            repo.createUpdateRecord(body).fold(
                onSuccess = {
                    _updatedItem.value = item.copy(
                        pendingProgressValue = currentValue,
                        progressApprovalStatus = 0
                    )
                    _submitted.value = true
                },
                onFailure = { _error.value = it.message ?: "提交失败" }
            )
            _loading.value = false
        }
    }

    companion object {
        const val MAX_ATTACHMENTS = 9
    }
}
