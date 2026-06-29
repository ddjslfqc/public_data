package com.fuusy.project.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuusy.project.bean.MonitoringData
import com.fuusy.project.bean.WebSocketMessage
import com.fuusy.project.repo.WebSocketGasRepository
import com.fuusy.project.ui.model.VideoChannelInfo
import kotlinx.coroutines.launch
import com.fuusy.project.bean.ProjectItem
import com.fuusy.project.bean.AppDatabase

class ProjectDetailViewModel(private val context: Context) : ViewModel() {
    private val _videoList = MutableLiveData<List<VideoChannelInfo>>()
    val videoList: LiveData<List<VideoChannelInfo>> = _videoList

    private val _isSingleMode = MutableLiveData<Boolean>(false)
    val isSingleMode: LiveData<Boolean> = _isSingleMode

    private val _selectedChannel = MutableLiveData<VideoChannelInfo?>()
    val selectedChannel: LiveData<VideoChannelInfo?> = _selectedChannel

    private val _currentProject = MutableLiveData<ProjectItem?>()
    val currentProject: LiveData<ProjectItem?> = _currentProject

    // Loading 状态 - 支持多个视频的独立 loading
    private val _videoLoadingStates = MutableLiveData<Map<Int, Boolean>>()
    val videoLoadingStates: LiveData<Map<Int, Boolean>> = _videoLoadingStates
    private val _isAllVideosLoaded = MutableLiveData<Boolean>()
    val isAllVideosLoaded: LiveData<Boolean> = _isAllVideosLoaded

    // 错误状态 - 支持多个视频的独立错误状态
    private val _videoErrorStates = MutableLiveData<List<Boolean>>()
    val videoErrorStates: LiveData<List<Boolean>> = _videoErrorStates

    // WebSocket 相关
    private val webGasSocketRepository = WebSocketGasRepository()
    val monitoringData: LiveData<List<MonitoringData>> = webGasSocketRepository.monitoringData
    val connectionStatus: LiveData<Boolean> = webGasSocketRepository.connectionStatus
    val errorMessage: LiveData<String> = webGasSocketRepository.errorMessage
    val alerts: LiveData<List<WebSocketMessage.Alert>> = webGasSocketRepository.alerts
    val alarmStatus: LiveData<WebSocketMessage.AlarmData> = webGasSocketRepository.alarmStatus
    val deviceInfo: LiveData<WebSocketMessage.GasData> = webGasSocketRepository.deviceInfo

    private var allChannels: List<VideoChannelInfo> = emptyList()
    private var currentChannelIndex = 0

    init {
        // 初始化时连接 WebSocket
        connectWebSocket()
    }

    fun loadVideoChannels() {
        fetchVideoChannels()
    }

    fun switchToAll() {
        if (_isSingleMode.value == true) {
            _isSingleMode.value = false
            _videoList.value = allChannels
        }
    }

    fun getCurrentChannelIndex(): Int = currentChannelIndex

    fun switchToSingle(channel: VideoChannelInfo) {
        currentChannelIndex = allChannels.indexOfFirst { it.channelNo == channel.channelNo }
    }

    // WebSocket 相关方法

    /**
     * 连接 WebSocket
     */
    fun connectWebSocket() {
        viewModelScope.launch {
            webGasSocketRepository.connect()
        }
    }

    /**
     * 断开 WebSocket 连接
     */
    fun disconnectWebSocket() {
        webGasSocketRepository.disconnect()
    }

    /**
     * 发送消息到 WebSocket 服务器
     */
    fun sendWebSocketMessage(message: String): Boolean {
        return webGasSocketRepository.sendMessage(message)
    }

    /**
     * 发送 JSON 消息到 WebSocket 服务器
     */
    fun sendWebSocketJsonMessage(message: Any): Boolean {
        return webGasSocketRepository.sendJsonMessage(message)
    }

    /**
     * 检查 WebSocket 连接状态
     */
    fun isWebSocketConnected(): Boolean {
        return webGasSocketRepository.isConnected()
    }

    /**
     * 获取 WebSocket 连接状态信息
     */
    fun getWebSocketConnectionInfo(): String {
        return webGasSocketRepository.getConnectionInfo()
    }

    /**
     * 清除错误信息
     */
    fun clearWebSocketError() {
        webGasSocketRepository.clearError()
    }

    /**
     * 清除告警信息
     */
    fun clearWebSocketAlerts() {
        webGasSocketRepository.clearAlerts()
    }

    /**
     * 重新连接 WebSocket
     */
    fun reconnectWebSocket() {
        webGasSocketRepository.disconnect()
        connectWebSocket()
    }

    /**
     * 获取告警总数
     */
    fun getAlarmCount(): Int {
        return webGasSocketRepository.getAlarmCount()
    }

