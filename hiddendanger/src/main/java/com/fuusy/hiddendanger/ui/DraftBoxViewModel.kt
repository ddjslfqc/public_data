package com.fuusy.hiddendanger.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.data.WorkOrderItem
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.common.data.local.AppDatabase
import com.fuusy.common.data.local.WorkOrderRepository
import kotlinx.coroutines.launch

class DraftBoxViewModel(application: Application) : AndroidViewModel(application) {
    val draftList = MutableLiveData<List<WorkOrderItem>>()
    private val db = AppDatabase.getInstance(application)
    private val repository = WorkOrderRepository(db)

    fun loadDrafts() {
        viewModelScope.launch {
            val all = repository.getWorkOrdersByStatus(WorkOrderStatus.DRAFT)
            val drafts = all.mapNotNull { (entity, attachments) ->
                try {
                    val item = com.google.gson.Gson().fromJson(entity.formJson, WorkOrderItem::class.java)
                    item.id = entity.id.toString()
                    // 合并附件
                    val attachList = attachments.map {
                        com.fuusy.common.data.Attachment(
                            it.localPath.substringAfterLast("/"),
                            "未知",
                            it.localPath
                        )
                    }
                    val itemWithAttachments = item.copy(attachments = attachList)
                    if (item.status == WorkOrderStatus.DRAFT) itemWithAttachments else null
                } catch (e: Exception) {
                    null
                }
            }
            draftList.postValue(drafts)
        }
    }

    fun deleteDraft(draft: WorkOrderItem) {
        viewModelScope.launch {
            repository.deleteWorkOrderById(draft.id)
            loadDrafts()
        }
    }
} 