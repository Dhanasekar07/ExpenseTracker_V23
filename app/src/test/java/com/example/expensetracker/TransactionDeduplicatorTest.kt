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
class TransactionDeduplicatorTest {

    private lateinit var dedup: TransactionDeduplicator

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("expense_dedup", Context.MODE_PRIVATE)
            .edit().clear().commit()
        dedup = TransactionDeduplicator(ctx)
    }

    @Test fun `makeHash returns non-empty string`() {
        assertTrue(dedup.makeHash(100.0, "GPay", "Payment of Rs 100").isNotEmpty())
    }

    @Test fun `makeHash is deterministic`() {
        val h1 = dedup.makeHash(100.0, "GPay", "Payment")
        val h2 = dedup.makeHash(100.0, "GPay", "Payment")
        assertEquals(h1, h2)
    }

    @Test fun `makeHash differs for different amounts`() {
        assertNotEquals(
            dedup.makeHash(100.0, "GPay", "Payment"),
            dedup.makeHash(200.0, "GPay", "Payment")
        )
    }

    @Test fun `makeHash differs for different sources`() {
        assertNotEquals(
            dedup.makeHash(100.0, "GPay", "Payment"),
            dedup.makeHash(100.0, "PhonePe", "Payment")
        )
    }

    @Test fun `makeHash is 64 char hex`() {
        val hash = dedup.makeHash(99.0, "test", "text")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun `isSeen false for new hash`() {
        assertFalse(dedup.isSeen(dedup.makeHash(100.0, "src", "text")))
    }

    @Test fun `isSeen true after markSeen`() {
        val hash = dedup.makeHash(100.0, "src", "text")
        dedup.markSeen(hash)
        assertTrue(dedup.isSeen(hash))
    }

    @Test fun `isDuplicate false for first call`() {
        assertFalse(dedup.isDuplicate(100.0, "GPay", "Paid Rs 100 to Swiggy"))
    }

    @Test fun `isDuplicate true for repeated call`() {
        dedup.isDuplicate(100.0, "GPay", "Paid Rs 100 to Swiggy")
        assertTrue(dedup.isDuplicate(100.0, "GPay", "Paid Rs 100 to Swiggy"))
    }

    @Test fun `different transactions not blocked`() {
        dedup.isDuplicate(100.0, "GPay", "Paid Rs 100 to Swiggy")
        assertFalse(dedup.isDuplicate(200.0, "GPay", "Paid Rs 200 to Zomato"))
    }

    @Test fun `cleanup does not crash`() {
        dedup.markSeen(dedup.makeHash(100.0, "src", "text"))
        dedup.cleanup()
    }
}
