package com.fuusy.project.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AlarmRecordViewModel : ViewModel() {
    val alarmList = MutableLiveData<List<AlarmRecord>>()
    fun loadAlarms() {
        alarmList.value = listOf(
            AlarmRecord("CO超过上限值，已发出提醒报警", "汽机凝汽器0米", "通道1", "今天 08:59:37-当前", true),
            AlarmRecord("CO超过上限值，已发出提醒报警", "汽机凝汽器0米", "通道1", "今天 08:59:37-当前", false)
        )
    }
}