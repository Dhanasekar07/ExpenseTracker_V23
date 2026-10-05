package com.example.expensetracker

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat

object ThemeColors {
    private fun ctx(src: Any): Context = when (src) {
        is Context -> src
        is View -> src.context
        else -> throw IllegalArgumentException("Expected Context or View")
    }

    fun primary(src: Any)        = ContextCompat.getColor(ctx(src), R.color.text_primary)
    fun secondary(src: Any)      = ContextCompat.getColor(ctx(src), R.color.text_secondary)
    fun hint(src: Any)           = ContextCompat.getColor(ctx(src), R.color.text_hint)
    fun bgPage(src: Any)         = ContextCompat.getColor(ctx(src), R.color.bg_page)
    fun bgCard(src: Any)         = ContextCompat.getColor(ctx(src), R.color.bg_card)
    fun border(src: Any)         = ContextCompat.getColor(ctx(src), R.color.border)
    fun divider(src: Any)        = ContextCompat.getColor(ctx(src), R.color.divider)
    fun brand(src: Any)          = ContextCompat.getColor(ctx(src), R.color.brand_primary)
    fun brandLight(src: Any)     = ContextCompat.getColor(ctx(src), R.color.brand_light)
    fun navInactive(src: Any)    = ContextCompat.getColor(ctx(src), R.color.nav_inactive)
    fun navActive(src: Any)      = ContextCompat.getColor(ctx(src), R.color.nav_active)
    fun amountNeg(src: Any)      = ContextCompat.getColor(ctx(src), R.color.amount_negative)
    fun rowNormal(src: Any)      = ContextCompat.getColor(ctx(src), R.color.row_normal)
    fun rowHighlight(src: Any)   = ContextCompat.getColor(ctx(src), R.color.row_highlight)
    fun filterInactive(src: Any) = ContextCompat.getColor(ctx(src), R.color.filter_inactive_text)
}
