package com.inventory.app.mobile.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#60000000") // Semi-transparent black
    }

    private val borderPaint = Paint().apply {
        color = Color.GREEN // Border color (Change if needed)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
    }

    private val frameRect: RectF by lazy {
        val widthRatio = 0.8f // 80% of screen width
        val heightRatio = 0.8f // 50% of screen height
        val left = (width * (1 - widthRatio)) / 2
        val top = (height * (1 - heightRatio)) / 2
        val right = width - left
        val bottom = height - top
        RectF(left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw semi-transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Clear the scan area (Transparent)
        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawRect(frameRect, clearPaint)

        // Draw border around the scan area
        canvas.drawRect(frameRect, borderPaint)

        // Draw corner lines
        val cornerLength = 50f
        drawCorners(canvas, frameRect, cornerLength)
    }

    private fun drawCorners(canvas: Canvas, rect: RectF, length: Float) {
        // Top-left corner
        canvas.drawLine(rect.left, rect.top, rect.left + length, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + length, cornerPaint)

        // Top-right corner
        canvas.drawLine(rect.right, rect.top, rect.right - length, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + length, cornerPaint)

        // Bottom-left corner
        canvas.drawLine(rect.left, rect.bottom, rect.left + length, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - length, cornerPaint)

        // Bottom-right corner
        canvas.drawLine(rect.right, rect.bottom, rect.right - length, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - length, cornerPaint)
    }
}
