package com.example.expensetracker

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {

    private const val PREF_NAME    = "expense_tracker_prefs"
    private const val KEY_USERNAME = "pref_username"
    private const val KEY_COUNTRY  = "pref_country"
    private const val KEY_CURRENCY = "pref_currency_symbol"
    private const val KEY_NIGHT    = "pref_night_mode"
    private const val KEY_MIN      = "pref_min_amount"
    private const val KEY_MAX      = "pref_max_amount"
    private const val KEY_ONBOARD  = "pref_onboarded"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Onboarding ──────────────────────────────────────────────────────────
    fun isOnboarded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARD, false)

    fun setOnboarded(ctx: Context) =
        prefs(ctx).edit().putBoolean(KEY_ONBOARD, true).apply()

    // ── Username ─────────────────────────────────────────────────────────────
    fun getUsername(ctx: Context): String =
        prefs(ctx).getString(KEY_USERNAME, "") ?: ""

    fun setUsername(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_USERNAME, v).apply()

    // ── Country & Currency ───────────────────────────────────────────────────
    fun getCountry(ctx: Context): String =
        prefs(ctx).getString(KEY_COUNTRY, "India") ?: "India"

    fun setCountry(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_COUNTRY, v).apply()

    fun getCurrencySymbol(ctx: Context): String =
        prefs(ctx).getString(KEY_CURRENCY, "₹") ?: "₹"

    fun setCurrencySymbol(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_CURRENCY, v).apply()

    // ── Night Mode ───────────────────────────────────────────────────────────
    fun isNightMode(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_NIGHT, false)

    fun setNightMode(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_NIGHT, v).apply()

    // ── Thresholds ───────────────────────────────────────────────────────────
    fun getMinAmount(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_MIN, 0f)

    fun setMinAmount(ctx: Context, v: Float) =
        prefs(ctx).edit().putFloat(KEY_MIN, v).apply()

    fun getMaxAmount(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_MAX, -1f)

    fun setMaxAmount(ctx: Context, v: Float) =
        prefs(ctx).edit().putFloat(KEY_MAX, v).apply()

    // Max categories shown in popup overlay (2-10, default 4)
    private const val KEY_POPUP_MAX = "popup_max_categories"

    fun getPopupMaxCategories(ctx: Context): Int =
        prefs(ctx).getInt(KEY_POPUP_MAX, 9)

    fun setPopupMaxCategories(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_POPUP_MAX, v.coerceIn(2, 9)).apply()

    // Popup alive time in seconds (15, 30, 60, 120, -1=Never)
    private const val KEY_POPUP_ALIVE = "popup_alive_time"
    val ALIVE_TIME_OPTIONS = intArrayOf(15, 30, 60, 120, -1)
    val ALIVE_TIME_LABELS = arrayOf("15s", "30s", "60s", "2min", "Never")

    fun getPopupAliveTime(ctx: Context): Int =
        prefs(ctx).getInt(KEY_POPUP_ALIVE, -1)

    fun setPopupAliveTime(ctx: Context, seconds: Int) =
        prefs(ctx).edit().putInt(KEY_POPUP_ALIVE, seconds).apply()

    // Category sort mode: "custom" or "frequency"
    private const val KEY_SORT_MODE = "category_sort_mode"

    fun getCategorySortMode(ctx: Context): String =
        prefs(ctx).getString(KEY_SORT_MODE, "custom") ?: "custom"

    fun setCategorySortMode(ctx: Context, mode: String) =
        prefs(ctx).edit().putString(KEY_SORT_MODE, mode).apply()
}
