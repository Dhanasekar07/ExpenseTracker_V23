package com.example.expensetracker

import org.junit.Assert.*
import org.junit.Test

class ExpenseModelTest {

    @Test fun `expense data class stores all fields`() {
        val expense = Expense(
            id        = "abc-123",
            category  = "Food",
            amount    = 250.50,
            source    = "GPay",
            channel   = "notification",
            timestamp = 1700000000000L
        )
        assertEquals("abc-123", expense.id)
        assertEquals("Food", expense.category)
        assertEquals(250.50, expense.amount, 0.01)
        assertEquals("GPay", expense.source)
        assertEquals("notification", expense.channel)
        assertEquals(1700000000000L, expense.timestamp)
    }

    @Test fun `expense default id is empty string`() {
        val expense = Expense(
            category  = "Tea",
            amount    = 10.0,
            source    = "test",
            channel   = "sms",
            timestamp = System.currentTimeMillis()
        )
        assertEquals("", expense.id)
    }

    @Test fun `expense equality by content`() {
        val ts = System.currentTimeMillis()
        val a = Expense("x", "Food", 100.0, "GPay", "notif", ts)
        val b = Expense("x", "Food", 100.0, "GPay", "notif", ts)
        assertEquals(a, b)
    }

    @Test fun `expense inequality by id`() {
        val ts = System.currentTimeMillis()
        val a = Expense("x", "Food", 100.0, "GPay", "notif", ts)
        val b = Expense("y", "Food", 100.0, "GPay", "notif", ts)
        assertNotEquals(a, b)
    }
}
