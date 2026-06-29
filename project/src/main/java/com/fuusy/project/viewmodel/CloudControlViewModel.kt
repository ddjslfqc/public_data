package com.fuusy.project.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.fuusy.project.ui.VLCPlayer
import com.fuusy.project.repo.PtzControlRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.util.Log

class CloudControlViewModel(application: Application) : AndroidViewModel(application) {
    // 通道流地址 - 支持动态设置
    private var _channelUrls = arrayOf("")
    val channelUrls: Array<String> get() = _channelUrls

    private val _currentChannel = MutableLiveData(0)
    val currentChannel: LiveData<Int> = _currentChannel

    private var _vlcPlayer: VLCPlayer? = null

    // 添加设备信息存储
    private var currentCameraip: String = ""
    private var currentChannelId: String = ""
    private var currentDeviceId: String = ""
    private var currentRegion: String = ""

    fun setVideoInfo(cameraip: String, channelId: String, deviceId: String = "", region: String = "") {
        Log.d("VideoControl", "CloudControlViewModel设置视频信息: cameraip=$cameraip, channelId=$channelId, deviceId=$deviceId, region=$region")
        currentCameraip = cameraip
        currentChannelId = channelId
        currentDeviceId = deviceId
        currentRegion = region
    }

    fun getCurrentCameraip(): String {
        Log.d("VideoControl", "CloudControlViewModel获取当前cameraip: $currentCameraip")
        return currentCameraip
    }
    
    fun getCurrentChannelId(): String {
        Log.d("VideoControl", "CloudControlViewModel获取当前channelId: $currentChannelId")
        return currentChannelId
    }
    
    fun getCurrentDeviceId(): String {
        Log.d("VideoControl", "CloudControlViewModel获取当前deviceId: $currentDeviceId")
        return currentDeviceId
    }
    
    fun getCurrentRegion(): String {
        Log.d("VideoControl", "CloudControlViewModel获取当前region: $currentRegion")
        return currentRegion
    }

    fun setVlcPlayer(player: VLCPlayer?) {
        Log.d("VideoControl", "设置VLC播放器: ${player != null}")
        _vlcPlayer = player
    }

    fun setChannelUrls(urls: List<String>) {
        Log.d("VideoControl", "设置通道URL列表: ${urls.size} 个URL")
        _channelUrls = urls.toTypedArray()
    }

    fun switchChannel(index: Int) {
        Log.d("VideoControl", "切换通道: $index")
        _currentChannel.value = index
    }

    fun getCurrentUrl(): String {
        val url = channelUrls[_currentChannel.value ?: 0]
        Log.d("VideoControl", "获取当前URL: $url")
        return url
    }

    // 获取域名
    fun getDomain(): String {
        val url = getCurrentUrl()
        val pattern = """(?:https?://|rtsp://)?([^:/]+)(?::(\d+))?""".toRegex()
        val matchResult = pattern.find(url)
        val domain = matchResult?.groupValues?.get(1) ?: ""
        Log.d("VideoControl", "获取域名: $domain")
        return domain
    }

    // 获取端口
    fun getPort(): Int {
        val url = getCurrentUrl()
        val pattern = """(?:https?://|rtsp://)?[^:/]+:(\d+)""".toRegex()
        val matchResult = pattern.find(url)
        val port = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: -1
        Log.d("VideoControl", "获取端口: $port")
        return port
    }

    fun stopPlayer() {
        Log.d("VideoControl", "停止播放器")
        _vlcPlayer?.let { player ->
            // 使用安全的释放方法
            Thread {
                try {
                    player.safeStop()
                    player.safeRelease()
                    Log.d("VideoControl", "播放器释放完成")
                } catch (e: Exception) {
                    Log.e("VideoControl", "子线程stopPlayer释放异常: ${e.message}")
                }
            }.start()
        }
        _vlcPlayer = null
    }

    override fun onCleared() {
        Log.d("VideoControl", "CloudControlViewModel被清理")
        super.onCleared()
        stopPlayer()
    }

    // 云台控制接口调用
    fun controlPtz(
        deviceId: String,
        channelId: String,
        command: String,
        horizonSpeed: String = "100",
        verticalSpeed: String = "100",
        zoomSpeed: String = "100",
        accessToken: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        Log.d("VideoControl", "CloudControlViewModel云台控制: deviceId=$deviceId, channelId=$channelId, command=$command")
        viewModelScope.launch {
            val result = PtzControlRepository.controlPtz(
                deviceId = deviceId,
                channelId = channelId,
                command = command,
                horizonSpeed = horizonSpeed,
                verticalSpeed = verticalSpeed,
                zoomSpeed = zoomSpeed,
                accessToken = accessToken
            )
            if (result.isSuccess) {
                Log.d("VideoControl", "CloudControlViewModel云台控制成功: ${result.getOrNull()}")
                onResult(true, result.getOrNull())
            } else {
                Log.e("VideoControl", "CloudControlViewModel云台控制失败: ${result.exceptionOrNull()?.message}")
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }
} 