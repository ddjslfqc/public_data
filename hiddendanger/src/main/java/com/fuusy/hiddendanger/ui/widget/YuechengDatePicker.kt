package com.fuusy.hiddendanger.ui.widget

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.NumberPicker
import android.widget.TextView
import com.fuusy.hiddendanger.R
import java.util.Calendar

/**
 * 统一弹出系统日期选择器，避免自定义 Alert Overlay 主题导致日历数字「消失」。
 */
object YuechengDatePicker {

    fun show(
        context: Context,
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hideDay: Boolean = false,
        onPicked: (year: Int, month: Int, dayOfMonth: Int) -> Unit
    ): DatePickerDialog {
        val dialog = DatePickerDialog(
            context,
            R.style.Theme_Yuecheng_DatePickerDialog,
            { _, y, m, d -> onPicked(y, m, d) },
            year,
            month,
            dayOfMonth
        )
        dialog.setOnShowListener {
            tintPickerText(dialog.datePicker)
            if (hideDay) {
                hideDaySpinner(dialog.datePicker)
            }
        }
        dialog.show()
        // 部分机型 onShow 前就画完了，再补一次
        dialog.datePicker.post {
            tintPickerText(dialog.datePicker)
            if (hideDay) hideDaySpinner(dialog.datePicker)
        }
        return dialog
    }

    fun showFromCalendar(
        context: Context,
        calendar: Calendar = Calendar.getInstance(),
        hideDay: Boolean = false,
        onPicked: (year: Int, month: Int, dayOfMonth: Int) -> Unit
    ): DatePickerDialog = show(
        context = context,
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH),
        dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
        hideDay = hideDay,
        onPicked = onPicked
    )

    private fun hideDaySpinner(picker: DatePicker) {
        val day = picker.findViewById<View>(
            picker.resources.getIdentifier("day", "id", "android")
        )
        day?.visibility = View.GONE
    }

    /** 强制日历/滚轮文字为深色，避免 OEM 主题把字刷成白色 */
    private fun tintPickerText(root: View?) {
        if (root == null) return
        when (root) {
            is TextView -> {
                val c = root.currentTextColor
                // 过亮（接近白）或透明时，改成正文色
                if (Color.alpha(c) < 40 || isNearWhite(c)) {
                    root.setTextColor(0xFF111827.toInt())
                }
            }
            is NumberPicker -> {
                try {
                    val fields = NumberPicker::class.java.declaredFields
                    for (f in fields) {
                        if (f.type == IntArray::class.java && f.name == "mSelectorIndices") continue
                        if (f.name == "mSelectorWheelPaint") {
                            f.isAccessible = true
                            (f.get(root) as? android.graphics.Paint)?.color = 0xFF111827.toInt()
                        }
                        if (f.type == TextView::class.java) {
                            f.isAccessible = true
                            (f.get(root) as? TextView)?.setTextColor(0xFF111827.toInt())
                        }
                    }
                    root.invalidate()
                } catch (_: Exception) {
                }
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                tintPickerText(root.getChildAt(i))
            }
        }
    }

    private fun isNearWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 230 && g > 230 && b > 230
    }
}