    /**
     * 检查特定气体是否告警
     */
    fun isGasAlarmed(gasType: String): Boolean {
        return webGasSocketRepository.isGasAlarmed(gasType)
    }

    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): WebSocketMessage.GasData? {
        return deviceInfo.value
    }

    /**
     * 启用/禁用模拟数据推送
     */
    fun setMockDataEnabled(enabled: Boolean) {
        webGasSocketRepository.setMockDataEnabled(enabled)
    }

    /**
     * 手动推送一次模拟数据
     */
    fun pushMockDataOnce() {
        webGasSocketRepository.pushMockDataOnce()
    }

    fun loadProjectDetail(projectId: String) {
        viewModelScope.launch {
            val db = AppDatabase.getInstance(context)
            
            // 解析项目标识符 (格式: item_device)
            val (item, device) = if (projectId.contains("_")) {
                val parts = projectId.split("_", limit = 2)
                parts[0] to parts[1]
            } else {
                // 兼容旧格式，只有item没有device
                projectId to ""
            }
            
            Log.d("ProjectSwitch", "解析项目标识: item=$item, device=$device")
            
            // 使用item和device来查找项目
            val allProjects = db.projectItemDao().getAll()
            val project = if (device.isNotEmpty()) {
                allProjects.find { it.item == item && it.device == device }
            } else {
                allProjects.find { it.item == item }
            }
            
            _currentProject.postValue(project)
            
            // 项目切换后，重新加载视频通道
            if (project != null) {
                Log.d("ProjectSwitch", "项目切换成功，重新加载视频通道: ${project.item}_${project.device}")
//                loadVideoChannels()
            } else {
                Log.e("ProjectSwitch", "未找到项目: item=$item, device=$device")
                // 清空视频列表
                _videoList.postValue(emptyList())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时断开 WebSocket 连接
        webGasSocketRepository.disconnect()
    }

    private fun fetchVideoChannels() {
        val item = currentProject.value?.item ?: ""
        val device = currentProject.value?.device ?: ""
        if (item.isNullOrEmpty() || device.isNullOrEmpty()) {
            return
        }
        viewModelScope.launch {
            try {
                val repo = com.fuusy.project.repo.ProjectNetRepo()
                val channels = repo.fetchVideoChannels(item, device)
                
                // 检查是否有有效的视频数据
                if (channels.isNotEmpty()) {
                    allChannels = channels
                    _videoList.postValue(channels)

                    // 初始化所有视频的 loading 状态
                    val loadingStates = channels.mapIndexed { index, _ -> index to true }.toMap()
                    _videoLoadingStates.postValue(loadingStates)
                    _isAllVideosLoaded.postValue(false)

                    // 初始化所有视频的错误状态为false
                    val errorStates = channels.map { false }
                    _videoErrorStates.postValue(errorStates)
                } else {
                    // 没有视频数据时，设置为空列表
                    Log.d("VideoControl", "接口返回空数据，隐藏视频区域")
                    allChannels = emptyList()
                    _videoList.postValue(emptyList())
                    _videoLoadingStates.postValue(emptyMap())
                    _isAllVideosLoaded.postValue(true)
                    _videoErrorStates.postValue(emptyList())
                }
            } catch (e: Exception) {
                Log.e("VideoControl", "获取视频通道失败，使用模拟数据", e)
                val mock = com.fuusy.project.repo.getMockVideoChannels()
                allChannels = mock
                _videoList.postValue(mock)

                // 初始化所有视频的 loading 状态
                val loadingStates = mock.mapIndexed { index, _ -> index to true }.toMap()
                _videoLoadingStates.postValue(loadingStates)
                _isAllVideosLoaded.postValue(false)

                // 初始化所有视频的错误状态为false
                val errorStates = mock.map { false }
                _videoErrorStates.postValue(errorStates)
            }
        }
    }

    /**
     * 设置指定视频的加载状态
     */
    fun setVideoLoadingState(videoIndex: Int, isLoading: Boolean) {
        val currentStates = _videoLoadingStates.value?.toMutableMap() ?: mutableMapOf()
        currentStates[videoIndex] = isLoading
        _videoLoadingStates.postValue(currentStates)

        // 检查是否所有视频都加载完成
        val allLoaded = currentStates.values.all { !it }
        _isAllVideosLoaded.postValue(allLoaded)
    }

    /**
     * 更新多个视频的加载状态
     */
    fun updateVideoLoadingStates(loadingStates: Map<Int, Boolean>) {
        _videoLoadingStates.postValue(loadingStates)

        // 检查是否所有视频都加载完成
        val allLoaded = loadingStates.values.all { !it }
        _isAllVideosLoaded.postValue(allLoaded)
    }

    /**
     * 设置指定视频的错误状态
     */
    fun setVideoErrorState(videoIndex: Int, hasError: Boolean) {
        val currentErrorStates = _videoErrorStates.value?.toMutableList() ?: mutableListOf()

        // 确保列表大小足够
        while (currentErrorStates.size <= videoIndex) {
            currentErrorStates.add(false)
        }

        currentErrorStates[videoIndex] = hasError
        _videoErrorStates.postValue(currentErrorStates)
        
        Log.d("VideoErrorStates", "设置视频[$videoIndex]错误状态: $hasError, 当前所有状态: ${currentErrorStates.joinToString()}")
    }

    /**
     * 更新多个视频的错误状态
     */
    fun updateVideoErrorStates(errorStates: List<Boolean>) {
        _videoErrorStates.postValue(errorStates)
    }

    /**
     * 清除所有视频的错误状态
     */
    fun clearAllVideoErrors() {
        val currentErrorStates = _videoErrorStates.value?.toMutableList() ?: mutableListOf()
        currentErrorStates.fill(false)
        _videoErrorStates.postValue(currentErrorStates)
    }
} 