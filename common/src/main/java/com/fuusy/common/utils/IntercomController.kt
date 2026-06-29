package com.fuusy.common.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import com.fuusy.common.intercom.IntercomService

class IntercomController(
    private val activity: Activity,
    private val baseUrlAndPort: String,
    private val sn: String,
    private val chid: String,
    private val pushKey: String,
    private val onStateChanged: ((Boolean) -> Unit)? = null,
    private val onLog: ((String) -> Unit)? = null
) {
    @Volatile
    private var isPushing = false
    @Volatile
    private var isStarting = false
    @Volatile
    private var isStopping = false
    private val opLock = Any()
    private var lastToggleTs = 0L
    private val CLICK_DEBOUNCE_MS = 500L

    fun toggleIntercom() {
        val now = System.currentTimeMillis()
        if (now - lastToggleTs < CLICK_DEBOUNCE_MS) {
            onLog?.invoke("快速点击已忽略")
            return
        }
        lastToggleTs = now
        if (isPushing) stopIntercom() else startIntercom()
    }

    fun isIntercomActive(): Boolean = isPushing

    fun startIntercom() {
        if (!checkAndRequestAudioPermission()) return
        synchronized(opLock) {
            if (isStarting || isStopping || isPushing) {
                onLog?.invoke("开始对讲被忽略：状态中 isStarting=$isStarting isStopping=$isStopping isPushing=$isPushing")
                return
            }
            isStarting = true
        }
        onLog?.invoke("准备开始对讲")
        Thread {
            try {
                val client = OkHttpClient()
                val url =
                    "${baseUrlAndPort}/api/play/broadcast/$sn/$chid?timeout=30&broadcastMode=true"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody)
                    if (jsonObject.getInt("code") == 0) {
                        val streamInfo =
                            jsonObject.getJSONObject("data").getJSONObject("streamInfo")
                        val rtcUrl = streamInfo.getString("rtc")
                        val rtcsUrl = streamInfo.getString("rtcs")
                        val pushUrl = if (rtcUrl.isNotEmpty()) {
                            "${rtcUrl}&sign=${md5(pushKey)}"
                        } else if (rtcsUrl.isNotEmpty()) {
                            "${rtcsUrl}&sign=${md5(pushKey)}"
                        } else {
                            postToast("没有可用的推流地址")
                            isStarting = false
                            return@Thread
                        }
                        activity.runOnUiThread {
                            val intent = Intent(activity, IntercomService::class.java).apply {
                                action = IntercomService.ACTION_START
                                putExtra(IntercomService.EXTRA_SIGNALING_URL, pushUrl)
                            }
                            try { activity.startForegroundService(intent) } catch (_: Exception) { activity.startService(intent) }
                            isPushing = true
                            isStarting = false
                            onStateChanged?.invoke(true)
                            postToast("对讲已开启")
                        }
                    } else {
                        postToast("API返回错误: ${jsonObject.getString("msg")}")
                        isStarting = false
                    }
                } else {
                    postToast("API请求失败: ${response.code}")
                    isStarting = false
                }
            } catch (e: Exception) {
                postToast("API请求异常: ${e.message}")
                isStarting = false
            }
        }.start()
    }

    fun stopIntercom() {
        synchronized(opLock) {
            if (isStopping) {
                onLog?.invoke("停止对讲进行中，忽略重复请求")
                return
            }
            isStopping = true
        }
        Thread {
            try {
                val intent = Intent(activity, IntercomService::class.java).apply { action = IntercomService.ACTION_STOP }
                try { activity.startService(intent) } catch (_: Exception) {}
            } catch (e: Exception) {
                onLog?.invoke("停止对讲异常: ${e.message}")
            } finally {
                isPushing = false
                isStarting = false
                activity.runOnUiThread {
                    onStateChanged?.invoke(false)
                    postToast("对讲已关闭")
                }
                isStopping = false
            }
        }.start()
    }

    private fun checkAndRequestAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                1
            )
            postToast("请先授权麦克风权限")
            return false
        }
        return true
    }

    private fun postToast(msg: String) {
        activity.runOnUiThread { (activity as? com.fuusy.common.base.BaseVmActivity<*>)?.showToast(msg) ?: Unit }
        onLog?.invoke(msg)
    }

    private fun md5(str: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(str.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}