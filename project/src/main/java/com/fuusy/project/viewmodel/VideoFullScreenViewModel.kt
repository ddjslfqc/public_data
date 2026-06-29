package com.fuusy.project.viewmodel

import androidx.lifecycle.MutableLiveData
import com.fuusy.common.base.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.fuusy.project.repo.PtzControlRepository
import android.util.Log

class VideoFullScreenViewModel : BaseViewModel() {
    val streamUrl = MutableLiveData<String>()
    
    // 添加设备信息存储
    private var currentCameraip: String = ""
    private var currentChannelId: String = ""
    private var currentDeviceId: String = ""
    private var currentRegion: String = ""

    fun setVideoInfo(cameraip: String, channelId: String, deviceId: String = "", region: String = "") {
        Log.d("VideoControl", "设置视频信息: cameraip=$cameraip, channelId=$channelId, deviceId=$deviceId, region=$region")
        currentCameraip = cameraip
        currentChannelId = channelId
        currentDeviceId = deviceId
        currentRegion = region
    }

    fun getCurrentCameraip(): String {
        Log.d("VideoControl", "获取当前cameraip: $currentCameraip")
        return currentCameraip
    }
    
    fun getCurrentChannelId(): String {
        Log.d("VideoControl", "获取当前channelId: $currentChannelId")
        return currentChannelId
    }
    
    fun getCurrentDeviceId(): String {
        Log.d("VideoControl", "获取当前deviceId: $currentDeviceId")
        return currentDeviceId
    }
    
    fun getCurrentRegion(): String {
        Log.d("VideoControl", "获取当前region: $currentRegion")
        return currentRegion
    }

    fun controlPtz(
        command: String,
        horizonSpeed: String = "100",
        verticalSpeed: String = "100",
        zoomSpeed: String = "100",
        accessToken: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        Log.d("VideoControl", "云台控制(简化版): command=$command, cameraip=$currentCameraip, channelId=$currentChannelId")
        // 使用当前存储的设备信息
        controlPtz(
            deviceId = currentCameraip, // 使用cameraip作为deviceId
            channelId = currentChannelId,
            command = command,
            horizonSpeed = horizonSpeed,
            verticalSpeed = verticalSpeed,
            zoomSpeed = zoomSpeed,
            accessToken = accessToken,
            onResult = onResult
        )
    }

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
        Log.d("VideoControl", "云台控制: deviceId=$deviceId, channelId=$channelId, command=$command")
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
                Log.d("VideoControl", "云台控制成功: ${result.getOrNull()}")
                onResult(true, result.getOrNull())
            } else {
                Log.e("VideoControl", "云台控制失败: ${result.exceptionOrNull()?.message}")
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }
} 