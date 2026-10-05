package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log

/**
 * ContentObserver on content://sms/inbox.
 * Fires the instant a new SMS lands in the inbox — bypasses OEM broadcast
 * restrictions on OnePlus/ColorOS/OxygenOS devices.
 */
class SmsObserver(
    private val context: Context,
    handler: Handler
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "ExpenseTracker"
        private val SMS_INBOX_URI = Uri.parse("content://sms/inbox")
        // Register on the parent URI — many OEMs only fire onChange here
        val OBSERVE_URI: Uri = Uri.parse("content://sms")

        // Track the last SMS _id we processed to avoid re-reads
        @Volatile
        private var lastProcessedId: Long = -1L
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        Log.d(TAG, "SmsObserver: inbox changed")

        try {
            readLatestSms()
        } catch (e: Exception) {
            Log.e(TAG, "SmsObserver: error reading SMS", e)
        }
    }

    private fun readLatestSms() {
        val cutoff = System.currentTimeMillis() - 30_000  // last 30 seconds only
        val cursor = context.contentResolver.query(
            SMS_INBOX_URI,
            arrayOf("_id", "address", "body", "date"),
            "date > ?",
            arrayOf(cutoff.toString()),
            "date DESC LIMIT 3"
        ) ?: return

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("_id"))
                val address = it.getString(it.getColumnIndexOrThrow("address")) ?: continue
                val body = it.getString(it.getColumnIndexOrThrow("body")) ?: continue

                // Skip already-processed messages
                if (id <= lastProcessedId) continue

                Log.d(TAG, "SmsObserver: new SMS id=$id from [$address]: ${body.take(80)}")

                // Update last processed id
                lastProcessedId = id

                processSms(address, body)
                return  // Process one new SMS per onChange
            }
        }
    }

    private fun processSms(sender: String, body: String) {
        // Stage 1-3: Shared debit filter
        if (!TransactionFilter.isLegitimateDebit(body)) {
            Log.d(TAG, "SmsObserver: failed debit filter")
            return
        }

        // Parse amount
        val amount = AmountParser.parse(body)
        if (amount <= 0.0) {
            Log.d(TAG, "SmsObserver: amount unparseable — skipping")
            return
        }

        // Threshold check
        val minAmount = AppPreferences.getMinAmount(context)
        val maxAmount = AppPreferences.getMaxAmount(context)

        if (amount < minAmount) {
            Log.d(TAG, "SmsObserver: ₹$amount below min ₹$minAmount — skipped")
            return
        }
        if (maxAmount > 0 && amount > maxAmount) {
            Log.d(TAG, "SmsObserver: ₹$amount above max ₹$maxAmount — skipped")
            return
        }

        // Dedup — catches same SMS arriving via both observer and notification
        val dedup = TransactionDeduplicator(context)
        if (dedup.isDuplicate(amount, sender, body)) {
            Log.d(TAG, "SmsObserver: duplicate — skipping")
            return
        }

        Log.d(TAG, "SmsObserver: payment confirmed — ₹$amount from $sender")

        // Fire overlay
        val intent = Intent(context, OverlayService::class.java).apply {
            putExtra("amount", amount)
            putExtra("source", sender)
            putExtra("snippet", body.take(80))
            putExtra("channel", "sms_observer")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent)
        else
            context.startService(intent)
    }

    /**
     * Seed the last processed ID so we don't re-fire on existing SMS when
     * the observer first registers.
     */
    fun seedLastId() {
        try {
            val cursor = context.contentResolver.query(
                SMS_INBOX_URI,
                arrayOf("_id"),
                null, null,
                "date DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    lastProcessedId = it.getLong(0)
                    Log.d(TAG, "SmsObserver: seeded lastId=$lastProcessedId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SmsObserver: seed failed", e)
        }
    }
}
