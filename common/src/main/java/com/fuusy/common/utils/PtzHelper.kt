package com.fuusy.common.utils

import android.view.MotionEvent
import android.view.TextureView
import android.view.View

object PtzHelper {
    fun getPtzTouchListener(
        onControlPtz: (command: String, onResult: (Boolean, String?) -> Unit) -> Unit,
        command: String,
        showToast: (String) -> Unit,
        buttonView: View,
        pressedResId: Int,
        normalResId: Int,
        vibrator: android.os.Vibrator? = null
    ): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    buttonView.setBackgroundResource(pressedResId)
                    vibrator?.vibrate(30)
                    onControlPtz(command) { success, msg ->
                        showToast(if (success) "$command-控制成功" else "$command-控制失败: $msg")
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    buttonView.setBackgroundResource(normalResId)
                    onControlPtz("stop") { success, msg ->
                        showToast(if (success) "停止-控制成功" else "停止-控制失败: $msg")
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun reverseVideo(textureView: TextureView) {
        textureView.rotation = if (textureView.rotation == 0f) 180f else 0f
    }
} 