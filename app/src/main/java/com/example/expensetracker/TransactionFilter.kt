package com.example.expensetracker

import android.util.Log

/**
 * Shared filter logic for both SmsReceiver and PaymentNotificationListener.
 * Three-stage filter:
 *   Stage 1: Must contain a DEBIT action keyword
 *   Stage 2: Must NOT be a credit-only message
 *   Stage 3: Must NOT contain false positive keywords
 */
object TransactionFilter {

    private const val TAG = "TransactionFilter"

    // Stage 1: At least one required to trigger
    private val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "deducted", "deduct",
        "spent", "paid", "payment",
        "purchase", "purchased",
        "withdrawn", "withdrawal",
        "charged", "charge",
        "sent", "used",
        "renewed", "auto-renewed",
        "taken"
    )

    // Stage 2: If ONLY credit keywords present (no debit keyword), skip
    private val CREDIT_KEYWORDS = listOf(
        "credited", "credit",
        "received", "deposited",
        "refund", "refunded",
        "reversed", "reversal",
        "added to", "cashback received"
    )

    // Stage 3: Context words that indicate it's NOT a real completed debit
    private val FALSE_POSITIVE_KEYWORDS = listOf(
        // Refunds & reversals
        "refund", "refunded", "reversed", "reversal",
        // Failed transactions
        "failed", "failure", "unsuccessful", "declined", "rejected",
        "could not", "unable to",
        // Future tense / reminders
        "will be deducted", "will be debited", "will be charged",
        "upcoming", "due on", "is due", "payment due",
        "reminder", "scheduled", "auto-pay is set",
        "is pending", "pending approval",
        // Requests (not actual payments)
        "requested", "request for", "has requested",
        // Promotional
        "offer", "reward", "earn", "get up to",
        "apply", "eligible", "promo", "discount",
        "cashback when", "cashback on",
        // Conditional
        "if debited", "if charged", "not yet",
        // Self transfers
        "own account", "self transfer", "your savings a/c",
        "between your", "to your own"
    )

    /**
     * Returns true if the message text represents a legitimate completed debit.
     * Returns false if it should be skipped (credit, promo, failed, future, etc.)
     */
    fun isLegitimateDebit(text: String): Boolean {
        val lower = text.lowercase()

        // Stage 1: Must contain a debit action keyword
        val hasDebitKeyword = DEBIT_KEYWORDS.any { lower.contains(it) }
        if (!hasDebitKeyword) {
            Log.d(TAG, "No debit keyword found — skipping")
            return false
        }

        // Stage 2: If credit keywords present but no strong debit keyword, skip
        val hasCreditKeyword = CREDIT_KEYWORDS.any { lower.contains(it) }
        val hasStrongDebit = listOf("debited", "debit", "spent", "purchase", "purchased", "withdrawn", "charged", "deducted")
            .any { lower.contains(it) }
        if (hasCreditKeyword && !hasStrongDebit) {
            Log.d(TAG, "Credit message without strong debit — skipping")
            return false
        }

        // Stage 3: Check for false positive context
        val falsePositive = FALSE_POSITIVE_KEYWORDS.firstOrNull { lower.contains(it) }
        if (falsePositive != null) {
            Log.d(TAG, "False positive keyword '$falsePositive' — skipping")
            return false
        }

        return true
    }
}
