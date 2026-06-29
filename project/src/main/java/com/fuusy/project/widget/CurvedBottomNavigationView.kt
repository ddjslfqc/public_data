package com.fuusy.project.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CurvedBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 凸起参数
    private val curveRadius = 80f
    private val curveHeight = 40f

    init {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL

        // 阴影设置
        shadowPaint.color = Color.parseColor("#20000000")
        shadowPaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)

        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.let { drawCurvedBackground(it) }
    }

    private fun drawCurvedBackground(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2

        path.reset()

        // 起始点
        path.moveTo(0f, 0f)

        // 左侧直线到凸起开始
        path.lineTo(centerX - curveRadius, 0f)

        // 左侧凸起曲线
        path.quadTo(
            centerX - curveRadius / 2, -curveHeight,
            centerX, -curveHeight
        )

        // 右侧凸起曲线
        path.quadTo(
            centerX + curveRadius / 2, -curveHeight,
            centerX + curveRadius, 0f
        )

        // 右侧直线
        path.lineTo(width, 0f)
        path.lineTo(width, height)
        path.lineTo(0f, height)
        path.close()

        // 绘制阴影
        canvas.drawPath(path, shadowPaint)

        // 绘制背景
        canvas.drawPath(path, paint)
    }
}