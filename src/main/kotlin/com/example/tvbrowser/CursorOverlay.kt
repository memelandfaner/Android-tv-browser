package com.example.tvbrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CursorOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38bdf8")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2238bdf8")
        style = Paint.Style.FILL
    }

    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284c7")
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var cursorX = 960f
    private var cursorY = 540f
    private var isEnabledCursor = false

    fun setCursorEnabled(enabled: Boolean) {
        this.isEnabledCursor = enabled
        visibility = if (enabled) VISIBLE else GONE
        invalidate()
    }

    fun isCursorActive(): Boolean = isEnabledCursor

    fun getCursorX(): Float = cursorX
    fun getCursorY(): Float = cursorY

    fun setCursorPosition(x: Float, y: Float) {
        this.cursorX = x.coerceIn(20f, width.toFloat().coerceAtLeast(1920f) - 20f)
        this.cursorY = y.coerceIn(20f, height.toFloat().coerceAtLeast(1080f) - 20f)
        invalidate()
    }

    fun moveBy(dx: Float, dy: Float) {
        val newX = (cursorX + dx).coerceIn(20f, width.toFloat().coerceAtLeast(1920f) - 20f)
        val newY = (cursorY + dy).coerceIn(20f, height.toFloat().coerceAtLeast(1080f) - 20f)
        this.cursorX = newX
        this.cursorY = newY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isEnabledCursor) return

        // 🌟 Focus Halo Glow Ring
        canvas.drawCircle(cursorX, cursorY, 24f, glowPaint)
        // Outer accent ring
        canvas.drawCircle(cursorX, cursorY, 14f, outerPaint)
        // Solid core
        canvas.drawCircle(cursorX, cursorY, 9f, innerPaint)
        // White precision center dot
        canvas.drawCircle(cursorX, cursorY, 3.5f, centerPaint)
    }
}
