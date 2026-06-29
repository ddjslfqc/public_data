package com.fuusy.project.network

import android.util.Log
import com.fuusy.common.network.ServerConfig
import com.fuusy.project.bean.WebSocketMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket 管理器
 * 负责与 WebSocket 服务器的连接、消息发送和接收
 */
class GasWebSocketManager {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val WEBSOCKET_URL = "ws://10.237.25.119:9250/WebSocketAppGasServer/YXKJ0001"
        private const val RECONNECT_DELAY = 5000L // 重连延迟5秒
        private const val MAX_RECONNECT_ATTEMPTS = 5 // 最大重连次数
    }

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var isConnected = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    
    private val gson = Gson()
    
    // 回调接口
    var onMessageReceived: ((WebSocketMessage) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * 初始化 WebSocket 客户端
     */
    private fun initClient() {
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // 心跳检测
            .build()
    }

    /**
     * 连接 WebSocket
     */
    fun connect() {
        if (isConnected) {
            Log.d(TAG, "WebSocket 已经连接")
            return
        }

        try {
            initClient()
            val request = Request.Builder()
                .url(WEBSOCKET_URL)
                .build()

            webSocket = client?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket 连接成功")
                    isConnected = true
                    reconnectAttempts = 0
                    onConnectionStatusChanged?.invoke(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "收到消息: $text")
                    handleMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket 连接关闭: code=$code, reason=$reason")
                    isConnected = false
                    onConnectionStatusChanged?.invoke(false)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket 连接失败: ${t.message}")
                    isConnected = false
                    onConnectionStatusChanged?.invoke(false)
                    onError?.invoke("连接失败: ${t.message}")
                    
                    // 自动重连
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "连接 WebSocket 时发生异常: ${e.message}")
            onError?.invoke("连接异常: ${e.message}")
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "主动断开 WebSocket 连接")
        reconnectJob?.cancel()
        webSocket?.close(1000, "主动断开")
        webSocket = null
        isConnected = false
        onConnectionStatusChanged?.invoke(false)
    }

    /**
     * 发送消息
     */
    fun sendMessage(message: String): Boolean {
        return if (isConnected && webSocket != null) {
            val success = webSocket?.send(message) ?: false
            if (success) {
                Log.d(TAG, "发送消息成功: $message")
            } else {
                Log.e(TAG, "发送消息失败: $message")
            }
            success
        } else {
            Log.e(TAG, "WebSocket 未连接，无法发送消息")
            false
        }
    }

    /**
     * 发送 JSON 消息
     */
    fun sendJsonMessage(message: Any): Boolean {
        return try {
            val json = gson.toJson(message)
            sendMessage(json)
        } catch (e: Exception) {
            Log.e(TAG, "序列化消息失败: ${e.message}")
            false
        }
    }

    /**
     * 处理接收到的消息
     */
    private fun handleMessage(message: String) {
        try {
            // 尝试解析为 GasData
            val gasData = gson.fromJson(message, WebSocketMessage.GasData::class.java)
            onMessageReceived?.invoke(gasData)
            return
        } catch (e: JsonSyntaxException) {
            // 不是 GasData，继续尝试其他类型
        }

        try {
            // 尝试解析为 Alert
            val alert = gson.fromJson(message, WebSocketMessage.Alert::class.java)
            onMessageReceived?.invoke(alert)
            return
        } catch (e: JsonSyntaxException) {
            // 不是 Alert，继续尝试其他类型
        }

        try {
            // 尝试解析为 Status
            val status = gson.fromJson(message, WebSocketMessage.Status::class.java)
            onMessageReceived?.invoke(status)
            return
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "无法解析消息: $message")
        }
    }

    /**
     * 安排重连
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "达到最大重连次数，停止重连")
            onError?.invoke("连接失败，已达到最大重连次数")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            reconnectAttempts++
            Log.d(TAG, "准备重连，第 $reconnectAttempts 次尝试")
            delay(RECONNECT_DELAY)
            connect()
        }
    }

    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = isConnected

    /**
     * 获取连接状态信息
     */
    fun getConnectionInfo(): String {
        return if (isConnected) {
            "已连接"
        } else {
            "未连接"
        }
    }
} 