package com.example.expensetracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppPreferencesTest {

    private lateinit var ctx: Context

    @Before fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        ctx.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── Onboarding ───────────────────────────────────────────────────────────
    @Test fun `isOnboarded defaults to false`() {
        assertFalse(AppPreferences.isOnboarded(ctx))
    }

    @Test fun `setOnboarded marks as true`() {
        AppPreferences.setOnboarded(ctx)
        assertTrue(AppPreferences.isOnboarded(ctx))
    }

    // ── Username ─────────────────────────────────────────────────────────────
    @Test fun `getUsername defaults to empty`() {
        assertEquals("", AppPreferences.getUsername(ctx))
    }

    @Test fun `setUsername persists`() {
        AppPreferences.setUsername(ctx, "David")
        assertEquals("David", AppPreferences.getUsername(ctx))
    }

    // ── Country & Currency ───────────────────────────────────────────────────
    @Test fun `getCountry defaults to India`() {
        assertEquals("India", AppPreferences.getCountry(ctx))
    }

    @Test fun `getCurrencySymbol defaults to rupee`() {
        assertEquals("₹", AppPreferences.getCurrencySymbol(ctx))
    }

    @Test fun `setCurrencySymbol persists`() {
        AppPreferences.setCurrencySymbol(ctx, "$")
        assertEquals("$", AppPreferences.getCurrencySymbol(ctx))
    }

    // ── Night Mode ───────────────────────────────────────────────────────────
    @Test fun `isNightMode defaults to false`() {
        assertFalse(AppPreferences.isNightMode(ctx))
    }

    @Test fun `setNightMode toggle works`() {
        AppPreferences.setNightMode(ctx, true)
        assertTrue(AppPreferences.isNightMode(ctx))
        AppPreferences.setNightMode(ctx, false)
        assertFalse(AppPreferences.isNightMode(ctx))
    }

    // ── Thresholds ───────────────────────────────────────────────────────────
    @Test fun `getMinAmount defaults to zero`() {
        assertEquals(0f, AppPreferences.getMinAmount(ctx), 0.01f)
    }

    @Test fun `getMaxAmount defaults to negative one`() {
        assertEquals(-1f, AppPreferences.getMaxAmount(ctx), 0.01f)
    }

    @Test fun `setMinAmount persists`() {
        AppPreferences.setMinAmount(ctx, 50f)
        assertEquals(50f, AppPreferences.getMinAmount(ctx), 0.01f)
    }

    @Test fun `setMaxAmount persists`() {
        AppPreferences.setMaxAmount(ctx, 10000f)
        assertEquals(10000f, AppPreferences.getMaxAmount(ctx), 0.01f)
    }
}
