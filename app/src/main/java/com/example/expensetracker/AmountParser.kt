package com.example.expensetracker

/**
 * Shared utility for extracting monetary amounts from SMS/notification text.
 * Used by SmsObserver for SMS payment detection.
 */
object AmountParser {

    private val PATTERNS = listOf(
        Regex("""[+]?(?:₹\s*|Rs\.?\s*|INR\s+)([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*/-""")
    )

    /**
     * Extract the first monetary amount found in the text.
     * Supports ₹, Rs, Rs., INR prefixes and trailing /- format.
     * Returns 0.0 if no amount found.
     */
    fun parse(text: String): Double {
        if (text.isBlank()) return 0.0
        for (p in PATTERNS) {
            val m = p.find(text) ?: continue
            val amt = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (amt > 0.0) return amt
        }
        return 0.0
    }
}
