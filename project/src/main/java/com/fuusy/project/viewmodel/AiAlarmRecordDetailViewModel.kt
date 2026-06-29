package com.fuusy.project.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fuusy.project.bean.AiAlarmRecord

class AiAlarmRecordDetailViewModel : ViewModel() {
    val record = MutableLiveData<AiAlarmRecord>()
} 