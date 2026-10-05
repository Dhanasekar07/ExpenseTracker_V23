package com.example.expensetracker

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * Central utility for category icons. Replaces CategoryEmoji.
 * Predefined categories get Material vector icons.
 * Custom categories get a bold first-letter fallback.
 */
object CategoryIcon {

    private val ICON_MAP = mapOf(
        "food"              to R.drawable.ic_cat_restaurant,
        "tea/coffee"        to R.drawable.ic_cat_coffee,
        "shopping"          to R.drawable.ic_cat_shopping_bag,
        "grocery"           to R.drawable.ic_cat_shopping_cart,
        "entertainment"     to R.drawable.ic_cat_entertainment,
        "movies"            to R.drawable.ic_cat_movies,
        "ott"               to R.drawable.ic_cat_subscriptions,
        "subscriptions"     to R.drawable.ic_cat_subscriptions,
        "fuel"              to R.drawable.ic_cat_fuel,
        "transport"         to R.drawable.ic_cat_transport,
        "taxi/ride"         to R.drawable.ic_cat_taxi,
        "medicine"          to R.drawable.ic_cat_medicine,
        "snacks"            to R.drawable.ic_cat_snacks,
        "mutual funds"      to R.drawable.ic_cat_investment,
        "investment"        to R.drawable.ic_cat_investment,
        "loan emi"          to R.drawable.ic_cat_credit_card,
        "online order"      to R.drawable.ic_cat_package,
        "personal grooming" to R.drawable.ic_cat_grooming,
        "salon/spa"         to R.drawable.ic_cat_grooming,
        "internet"          to R.drawable.ic_cat_internet,
        "electricity"       to R.drawable.ic_cat_electricity,
        "gas"               to R.drawable.ic_cat_gas,
        "house rent"        to R.drawable.ic_cat_home,
        "insurance premium" to R.drawable.ic_cat_insurance,
        "insurance"         to R.drawable.ic_cat_insurance,
        "education"         to R.drawable.ic_cat_education,
        "fitness"           to R.drawable.ic_cat_fitness,
        "savings"           to R.drawable.ic_cat_savings,
        "laundry"           to R.drawable.ic_cat_laundry,
        "water bill"        to R.drawable.ic_cat_water,
        "mobile recharge"   to R.drawable.ic_cat_phone,
        "travel"            to R.drawable.ic_cat_travel,
        "gifts"             to R.drawable.ic_cat_gifts,
        "donation"          to R.drawable.ic_cat_donation,
        "pet care"          to R.drawable.ic_cat_donation,
        "recharge"          to R.drawable.ic_cat_internet,
        "health"            to R.drawable.ic_cat_medicine,
        "kids/school"       to R.drawable.ic_cat_education,
        "maintenance"       to R.drawable.ic_cat_home,
        "parking"           to R.drawable.ic_cat_taxi,
        "monthly bills"     to R.drawable.ic_cat_receipt,
        "miscellaneous"     to R.drawable.ic_cat_receipt
    )

    /** Get drawable resource ID for a category, or null for custom categories */
    fun getDrawableId(categoryName: String): Int? {
        return ICON_MAP[categoryName.lowercase()]
    }

    /**
     * Populate a FrameLayout with the category icon (vector or letter fallback).
     * Sets the background colour and adds the icon view inside.
     */
    fun applyIcon(
        container: FrameLayout,
        categoryName: String,
        bgColor: String,
        iconTint: String,
        iconSizeDp: Int = 20
    ) {
        val ctx = container.context
        container.removeAllViews()

        // Set background
        container.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(ctx, 12f)
            setColor(parseColorSafe(bgColor))
        }

        val drawableId = getDrawableId(categoryName)
        val tintColor = parseColorSafe(iconTint)

        if (drawableId != null) {
            // Predefined: vector icon
            val iv = ImageView(ctx).apply {
                setImageResource(drawableId)
                setColorFilter(tintColor)
                val size = dpToPx(ctx, iconSizeDp.toFloat()).toInt()
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
            }
            container.addView(iv)
        } else {
            // Custom: bold first letter
            val tv = TextView(ctx).apply {
                text = categoryName.firstOrNull()?.uppercase() ?: "?"
                setTextColor(tintColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (iconSizeDp * 0.8f))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            container.addView(tv)
        }
    }

    /** For overlay chips — returns a small ImageView or TextView */
    fun createChipIcon(ctx: Context, categoryName: String, tintColor: Int, sizeDp: Int = 18): View {
        val drawableId = getDrawableId(categoryName)
        val size = dpToPx(ctx, sizeDp.toFloat()).toInt()

        return if (drawableId != null) {
            ImageView(ctx).apply {
                setImageResource(drawableId)
                setColorFilter(tintColor)
                layoutParams = FrameLayout.LayoutParams(size, size)
            }
        } else {
            TextView(ctx).apply {
                text = categoryName.firstOrNull()?.uppercase() ?: "?"
                setTextColor(tintColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = FrameLayout.LayoutParams(size, size)
                gravity = Gravity.CENTER
            }
        }
    }

    private fun dpToPx(ctx: Context, dp: Float): Float {
        return dp * ctx.resources.displayMetrics.density
    }

    private fun parseColorSafe(hex: String): Int {
        return try { Color.parseColor(hex) } catch (e: Exception) { Color.GRAY }
    }
}
