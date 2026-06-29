package com.fuusy.hiddendanger.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CircleProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f // 0~1
        set(value) {
            field = value
            invalidate()
        }
    var isFull: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        color = Color.WHITE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val fillPaintFull = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy) - 16f

        // 内圈
        canvas.drawCircle(cx, cy, radius - 16f, if (isFull) fillPaintFull else fillPaint)
        // 外环
        canvas.drawCircle(cx, cy, radius, ringPaint)
        // 进度环
        val sweepAngle = 360 * progress
        val progressPaint = Paint(ringPaint).apply { color = Color.RED }
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            -90f, sweepAngle, false, progressPaint
        )
    }
} 