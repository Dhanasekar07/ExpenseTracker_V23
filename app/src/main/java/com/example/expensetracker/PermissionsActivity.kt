package com.example.expensetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import android.widget.TextView


class PermissionsActivity : AppCompatActivity() {

    companion object {
        private const val SMS_REQUEST_CODE = 1001
    }

    // Which permission we're waiting for after returning from Settings
    private var waitingFor = ""

    private lateinit var checkSms: ImageView
    private lateinit var checkOverlay: ImageView
    private lateinit var checkBattery: ImageView
    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        checkSms     = findViewById(R.id.checkSms)
        checkOverlay = findViewById(R.id.checkOverlay)
        checkBattery = findViewById(R.id.checkBattery)
        btnContinue  = findViewById(R.id.btnContinue)

        // Set click listeners on each row for manual re-grant
        findViewById<LinearLayout>(R.id.permSms).setOnClickListener { requestSms() }
        findViewById<LinearLayout>(R.id.permOverlay).setOnClickListener { showOverlayDialog() }
        findViewById<LinearLayout>(R.id.permBattery).setOnClickListener { requestBattery() }

        btnContinue.setOnClickListener { startSequentialGrant() }

        refreshChecks()
    }

    override fun onResume() {
        super.onResume()

        // Handle return from Settings pages
        when (waitingFor) {
            "sms_settings" -> {
                waitingFor = ""
                refreshChecks()
                if (isSmsGranted()) proceedAfterSms()
                else showSmsSettingsDialog()
            }
            "overlay" -> {
                waitingFor = ""
                refreshChecks()
                if (isOverlayGranted()) proceedAfterOverlay()
            }
            "battery" -> {
                waitingFor = ""
                refreshChecks()
                if (isBatteryExempt()) proceedAfterBattery()
            }
            else -> refreshChecks()
        }
    }

    // ── Sequential Grant Flow ───────────────────────────────────────────

    private fun startSequentialGrant() {
        when {
            !isSmsGranted()     -> requestSms()
            !isOverlayGranted() -> showOverlayDialog()
            !isBatteryExempt()  -> requestBattery()
            else                -> goToMain()
        }
    }

    // ── SMS (runtime dialog — seamless) ─────────────────────────────────

    private var smsDeniedOnce = false

    private fun requestSms() {
        if (isSmsGranted()) {
            refreshChecks()
            proceedAfterSms()
            return
        }
        // Try system popup first
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
            SMS_REQUEST_CODE
        )
        
        // Sideloaded apps can't get SMS popup — go to Settings - OLD CODE
        // waitingFor = "sms_settings"
        // AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        //     .setTitle("SMS Permission")
        //     .setMessage("Allow Expense Tracker to read SMS messages to detect payment transactions.")
        //     .setCancelable(false)
        //     .setPositiveButton("Go to Settings") { _, _ ->
        //         startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        //             data = Uri.parse("package:$packageName")
        //         })
        //     }
        //     .show()

        // // If already denied once or permanently denied, go to app settings
        // if (!ActivityCompat.shouldShowRequestPermissionRationale(
        //         this, Manifest.permission.READ_SMS)) {
        //     waitingFor = "sms_settings"
        //     startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        //         data = Uri.parse("package:$packageName")
        //     })
        //     return
        // }

        // ActivityCompat.requestPermissions(
        //     this,
        //     arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
        //     SMS_REQUEST_CODE
        // )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_REQUEST_CODE) {
            refreshChecks()
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                proceedAfterSms()
            } else {
                showSmsSettingsDialog()
            }
        }
    }

    private fun showSmsSettingsDialog() {
        val view: android.view.View = layoutInflater.inflate(R.layout.dialog_permission, null)
        (view.findViewById(R.id.dialogTitle) as TextView).text = "SMS Permission"
        (view.findViewById(R.id.dialogMessage) as TextView).text =
            "Allow Expense Tracker to read SMS messages to detect payment transactions"
    
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setView(view as android.view.View)
            .setCancelable(false)
            .create()
    
        (view.findViewById(R.id.dialogButton) as Button).setOnClickListener {
            waitingFor = "sms_settings"
            dialog.dismiss()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun proceedAfterSms() {
        if (!isOverlayGranted()) showOverlayDialog()
        else if (!isBatteryExempt()) requestBattery()
        else goToMain()
    }

    // ── Notification Listener (opens Settings) ──────────────────────────

    // private fun requestNotifListener() {
    //     if (isNotifGranted()) {
    //         refreshChecks()
    //         proceedAfterNotif()
    //         return
    //     }
    //     waitingFor = "notif"
    //     startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    // }

    // private fun proceedAfterNotif() {
    //     if (!isOverlayGranted()) showOverlayDialog()
    //     else if (!isBatteryExempt()) requestBattery()
    //     else goToMain()
    // }

    // ── Overlay (opens Settings) ────────────────────────────────────────

    private fun showOverlayDialog() {
        if (isOverlayGranted()) {
            refreshChecks()
            proceedAfterOverlay()
            return
        }

        val view: android.view.View = layoutInflater.inflate(R.layout.dialog_permission, null)
        (view.findViewById(R.id.dialogTitle) as TextView).text = "Display Over Other Apps"
        (view.findViewById(R.id.dialogMessage) as TextView).text =
            "Allow Expense Tracker to display over other apps to show the category popup after payments"
    
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setView(view as android.view.View)
            .setCancelable(false)
            .create()
    
        (view.findViewById(R.id.dialogButton) as Button).setOnClickListener {
            waitingFor = "overlay"
            dialog.dismiss()
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
    
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
    
    private fun proceedAfterOverlay() {
        if (!isBatteryExempt()) requestBattery()
        else goToMain()
    }

    // ── Battery Optimization (system dialog) ────────────────────────────

    private fun requestBattery() {
        if (isBatteryExempt()) {
            refreshChecks()
            proceedAfterBattery()
            return
        }

        val view: android.view.View = layoutInflater.inflate(R.layout.dialog_permission, null)
        (view.findViewById(R.id.dialogTitle) as TextView).text = "Battery Optimization"
        (view.findViewById(R.id.dialogMessage) as TextView).text =
            "Allow Expense Tracker to run in the background to keep tracking payments"
        (view.findViewById(R.id.dialogButton) as Button).text = "Allow"
    
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setView(view as android.view.View)
            .setCancelable(false)
            .create()
    
        (view.findViewById(R.id.dialogButton) as Button).setOnClickListener {
            waitingFor = "battery"
            dialog.dismiss()
            try {
                startActivity(Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                ))
            } catch (e: Exception) {
                waitingFor = ""
                proceedAfterBattery()
            }
        }
    
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun proceedAfterBattery() {
        refreshChecks()
        if (allGranted()) goToMain()
    }

    // ── Permission Checks ───────────────────────────────────────────────

    private fun isSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    // private fun isNotifGranted(): Boolean {
    //     val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
    //     return flat?.contains(packageName) == true
    // }

    private fun isOverlayGranted(): Boolean = Settings.canDrawOverlays(this)

    private fun isBatteryExempt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun allGranted(): Boolean =
        isSmsGranted() && isOverlayGranted() && isBatteryExempt()

    // ── UI Updates ──────────────────────────────────────────────────────

    private fun refreshChecks() {
        setCheck(checkSms, isSmsGranted())
        setCheck(checkOverlay, isOverlayGranted())
        setCheck(checkBattery, isBatteryExempt())

        if (allGranted()) {
            btnContinue.text = "Continue"
        }
    }

    private fun setCheck(icon: ImageView, granted: Boolean) {
        icon.setImageResource(
            if (granted) R.drawable.ic_check_green
            else R.drawable.ic_check_grey
        )
    }

    // ── Navigation ──────────────────────────────────────────────────────

    private fun goToMain() {
        AppPreferences.setOnboarded(this)

        try { CategoryManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }

        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
