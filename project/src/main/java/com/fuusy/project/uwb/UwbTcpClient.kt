package com.fuusy.project.uwb

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class UwbTcpClient(
    private val onBytesReceived: (ByteArray) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "UwbTcpClient"
        private const val READ_BUFFER_SIZE = 4096
        private const val CONNECT_TIMEOUT_MS = 8000
    }

    private var socket: Socket? = null
    private var readerThread: Thread? = null
    private val running = AtomicBoolean(false)

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    fun connect(host: String, port: Int) {
        disconnect()
        try {
            val newSocket = Socket()
            newSocket.tcpNoDelay = true
            newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket = newSocket
            running.set(true)
            onConnectionChanged(true)
            startReader(newSocket)
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            onError("连接失败: ${e.message}")
            onConnectionChanged(false)
        }
    }

    fun disconnect() {
        running.set(false)
        readerThread?.interrupt()
        readerThread = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        onConnectionChanged(false)
    }

    fun send(data: ByteArray): Boolean {
        val output = socket?.getOutputStream() ?: run {
            onError("未连接，无法发送")
            return false
        }
        return try {
            output.write(data)
            output.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
            onError("发送失败: ${e.message}")
            disconnect()
            false
        }
    }

    private fun startReader(socket: Socket) {
        readerThread = Thread({
            val input: InputStream = socket.getInputStream()
            val buffer = ByteArray(READ_BUFFER_SIZE)
            while (running.get() && !Thread.currentThread().isInterrupted) {
                try {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    onBytesReceived(buffer.copyOf(read))
                } catch (e: Exception) {
                    if (running.get()) {
                        Log.e(TAG, "read failed", e)
                        onError("读取中断: ${e.message}")
                    }
                    break
                }
            }
            if (running.getAndSet(false)) {
                onConnectionChanged(false)
            }
        }, "UwbTcpReader").also { it.isDaemon = true; it.start() }
    }
}
