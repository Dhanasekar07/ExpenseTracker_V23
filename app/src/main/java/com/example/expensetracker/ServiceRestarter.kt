package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Starts OverlayService (which hosts SmsObserver) on boot,
 * app update, and quick-boot so the ContentObserver is always watching.
 */
class ServiceRestarter : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ExpenseTracker", "ServiceRestarter fired: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d("ExpenseTracker", "Starting OverlayService after: ${intent.action}")
                val svc = Intent(context, OverlayService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(svc)
                    else
                        context.startService(svc)
                } catch (e: Exception) {
                    Log.e("ExpenseTracker", "Failed to start service on boot", e)
                }
            }
        }
    }
}
