package com.example.expensetracker

/**
 * Single source of truth for category → emoji mapping.
 * Used by OverlayService, MainActivity, TransactionsActivity,
 * ExpenseBreakdownActivity, and ManageCategoriesActivity.
 */
object CategoryEmoji {

    fun get(categoryName: String): String = when (categoryName.lowercase()) {
        "food"              -> "🍽️"
        "tea/coffee"        -> "☕"
        "fuel"              -> "⛽"
        "shopping"          -> "🛍️"
        "transport"         -> "🚌"
        "grocery"           -> "🛒"
        "medicine"          -> "💊"
        "movies"            -> "🎬"
        "ott"               -> "📺"
        "snacks"            -> "🍪"
        "mutual funds"      -> "📈"
        "loan emi"          -> "💳"
        "online order"      -> "📦"
        "personal grooming" -> "✂️"
        "internet"          -> "📶"
        "electricity"       -> "⚡"
        "gas"               -> "🔥"
        "house rent"        -> "🏠"
        "insurance premium" -> "🛡️"
        else                -> "💰"
    }
}
