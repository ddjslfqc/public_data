package com.fuusy.project.repo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.fuusy.project.bean.MonitoringData
import com.fuusy.project.bean.WebSocketMessage
import com.fuusy.project.network.GasWebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.fuusy.common.utils.SpUtils
import com.google.gson.Gson
import com.fuusy.project.bean.ProjectItem

/**
 * WebSocket 数据仓库
 * 负责管理实时数据的获取、缓存和分发
 */
class WebSocketGasRepository {
    private val webSocketManager = GasWebSocketManager()

    // 实时监测数据
    private val _monitoringData = MutableLiveData<List<MonitoringData>>()
    val monitoringData: LiveData<List<MonitoringData>> = _monitoringData

    // 连接状态
    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus

    // 错误信息
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // 告警信息
    private val _alerts = MutableLiveData<List<WebSocketMessage.Alert>>()
    val alerts: LiveData<List<WebSocketMessage.Alert>> = _alerts

    // 告警状态（用于角标显示）
    private val _alarmStatus = MutableLiveData<WebSocketMessage.AlarmData>()
    val alarmStatus: LiveData<WebSocketMessage.AlarmData> = _alarmStatus

    // 设备信息
    private val _deviceInfo = MutableLiveData<WebSocketMessage.GasData>()
    val deviceInfo: LiveData<WebSocketMessage.GasData> = _deviceInfo

    private val alertsList = mutableListOf<WebSocketMessage.Alert>()

    // 模拟数据推送相关
    private var mockDataJob: kotlinx.coroutines.Job? = null
    private var isMockDataEnabled = false // 默认禁用模拟数据

    init {
        setupWebSocketCallbacks()
        // 初始化默认的监测数据，显示"--"占位符
        initializeDefaultMonitoringData()
        // 初始化默认告警状态，避免观察者收到 null
        _alarmStatus.postValue(
            WebSocketMessage.AlarmData(
                ex = 0,
                o2 = 0,
                h2s = 0,
                co = 0,
                tem = 0
            )
        )
    }

