package com.fuusy.project.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fuusy.project.bean.AiAlarmRecord
import com.fuusy.project.repo.AiWebSocketRepository

class AiAlarmRecordViewModel : ViewModel() {
    private val repo = AiWebSocketRepository()
    val aiAlarmList: LiveData<List<AiAlarmRecord>> = repo.aiAlarmList
    val connectionStatus: LiveData<Boolean> = repo.connectionStatus
    val errorMessage: LiveData<String> = repo.errorMessage

    // 筛选条件
    val filterType = MutableLiveData<String>("全部")
    val filterStart = MutableLiveData<String?>(null)
    val filterEnd = MutableLiveData<String?>(null)
    val filteredList = MediatorLiveData<List<AiAlarmRecord>>()

    init {
        repo.connect()
        filteredList.addSource(aiAlarmList) { filter() }
        filteredList.addSource(filterType) { filter() }
        filteredList.addSource(filterStart) { filter() }
        filteredList.addSource(filterEnd) { filter() }
    }

    private fun filter() {
        val all = aiAlarmList.value ?: emptyList()
        val type = filterType.value ?: "全部"
        val start = filterStart.value
        val end = filterEnd.value
        filteredList.value = all.filter { record ->
            (type == "全部" || record.type == type)
            && (start.isNullOrEmpty() || record.appendtime >= start)
            && (end.isNullOrEmpty() || record.appendtime <= end)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repo.disconnect()
    }
} 