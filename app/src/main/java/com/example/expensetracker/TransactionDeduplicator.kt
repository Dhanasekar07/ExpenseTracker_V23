package com.example.expensetracker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest

class TransactionDeduplicator(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("expense_dedup", Context.MODE_PRIVATE)

    companion object {
        private const val TAG        = "TransactionDedup"
        private const val WINDOW_MS  = 30_000L
        private const val PREFIX     = "h_"
        private const val PREFIX_AMT = "a_"   // cross-channel amount-only
    }

    /**
     * Channel-specific hash: amount + source + normalized text + time bucket.
     */
    fun makeHash(amount: Double, source: String, text: String): String {
        val bucket     = System.currentTimeMillis() / 15_000L
        val normalized = text.take(60).lowercase().replace("\\s+".toRegex(), " ").trim()
        val raw        = "$amount|$source|$normalized|$bucket"
        val bytes      = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Cross-channel hash: amount + 30-second time bucket ONLY.
     * Catches the same ₹X payment arriving via both SMS and notification.
     */
    private fun makeAmountHash(amount: Double): String {
        val bucket = System.currentTimeMillis() / 30_000L
        val raw    = "$amount|$bucket"
        val bytes  = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Returns true if this transaction should be SKIPPED (already handled). */
    fun isDuplicate(amount: Double, source: String, text: String): Boolean {
        // Check 1: exact channel-specific match
        val hash = makeHash(amount, source, text)
        if (isSeen(hash)) {
            Log.d(TAG, "Channel duplicate: amount=$amount source=$source")
            return true
        }

        // Check 2: cross-channel amount match (same ₹X within 30s from any channel)
        val amtHash = makeAmountHash(amount)
        if (isSeen(PREFIX_AMT + amtHash)) {
            Log.d(TAG, "Cross-channel duplicate: amount=$amount source=$source")
            return true
        }

        // Mark both hashes
        markSeen(hash)
        markSeen(PREFIX_AMT + amtHash)
        return false
    }

    fun isSeen(hash: String): Boolean {
        return try {
            val t = prefs.getLong(PREFIX + hash, -1L)
            t != -1L && (System.currentTimeMillis() - t) < WINDOW_MS
        } catch (e: Exception) {
            Log.e(TAG, "isSeen failed", e)
            false
        }
    }

    fun markSeen(hash: String) {
        try {
            prefs.edit().putLong(PREFIX + hash, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "markSeen failed", e)
        }
    }

    fun cleanup() {
        try {
            val now      = System.currentTimeMillis()
            val editor   = prefs.edit()
            val snapshot = HashMap(prefs.all)
            var removed  = 0
            for ((key, value) in snapshot) {
                if (key.startsWith(PREFIX) && value is Long) {
                    if (now - value > 60_000L) {
                        editor.remove(key)
                        removed++
                    }
                }
            }
            editor.apply()
            if (removed > 0) Log.d(TAG, "Cleaned $removed stale entries")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}
