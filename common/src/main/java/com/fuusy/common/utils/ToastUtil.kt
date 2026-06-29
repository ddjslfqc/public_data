package com.fuusy.common.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

object ToastUtil {

    private var toast: Toast? = null

    fun showCustomToast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            toast?.cancel()
            toast = Toast(context.applicationContext)
            val textView = TextView(context.applicationContext)
            textView.text = text
            textView.setTextColor(Color.WHITE) // 白色字体
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            textView.setPadding(
                dp2px(context, 20f), dp2px(context, 12f), dp2px(context, 20f), dp2px(context, 12f)
            )
            val gd = GradientDrawable()
            gd.cornerRadius = dp2px(context, 8f).toFloat()
            gd.setColor(Color.parseColor("#CC000000")) // 半透明黑色背景
            textView.background = gd
            toast?.view = textView
            toast?.setGravity(Gravity.CENTER, 0, 0)
            toast?.duration = Toast.LENGTH_SHORT
            toast?.show()
        }
    }

    private fun dp2px(context: Context, dipValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dipValue, context.resources.displayMetrics
        ).toInt()
    }
}