package com.bleprovision.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** 顶部三步进度指示（扫描 - 配置 - 完成） */
class StepIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var step: Int = 1
        set(value) {
            field = value.coerceIn(1, 3)
            invalidate()
        }

    private val labels = arrayOf("扫描", "配置", "完成")
    private val colorActive = Color.parseColor("#1E88E5")
    private val colorInactive = Color.parseColor("#C9CDD4")

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val cy = dp(18f)
        val r = dp(8f)
        val cx = floatArrayOf(w * 0.2f, w * 0.5f, w * 0.8f)

        linePaint.strokeWidth = dp(2f)
        for (i in 0..1) {
            linePaint.color = if (step > i + 1) colorActive else colorInactive
            canvas.drawLine(cx[i] + r + dp(6f), cy, cx[i + 1] - r - dp(6f), cy, linePaint)
        }

        for (i in 0..2) {
            val active = step >= i + 1
            val completed = step > i + 1
            circlePaint.color = if (active) colorActive else colorInactive
            canvas.drawCircle(cx[i], cy, r, circlePaint)
            if (completed) {
                textPaint.textSize = dp(10f)
                textPaint.color = Color.WHITE
                canvas.drawText("✓", cx[i], cy + dp(3.5f), textPaint)
            } else if (active) {
                circlePaint.color = Color.WHITE
                canvas.drawCircle(cx[i], cy, r - dp(3f), circlePaint)
            }
            textPaint.textSize = dp(12f)
            textPaint.color = if (active) colorActive else colorInactive
            canvas.drawText(labels[i], cx[i], cy + r + dp(18f), textPaint)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
