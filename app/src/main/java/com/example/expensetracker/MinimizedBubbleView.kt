package com.example.expensetracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Floating bubble shown while the payment popup is minimized.
 * Drawn like the launcher icon (dark disc + green ring) with the amount
 * (or the pending count) in the centre instead of the rupee glyph.
 * All geometry is defined on a 72-unit grid and scaled to the view size.
 */
class MinimizedBubbleView(context: Context) : View(context) {

    private var label = ""
    private var labelUnits = 11f   // text size on the 72-unit grid

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A"); style = Paint.Style.FILL
    }
    private val ringBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8EE90"); style = Paint.Style.STROKE
    }
    private val ringForest = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2D6A4F"); style = Paint.Style.STROKE
    }
    private val ringBright = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4ADE80"); style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8EE90")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setLabel(text: String, sizeUnits: Float) {
        label = text
        labelUnits = sizeUnits
        contentDescription = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = w / 72f
        val c = w / 2f

        canvas.drawCircle(c, h / 2f, w / 2f, discPaint)

        val r = 28.5f * s
        val stroke = 6.2f * s
        ringBase.strokeWidth = stroke
        ringForest.strokeWidth = stroke
        ringBright.strokeWidth = stroke
        rect.set(c - r, h / 2f - r, c + r, h / 2f + r)
        canvas.drawCircle(c, h / 2f, r, ringBase)
        canvas.drawArc(rect, -90f, 70f, false, ringForest)
        canvas.drawArc(rect, -20f, 84.5f, false, ringBright)

        textPaint.textSize = labelUnits * s
        val fm = textPaint.fontMetrics
        canvas.drawText(label, c, h / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
    }
}
