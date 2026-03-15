package com.camera2rtsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** View transparente que desenha a grade de enquadramento 3×3. */
class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.argb(60, 255, 255, 255)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        // linhas verticais
        canvas.drawLine(w / 3f, 0f, w / 3f, h, paint)
        canvas.drawLine(2 * w / 3f, 0f, 2 * w / 3f, h, paint)
        // linhas horizontais
        canvas.drawLine(0f, h / 3f, w, h / 3f, paint)
        canvas.drawLine(0f, 2 * h / 3f, w, 2 * h / 3f, paint)
    }
}
