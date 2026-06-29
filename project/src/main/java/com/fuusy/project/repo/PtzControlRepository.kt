package com.fuusy.project.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import android.util.Log
import com.fuusy.common.network.ServerConfig

object PtzControlRepository {
    private val client = OkHttpClient()

    suspend fun controlPtz(
        baseUrl: String = ServerConfig.getYunBaseUrl(),
        deviceId: String,
        channelId: String,
        command: String,
        horizonSpeed: String = "30",
        verticalSpeed: String = "30",
        zoomSpeed: String = "30",
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "${baseUrl}/api/ptz/control/$deviceId/$channelId" +
                "?deviceId=$deviceId&channelId=$channelId&command=$command" +
                "&horizonSpeed=$horizonSpeed&verticalSpeed=$verticalSpeed&zoomSpeed=$zoomSpeed"

        Log.d("VideoControl", "PtzControlRepository云台控制请求: url=$url")
        Log.d("VideoControl", "PtzControlRepository云台控制参数: deviceId=$deviceId, channelId=$channelId, command=$command")

        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, ByteArray(0)))
            .addHeader("access-token", accessToken)
            .build()

        try {
            val response = client.newCall(request).execute()
            Log.d("VideoControl", "PtzControlRepository云台控制响应: code=${response.code}, success=${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d("VideoControl", "PtzControlRepository云台控制成功: $responseBody")
                Result.success(responseBody)
            } else {
                Log.e("VideoControl", "PtzControlRepository云台控制失败: ${response.code}")
                Result.failure(Exception("请求失败: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("VideoControl", "PtzControlRepository云台控制异常", e)
            Result.failure(e)
        }
    }
}