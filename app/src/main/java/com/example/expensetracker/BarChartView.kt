package com.example.expensetracker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

data class BarEntry(val label: String, val value: Float, val color: Int)

class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val barPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; textAlign = Paint.Align.CENTER }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val gridPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }

    private var entries  = listOf<BarEntry>()
    private var currency = "₹"

    fun setData(data: List<BarEntry>, currency: String = "₹") {
        this.entries = data; this.currency = currency; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        textPaint.color  = ContextCompat.getColor(context, R.color.text_hint)
        valuePaint.color = ContextCompat.getColor(context, R.color.text_primary)
        gridPaint.color  = ContextCompat.getColor(context, R.color.divider)

        val w = width.toFloat(); val h = height.toFloat()
        val topPad = 40f; val botPad = 44f; val sidePad = 16f
        val graphH = h - topPad - botPad
        val maxVal = entries.maxOf { it.value }.coerceAtLeast(1f)

        for (i in 0..3) {
            val y = topPad + graphH * (1f - i / 3f)
            canvas.drawLine(sidePad, y, w - sidePad, y, gridPaint)
        }

        val barW = (w - sidePad * 2f) / entries.size; val barGap = barW * 0.3f
        entries.forEachIndexed { i, entry ->
            val barH = (entry.value / maxVal) * graphH
            val left = sidePad + i * barW + barGap / 2f
            val right = left + barW - barGap
            val top = topPad + graphH - barH; val bottom = topPad + graphH

            barPaint.color = entry.color
            canvas.drawRoundRect(RectF(left, top, right, bottom), 8f, 8f, barPaint)

            if (entry.value > 0) {
                val label = if (entry.value >= 1000) "$currency${(entry.value / 1000).toInt()}k"
                else "$currency${entry.value.toInt()}"
                canvas.drawText(label, left + (barW - barGap) / 2f, top - 6f, valuePaint)
            }
            canvas.drawText(entry.label.take(4), left + (barW - barGap) / 2f, h - 8f, textPaint)
        }
    }
}
