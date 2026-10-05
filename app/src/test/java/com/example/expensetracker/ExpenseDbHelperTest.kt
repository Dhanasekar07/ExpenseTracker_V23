package com.example.expensetracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ExpenseDbHelperTest {

    private lateinit var db: ExpenseDbHelper

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("expenses.db")
        db = ExpenseDbHelper(ctx)
    }

    @After fun teardown() {
        db.close()
    }

    // ── Insert ───────────────────────────────────────────────────────────────
    @Test fun `insertExpense returns positive row ID`() {
        val id = db.insertExpense("Food", 250.0, "GPay", "notification")
        assertTrue("Insert should return positive ID, got $id", id > 0)
    }

    @Test fun `insertExpense with zero amount succeeds`() {
        val id = db.insertExpense("Travel", 0.0, "unknown", "sms")
        assertTrue(id > 0)
    }

    // ── Query ────────────────────────────────────────────────────────────────
    @Test fun `getExpenses returns inserted data`() {
        db.insertExpense("Food", 100.0, "GPay", "notification")
        db.insertExpense("Travel", 200.0, "PhonePe", "sms")

        val all = db.getExpenses(0L)
        assertEquals(2, all.size)
    }

    @Test fun `getExpenses filters by fromTs`() {
        db.insertExpense("Food", 100.0, "GPay", "notification")
        Thread.sleep(50) // ensure different timestamp

        val futureTs = System.currentTimeMillis() + 100_000L
        val results = db.getExpenses(futureTs)
        assertEquals(0, results.size)
    }

    @Test fun `getExpenses returns newest first`() {
        db.insertExpense("Food", 100.0, "src1", "sms")
        Thread.sleep(50)
        db.insertExpense("Travel", 200.0, "src2", "notification")

        val all = db.getExpenses(0L)
        assertEquals("Travel", all[0].category) // newest first
        assertEquals("Food", all[1].category)
    }

    @Test fun `getExpenses respects toTs upper bound`() {
        db.insertExpense("Food", 100.0, "GPay", "notification")

        val pastTs = System.currentTimeMillis() - 100_000L
        val results = db.getExpenses(0L, pastTs)
        assertEquals(0, results.size)
    }

    // ── Category totals ──────────────────────────────────────────────────────
    @Test fun `getTotalByCategory sums correctly`() {
        db.insertExpense("Food", 100.0, "src", "sms")
        db.insertExpense("Food", 200.0, "src", "sms")
        db.insertExpense("Travel", 50.0, "src", "notif")

        val totals = db.getTotalByCategory(0L)
        assertEquals(300.0, totals["Food"]!!, 0.01)
        assertEquals(50.0, totals["Travel"]!!, 0.01)
    }

    @Test fun `getTotalByCategory empty when no data`() {
        val totals = db.getTotalByCategory(0L)
        assertTrue(totals.isEmpty())
    }

    // ── Transaction count ────────────────────────────────────────────────────
    @Test fun `getTransactionCount returns correct count`() {
        db.insertExpense("Food", 100.0, "src", "sms")
        db.insertExpense("Food", 200.0, "src", "sms")
        db.insertExpense("Travel", 50.0, "src", "sms")

        val count = db.getTransactionCount("Food", 0L, Long.MAX_VALUE)
        assertEquals(2, count)
    }

    @Test fun `getTransactionCount returns zero for missing category`() {
        val count = db.getTransactionCount("Nonexistent", 0L, Long.MAX_VALUE)
        assertEquals(0, count)
    }

    // ── Delete ───────────────────────────────────────────────────────────────
    @Test fun `deleteExpense removes the record`() {
        db.insertExpense("Food", 100.0, "src", "sms")
        val expenses = db.getExpenses(0L)
        assertEquals(1, expenses.size)

        db.deleteExpense(expenses[0].id)
        assertEquals(0, db.getExpenses(0L).size)
    }

    // ── Update category ──────────────────────────────────────────────────────
    @Test fun `updateExpenseCategory changes category`() {
        db.insertExpense("Food", 100.0, "src", "sms")
        val expense = db.getExpenses(0L)[0]

        db.updateExpenseCategory(expense.id, "Travel")
        val updated = db.getExpenses(0L)[0]
        assertEquals("Travel", updated.category)
    }

    // ── Clear all ────────────────────────────────────────────────────────────
    @Test fun `clearAll removes everything`() {
        db.insertExpense("Food", 100.0, "src", "sms")
        db.insertExpense("Travel", 200.0, "src", "notif")
        db.clearAll()
        assertEquals(0, db.getExpenses(0L).size)
    }
}
