package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.CreateUpdateRecordRequest
import com.fuusy.hiddendanger.data.OkrUpdateRecordAttachment
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
            val attachmentItems = mutableListOf<OkrUpdateRecordAttachment>()
            for (path in _attachments.value.orEmpty()) {
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    attachmentItems.add(
                        OkrUpdateRecordAttachment(
                            type = inferAttachmentType(path),
                            name = path.substringAfterLast('/'),
                            url = path
                        )
                    )
                    continue
                }
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
                uploadResult.getOrNull()?.let { dto ->
                    attachmentItems.add(
                        OkrUpdateRecordAttachment(
                            type = inferAttachmentType(file.name),
                            name = dto.fileName ?: file.name,
                            url = dto.fileUrl ?: dto.filePath
                        )
                    )
                }
            }

            val body = CreateUpdateRecordRequest(
                okrType = "kr",
                okrId = item.id,
                currentValue = currentValue,
                content = remark?.trim()?.ifBlank { null },
                attachments = attachmentItems.takeIf { it.isNotEmpty() }
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

        private fun inferAttachmentType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image"
                "mp4", "mov", "avi", "mkv" -> "video"
                "pdf" -> "pdf"
                else -> "file"
            }
        }
    }
}
