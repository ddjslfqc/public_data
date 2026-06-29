package com.fuusy.project.network

import android.util.Log
import com.fuusy.project.bean.AiAlarmRecord
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit

class AiWebSocketManager {
    companion object {
        private const val TAG = "AiWebSocketManager"
        private const val WEBSOCKET_URL = "ws://111.229.81.186:9250/WebSocketDeviceAppAiServer/YXKJ0001"
    }

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var isConnected = false
    private val gson = Gson()

    var onMessageReceived: ((AiAlarmRecord) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private fun initClient() {
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    fun connect() {
        if (isConnected) return
        try {
            initClient()
            val request = Request.Builder().url(WEBSOCKET_URL).build()
            webSocket = client?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnected = true
                    onConnectionStatusChanged?.invoke(true)
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val record = gson.fromJson(text, AiAlarmRecord::class.java)
                        onMessageReceived?.invoke(record)
                    } catch (e: Exception) {
                        onError?.invoke("解析AI告警数据失败: ${e.message}")
                    }
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isConnected = false
                    onConnectionStatusChanged?.invoke(false)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnected = false
                    onConnectionStatusChanged?.invoke(false)
                    onError?.invoke("连接失败: ${t.message}")
                }
            })
        } catch (e: Exception) {
            onError?.invoke("连接异常: ${e.message}")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "主动断开")
        webSocket = null
        isConnected = false
        onConnectionStatusChanged?.invoke(false)
    }

    fun isConnected(): Boolean = isConnected
} 