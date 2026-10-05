package com.example.expensetracker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

data class PieSlice(val label: String, val value: Float, val color: Int)

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 28f
        textAlign = Paint.Align.CENTER
    }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize       = 36f
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var slices   = listOf<PieSlice>()
    private var currency = "₹"

    fun setData(data: List<PieSlice>, currency: String = "₹") {
        this.slices   = data
        this.currency = currency
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val size = minOf(w, h); val cx = w / 2f; val cy = h / 2f
        val outer = size / 2f - 8f; val inner = outer * 0.55f

        totalPaint.color = ContextCompat.getColor(context, R.color.text_primary)
        textPaint.color  = ContextCompat.getColor(context, R.color.text_hint)
        val holeColor    = ContextCompat.getColor(context, R.color.chart_hole)

        if (slices.isEmpty()) {
            paint.color = ContextCompat.getColor(context, R.color.chart_empty)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, outer, paint)
            paint.color = holeColor
            canvas.drawCircle(cx, cy, inner, paint)
            canvas.drawText("No data", cx, cy + 12f, textPaint)
            return
        }

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        var startAngle = -90f
        val rect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)

        slices.forEach { slice ->
            val sweep = if (total > 0) (slice.value / total) * 360f else 0f
            paint.color = slice.color; paint.style = Paint.Style.FILL
            canvas.drawArc(rect, startAngle, sweep, true, paint)
            startAngle += sweep
        }

        paint.color = holeColor; paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, inner, paint)
        canvas.drawText("$currency${String.format("%.0f", total)}", cx, cy + 12f, totalPaint)
    }
}
