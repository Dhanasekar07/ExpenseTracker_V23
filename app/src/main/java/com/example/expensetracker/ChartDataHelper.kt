package com.example.expensetracker

import android.graphics.Color

/**
 * Shared helper for building chart data with the 6+1 rule:
 * - 6 or fewer categories: show all individually
 * - 7+ categories: top 6 by amount + "Others" in grey
 */
object ChartDataHelper {

    const val OTHERS_COLOR_CHART = "#9CA3AF"  // grey mid-tone
    const val OTHERS_COLOR_BG    = "#E5E7EB"  // light grey
    const val OTHERS_COLOR_TINT  = "#6B7280"  // dark grey

    data class ChartEntry(
        val name: String,
        val amount: Double,
        val chartColor: Int,
        val isOthers: Boolean = false
    )

    fun buildChartData(
        catTotals: Map<String, Double>,
        getCat: (String) -> Category?
    ): List<ChartEntry> {
        val sorted = catTotals.entries.sortedByDescending { it.value }

        if (sorted.size <= 6) {
            return sorted.map { (name, amt) ->
                val cat = getCat(name)
                ChartEntry(name, amt, parseChart(cat))
            }
        }

        val top6 = sorted.take(6).map { (name, amt) ->
            val cat = getCat(name)
            ChartEntry(name, amt, parseChart(cat))
        }

        val othersAmount = sorted.drop(6).sumOf { it.value }
        val others = ChartEntry("Others", othersAmount,
            Color.parseColor(OTHERS_COLOR_CHART), isOthers = true)

        return top6 + others
    }

    /** Get the remaining categories beyond top 6, sorted descending by amount */
    fun getOthersBreakdown(
        catTotals: Map<String, Double>
    ): List<Pair<String, Double>> {
        return catTotals.entries
            .sortedByDescending { it.value }
            .drop(6)
            .map { it.key to it.value }
    }

    private fun parseChart(cat: Category?): Int {
        val hex = cat?.chartColor?.ifEmpty { cat.colorHex } ?: "#9CA3AF"
        return try { Color.parseColor(hex) } catch (e: Exception) { Color.GRAY }
    }
}
