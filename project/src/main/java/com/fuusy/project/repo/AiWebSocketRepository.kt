package com.fuusy.project.repo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.fuusy.project.bean.AiAlarmRecord
import com.fuusy.project.network.AiWebSocketManager

class AiWebSocketRepository {
    private val webSocketManager = AiWebSocketManager()
    private val _aiAlarmList = MutableLiveData<List<AiAlarmRecord>>(emptyList())
    val aiAlarmList: LiveData<List<AiAlarmRecord>> = _aiAlarmList
    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val recordList = mutableListOf<AiAlarmRecord>()

    init {
        // 插入两条假数据，方便UI调试
        if (!webSocketManager.isConnected()) {
            val fakeList = listOf(
                AiAlarmRecord(
                    address = "测试区域A",
                    appendtime = "2025-06-10 10:41:54",
                    device = "设备001",
                    id = "1",
                    image = "https://img1.baidu.com/it/u=123456789,123456789&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=500",
                    ip = "192.168.1.1",
                    item = "AI001",
                    itemName = "未戴安全帽",
                    type = "安全帽"
                ),
                AiAlarmRecord(
                    address = "测试区域B",
                    appendtime = "2025-06-10 11:22:33",
                    device = "设备002",
                    id = "2",
                    image = "https://img1.baidu.com/it/u=987654321,987654321&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=500",
                    ip = "192.168.1.2",
                    item = "AI002",
                    itemName = "抽烟",
                    type = "抽烟"
                )
            )
            recordList.addAll(fakeList)
            _aiAlarmList.postValue(recordList.toList())
        }
        webSocketManager.onMessageReceived = { record ->
            recordList.add(0, record)
            if (recordList.size > 50) recordList.removeLast()
            _aiAlarmList.postValue(recordList.toList())
        }
        webSocketManager.onConnectionStatusChanged = { _connectionStatus.postValue(it) }
        webSocketManager.onError = { _errorMessage.postValue(it) }
    }

    fun connect() = webSocketManager.connect()
    fun disconnect() = webSocketManager.disconnect()
    fun isConnected() = webSocketManager.isConnected()
} 