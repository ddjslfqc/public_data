package com.fuusy.project.uwb

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuusy.common.utils.SpUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class UwbDebugViewModel : ViewModel() {

    private val _connected = MutableLiveData(false)
    val connected: LiveData<Boolean> = _connected

    private val _logs = MutableLiveData<List<UwbLogEntry>>(emptyList())
    val logs: LiveData<List<UwbLogEntry>> = _logs

    private val logItems = ArrayDeque<UwbLogEntry>()
    private val logId = AtomicLong(0)
    private val frameBuffer = Gnm1000Protocol.FrameBuffer()
    private var seqCounter = 1

    private var tcpClient: UwbTcpClient? = null

    fun loadSavedHost(): String =
        SpUtils.getString(KEY_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST

    fun loadSavedPort(): String =
        SpUtils.getString(KEY_PORT)?.takeIf { it.isNotBlank() } ?: DEFAULT_PORT

    fun saveConnection(host: String, port: String) {
        SpUtils.put(KEY_HOST, host)
        SpUtils.put(KEY_PORT, port)
    }

    fun connect(host: String, port: String) {
        saveConnection(host, port)
        val portInt = port.toIntOrNull()
        if (host.isBlank() || portInt == null) {
            appendInfo("请输入有效的 IP 和端口")
            return
        }
        disconnectInternal()
        frameBuffer.clear()
        appendInfo("正在连接 $host:$portInt ...")

        tcpClient = UwbTcpClient(
            onBytesReceived = { bytes ->
                viewModelScope.launch(Dispatchers.Main) {
                    handleReceived(bytes)
                }
            },
            onConnectionChanged = { connected ->
                viewModelScope.launch(Dispatchers.Main) {
                    _connected.value = connected
                    if (connected) {
                        appendInfo("已连接 $host:$portInt")
                    } else {
                        appendInfo("连接已断开")
                    }
                }
            },
            onError = { message ->
                viewModelScope.launch(Dispatchers.Main) {
                    appendInfo(message)
                }
            }
        )
        viewModelScope.launch(Dispatchers.IO) {
            tcpClient?.connect(host.trim(), portInt)
        }
    }

    fun disconnect() {
        disconnectInternal()
        appendInfo("已手动断开")
    }

    fun clearLogs() {
        logItems.clear()
        _logs.value = emptyList()
    }

    fun sendHex(hex: String) {
        val bytes = Gnm1000Protocol.parseHex(hex)
        if (bytes == null) {
            appendInfo("HEX 格式无效，示例: 59 4D 06 01 00 00 00 XX")
            return
        }
        sendRaw(bytes, "手动发送")
    }

    fun sendQueryModuleInfo() {
        sendBuiltFrame(0x06, "查询标签/模块信息")
    }

    fun sendReadModuleInfo() {
        sendBuiltFrame(0x0B, "读取基站模块信息")
    }

    private fun sendBuiltFrame(cmdType: Int, label: String) {
        val frame = Gnm1000Protocol.buildFrame(cmdType, nextSeq())
        sendRaw(frame, label)
    }

    private fun sendRaw(bytes: ByteArray, label: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                tcpClient?.send(bytes) == true
            }
            if (ok) {
                appendTx(label, bytes)
            }
        }
    }

    private fun handleReceived(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        frameBuffer.append(bytes).forEach { frame ->
            val result = Gnm1000Protocol.parseFrameResult(frame.raw)
            val summary = result?.pdoaRecords?.firstOrNull()?.toReadableLine()
                ?: frame.summary
            appendRx(summary, frame.raw)
        }
    }

    private fun appendRx(summary: String, raw: ByteArray) {
        appendLog(
            UwbLogEntry(
                id = logId.incrementAndGet(),
                timestamp = System.currentTimeMillis(),
                direction = UwbLogDirection.RX,
                title = "收到数据",
                detail = summary,
                hex = Gnm1000Protocol.toHex(raw)
            )
        )
    }

    private fun appendTx(label: String, raw: ByteArray) {
        appendLog(
            UwbLogEntry(
                id = logId.incrementAndGet(),
                timestamp = System.currentTimeMillis(),
                direction = UwbLogDirection.TX,
                title = label,
                detail = Gnm1000Protocol.toHex(raw),
                hex = Gnm1000Protocol.toHex(raw)
            )
        )
    }

    private fun appendInfo(message: String) {
        appendLog(
            UwbLogEntry(
                id = logId.incrementAndGet(),
                timestamp = System.currentTimeMillis(),
                direction = UwbLogDirection.INFO,
                title = "系统",
                detail = message
            )
        )
    }

    private fun appendLog(entry: UwbLogEntry) {
        logItems.addFirst(entry)
        while (logItems.size > MAX_LOGS) {
            logItems.removeLast()
        }
        _logs.value = logItems.toList()
    }

    private fun nextSeq(): Int {
        val value = seqCounter
        seqCounter = (seqCounter + 1) and 0xFFFF
        if (seqCounter == 0) seqCounter = 1
        return value
    }

    private fun disconnectInternal() {
        tcpClient?.disconnect()
        tcpClient = null
        _connected.value = false
    }

    override fun onCleared() {
        disconnectInternal()
        super.onCleared()
    }

    companion object {
        private const val KEY_HOST = "uwb_debug_host"
        private const val KEY_PORT = "uwb_debug_port"
        private const val DEFAULT_HOST = "192.168.1.100"
        private const val DEFAULT_PORT = "8899"
        private const val MAX_LOGS = 200

        fun formatTime(timestamp: Long): String =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }
}
