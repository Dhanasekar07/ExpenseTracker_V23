package com.example.expensetracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    @Suppress("DEPRECATION")
    override fun onBackPressed() { finishAffinity() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnBack     = findViewById<android.widget.ImageView>(R.id.btnBack)
        val etUsername  = findViewById<EditText>(R.id.etUsername)
        val etMin       = findViewById<EditText>(R.id.etMinAmount)
        val etMax       = findViewById<EditText>(R.id.etMaxAmount)

        // Load saved values
        etUsername.setText(AppPreferences.getUsername(this))
        val minVal = AppPreferences.getMinAmount(this).toInt()
        etMin.setText(if (minVal > 0) minVal.toString() else "")
        val maxAmt = AppPreferences.getMaxAmount(this)
        if (maxAmt > 0) etMax.setText(maxAmt.toInt().toString())

        btnBack.setOnClickListener { finish() }

        etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = etUsername.text.toString().trim()
                if (name.isNotEmpty()) AppPreferences.setUsername(this, name)
            }
        }

        etMin.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) AppPreferences.setMinAmount(this, etMin.text.toString().toFloatOrNull() ?: 0f)
        }
        etMax.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) AppPreferences.setMaxAmount(this, etMax.text.toString().toFloatOrNull() ?: -1f)
        }

        // Popup max categories slider
        val seekPopup = findViewById<SeekBar>(R.id.seekPopupMax)
        val tvPopupVal = findViewById<TextView>(R.id.tvPopupMaxValue)
        val currentMax = AppPreferences.getPopupMaxCategories(this)
        seekPopup.progress = currentMax
        tvPopupVal.text = currentMax.toString()
        seekPopup.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(2, 9)
                tvPopupVal.text = value.toString()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val value = (sb?.progress ?: 4).coerceIn(2, 9)
                AppPreferences.setPopupMaxCategories(this@SettingsActivity, value)
            }
        })

        // Popup alive time chips
        val chipsContainer = findViewById<LinearLayout>(R.id.aliveChipsContainer)
        val currentAlive = AppPreferences.getPopupAliveTime(this)
        buildAliveChips(chipsContainer, currentAlive)

        // Bottom nav - settings active
        setupBottomNav()
    }

    private fun buildAliveChips(container: LinearLayout, selectedValue: Int) {
        container.removeAllViews()
        val d = resources.displayMetrics.density

        AppPreferences.ALIVE_TIME_OPTIONS.forEachIndexed { index, value ->
            val label = AppPreferences.ALIVE_TIME_LABELS[index]
            val isSelected = (value == selectedValue)

            val chip = TextView(this).apply {
                text = label
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                setPadding((14*d).toInt(), (8*d).toInt(), (14*d).toInt(), (8*d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    marginEnd = if (index < AppPreferences.ALIVE_TIME_OPTIONS.size - 1) (6*d).toInt() else 0
                }

                if (isSelected) {
                    setTextColor(android.graphics.Color.WHITE)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 16 * d
                        setColor(android.graphics.Color.parseColor("#2D6A4F"))
                    }
                } else {
                    setTextColor(android.graphics.Color.parseColor("#6B7280"))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 16 * d
                        setColor(android.graphics.Color.parseColor("#F3F4F6"))
                    }
                }

                setOnClickListener {
                    AppPreferences.setPopupAliveTime(this@SettingsActivity, value)
                    buildAliveChips(container, value)
                }
            }
            container.addView(chip)
        }
    }

    private fun setupBottomNav() {
        // Highlight Settings
        listOf(
            Triple(R.id.navHomePill, R.id.navHomeIcon, R.id.navHomeLabel),
            Triple(R.id.navTransactionsPill, R.id.navTransactionsIcon, R.id.navTransactionsLabel),
            Triple(R.id.navCategoryPill, R.id.navCategoryIcon, R.id.navCategoryLabel),
            Triple(R.id.navSettingsPill, R.id.navSettingsIcon, R.id.navSettingsLabel)
        ).forEach { (pillId, iconId, labelId) ->
            findViewById<LinearLayout>(pillId).background = null
            findViewById<android.widget.ImageView>(iconId).alpha = 0.5f
            findViewById<TextView>(labelId).setTextColor(ThemeColors.hint(this))
        }
        findViewById<LinearLayout>(R.id.navSettingsPill).setBackgroundResource(R.drawable.bg_nav_active_pill)
        findViewById<android.widget.ImageView>(R.id.navSettingsIcon).apply {
            alpha = 1f; setColorFilter(ThemeColors.brand(this))
        }
        findViewById<TextView>(R.id.navSettingsLabel).setTextColor(ThemeColors.brand(this))

        // Nav clicks
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navTransactions).setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navCategory).setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java)); finish(); overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener { /* already here */ }
    }
}