    /**
     * 获取当前选中的项目信息
     */
    private fun getCurrentSelectedProject(): ProjectItem? {
        val projectJson = SpUtils.getString("selected_project")
        if (projectJson.isNullOrEmpty()) {
            return null
        }

        return try {
            val gson = Gson()
            gson.fromJson(projectJson, ProjectItem::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 初始化默认的监测数据
     */
    private fun initializeDefaultMonitoringData() {
        val defaultDataList = listOf(
            MonitoringData("O₂", "--", "%VOL"),
            MonitoringData("H₂S", "--", "PPM"),
            MonitoringData("Ex", "--", "%LEL"),
            MonitoringData("CO", "--", "PPM"),
            MonitoringData("粉尘", "--", "mg/m³")
        )
        _monitoringData.postValue(defaultDataList)
    }

    /**
     * 设置 WebSocket 回调
     */
    private fun setupWebSocketCallbacks() {
        webSocketManager.onMessageReceived = { message ->
            when (message) {
                is WebSocketMessage.GasData -> handleGasData(message)
                is WebSocketMessage.Alert -> handleAlert(message)
                is WebSocketMessage.Status -> handleStatus(message)
            }
        }

        webSocketManager.onConnectionStatusChanged = { isConnected ->
            _connectionStatus.postValue(isConnected)
            if (isConnected && isMockDataEnabled) {
                // 连接成功后开始推送模拟数据
                startMockDataPush()
            } else {
                // 连接断开时停止推送模拟数据
                stopMockDataPush()
            }
        }

        webSocketManager.onError = { error ->
            _errorMessage.postValue(error)
        }
    }

    /**
     * 处理气体监测数据
     */
    private fun handleGasData(gasData: WebSocketMessage.GasData) {
        // 获取当前选中的项目
        val currentProject = getCurrentSelectedProject()

        // 如果没有选中项目，或者设备ID不匹配，则不处理数据
        if (currentProject == null) {
            println("未选中项目，忽略设备数据: ${gasData.device}")
            return
        }

        // 检查设备ID是否匹配当前选中的项目
        if (currentProject.device != gasData.device) {
            println("设备ID不匹配，忽略数据: 当前项目设备=${currentProject.device}, 数据设备=${gasData.device}")
            return
        }

        println("处理设备数据: 项目=${currentProject.itemName}, 设备=${gasData.device}")

        // 更新设备信息
        _deviceInfo.postValue(gasData)

        // 安全处理告警状态（后端可能未下发 alarm 字段）
        val safeAlarm = gasData.alarm
        _alarmStatus.postValue(safeAlarm)

        // 更新监测数据
        val monitoringDataList = listOf(
            MonitoringData("O₂", if (gasData.gas.o2 >= 0) String.format("%.1f", gasData.gas.o2) else "--", "%VOL"),
            MonitoringData("H₂S", if (gasData.gas.h2s >= 0) String.format("%.1f", gasData.gas.h2s) else "--", "PPM"),
            MonitoringData("Ex", if (gasData.gas.ex >= 0) String.format("%.1f", gasData.gas.ex) else "--", "%LEL"),
            MonitoringData("CO", if (gasData.gas.co >= 0) String.format("%.1f", gasData.gas.co) else "--", "PPM"),
            MonitoringData("粉尘", if (gasData.gas.tem >= 0) String.format("%.1f", gasData.gas.tem) else "--", "mg/m³")
        )

        _monitoringData.postValue(monitoringDataList)

        // 检查告警状态并生成告警信息
        checkAlarmStatus(safeAlarm)
        
        // 调试日志
        println("收到WebSocket数据: 项目=${currentProject.itemName}, 设备=${gasData.device}, O2=${gasData.gas.o2}, CO=${gasData.gas.co}, H2S=${gasData.gas.h2s}")
    }

    /**
     * 检查告警状态并生成告警信息
     */
    private fun checkAlarmStatus(alarm: WebSocketMessage.AlarmData) {
        val alarmMessages = mutableListOf<String>()

        if (alarm.co == 1) {
            alarmMessages.add("一氧化碳浓度超标")
        }
        if (alarm.h2s == 1) {
            alarmMessages.add("硫化氢浓度超标")
        }
        if (alarm.o2 == 1) {
            alarmMessages.add("氧气浓度异常")
        }
        if (alarm.ex == 1) {
            alarmMessages.add("甲烷浓度超标")
        }
        if (alarm.tem == 1) {
            alarmMessages.add("温度异常")
        }

        if (alarmMessages.isNotEmpty()) {
            val alert = WebSocketMessage.Alert(
                type = "gas_alarm",
                message = alarmMessages.joinToString("、"),
                level = "high",
                timestamp = System.currentTimeMillis()
            )
            handleAlert(alert)
            println("检测到告警: ${alarmMessages.joinToString("、")}")
        }
    }

    /**
     * 处理告警信息
     */
    private fun handleAlert(alert: WebSocketMessage.Alert) {
        CoroutineScope(Dispatchers.Main).launch {
            alertsList.add(alert)
            // 只保留最近的10条告警
            if (alertsList.size > 10) {
                alertsList.removeAt(0)
            }
            _alerts.postValue(alertsList.toList())
        }
    }

    /**
     * 处理状态信息
     */
    private fun handleStatus(status: WebSocketMessage.Status) {
        // 可以根据需要处理状态信息
        println("收到状态信息: ${status.message}")
    }

    /**
     * 获取告警总数
     */
    fun getAlarmCount(): Int {
        val alarmData = _alarmStatus.value
        return alarmData?.let { alarm ->
            alarm.co + alarm.h2s + alarm.o2 + alarm.ex + alarm.tem
        } ?: 0
    }

    /**
     * 检查特定气体是否告警
     */
    fun isGasAlarmed(gasType: String): Boolean {
        val alarmData = _alarmStatus.value
        return when (gasType.uppercase()) {
            "CO" -> alarmData?.co == 1
            "H2S" -> alarmData?.h2s == 1
            "O2" -> alarmData?.o2 == 1
            "EX" -> alarmData?.ex == 1
            "TEM" -> alarmData?.tem == 1
            else -> false
        }
    }

    /**
     * 连接 WebSocket
     */
    fun connect() {
        webSocketManager.connect()
    }

    /**
     * 断开 WebSocket 连接
     */
    fun disconnect() {
        webSocketManager.disconnect()
    }

    /**
     * 发送消息
     */
    fun sendMessage(message: String): Boolean {
        return webSocketManager.sendMessage(message)
    }

    /**
     * 发送 JSON 消息
     */
    fun sendJsonMessage(message: Any): Boolean {
        return webSocketManager.sendJsonMessage(message)
    }

    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean {
        return webSocketManager.isConnected()
    }

    /**
     * 获取连接状态信息
     */
    fun getConnectionInfo(): String {
        return webSocketManager.getConnectionInfo()
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.postValue("")
    }

    /**
     * 清除告警信息
     */
    fun clearAlerts() {
        alertsList.clear()
        _alerts.postValue(emptyList())
    }

    /**
     * 开始推送模拟数据
     */
    private fun startMockDataPush() {
        // 如果禁用模拟数据，直接返回
        if (!isMockDataEnabled) return
        
        stopMockDataPush() // 先停止之前的任务

        mockDataJob = CoroutineScope(Dispatchers.IO).launch {
            // 延迟1秒后开始推送，确保连接稳定
            delay(1000)

            // 推送初始测试数据
            pushMockGasData()

            // 每5秒推送一次模拟数据
            while (isMockDataEnabled && webSocketManager.isConnected()) {
                delay(5000)
                pushMockGasData()
            }
        }
    }

    /**
     * 停止推送模拟数据
     */
    private fun stopMockDataPush() {
        mockDataJob?.cancel()
        mockDataJob = null
    }

    /**
     * 推送模拟气体数据
     */
    private fun pushMockGasData() {
        val mockGasData = WebSocketMessage.GasData(
            alarm = WebSocketMessage.AlarmData(
                ex = 0,
                o2 = 1,  // 模拟氧气告警
                h2s = 0,
                co = 0,
                tem = 0
            ),
            camera = "100232",
            device = "YXKJ0001",
            floor = "GL1A-1",
            gas = generateRandomGasValues(), // 动态生成
            item = "YXKJ0001",
            name = listOf("CO", "H2S", "O2", "EX", "TEM"),
            region = "001",
            time = "2023-10-05 12:00:00",
            wendu = 25.5
        )
        handleGasData(mockGasData)
        println("推送模拟数据: 设备=${mockGasData.device}, O2=${mockGasData.gas.o2}, CO=${mockGasData.gas.co}")
    }

    private fun generateRandomGasValues(): WebSocketMessage.GasValues {
        return WebSocketMessage.GasValues(
            co = (0..50).random() / 10.0,      // 0.0 ~ 5.0 PPM，更符合实际CO浓度范围
            h2s = (0..20).random() / 10.0,     // 0.0 ~ 2.0 PPM，更符合实际H2S浓度范围
            o2 = 20.0 + (0..100).random() / 10.0,  // 20.0 ~ 30.0 %VOL，更符合实际氧气浓度范围
            ex = (0..50).random() / 100.0,     // 0.00 ~ 0.50 %LEL，更符合实际甲烷浓度范围
            tem = 15.0 + (0..200).random() / 10.0   // 15.0 ~ 35.0 ℃，更符合实际温度范围
        )
    }

    /**
     * 启用/禁用模拟数据推送
     */
    fun setMockDataEnabled(enabled: Boolean) {
        isMockDataEnabled = enabled
        if (enabled && webSocketManager.isConnected()) {
            startMockDataPush()
        } else {
            stopMockDataPush()
        }
    }

    /**
     * 手动推送一次模拟数据
     */
    fun pushMockDataOnce() {
        if (webSocketManager.isConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                pushMockGasData()
            }
        }
    }
} 