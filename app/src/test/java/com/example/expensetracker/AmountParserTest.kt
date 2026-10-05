package com.example.expensetracker

import org.junit.Assert.*
import org.junit.Test

class AmountParserTest {

    // ── Rupee symbol ─────────────────────────────────────────────────────────
    @Test fun `parse rupee symbol with integer`() {
        assertEquals(500.0, AmountParser.parse("You paid ₹500 to Zomato"), 0.01)
    }

    @Test fun `parse rupee symbol with decimals`() {
        assertEquals(1234.56, AmountParser.parse("Debited ₹1,234.56 from A/C"), 0.01)
    }

    @Test fun `parse rupee symbol no space`() {
        assertEquals(99.0, AmountParser.parse("₹99 debited"), 0.01)
    }

    @Test fun `parse rupee symbol with space`() {
        assertEquals(250.0, AmountParser.parse("₹ 250 paid"), 0.01)
    }

    // ── Rs prefix ────────────────────────────────────────────────────────────
    @Test fun `parse Rs dot prefix`() {
        assertEquals(1500.0, AmountParser.parse("Rs.1,500 debited from your A/C"), 0.01)
    }

    @Test fun `parse Rs no dot`() {
        assertEquals(300.0, AmountParser.parse("Rs 300 debited"), 0.01)
    }

    @Test fun `parse Rs dot with space`() {
        assertEquals(750.50, AmountParser.parse("Rs. 750.50 credited"), 0.01)
    }

    // ── INR prefix ───────────────────────────────────────────────────────────
    @Test fun `parse INR prefix`() {
        assertEquals(2000.0, AmountParser.parse("INR 2000 debited from your account"), 0.01)
    }

    @Test fun `parse INR uppercase`() {
        assertEquals(100.0, AmountParser.parse("Paid INR 100"), 0.01)
    }

    // ── Trailing /- format ───────────────────────────────────────────────────
    @Test fun `parse trailing slash-dash format`() {
        assertEquals(450.0, AmountParser.parse("Amount 450/-"), 0.01)
    }

    @Test fun `parse trailing slash-dash with comma`() {
        assertEquals(10000.0, AmountParser.parse("10,000/- debited"), 0.01)
    }

    // ── Commas in amounts ────────────────────────────────────────────────────
    @Test fun `parse large amount with commas`() {
        assertEquals(25000.0, AmountParser.parse("₹25,000 transferred via UPI"), 0.01)
    }

    @Test fun `parse Indian lakh format`() {
        assertEquals(150000.0, AmountParser.parse("Rs.1,50,000 credited"), 0.01)
    }

    // ── Edge cases ───────────────────────────────────────────────────────────
    @Test fun `parse returns zero for empty string`() {
        assertEquals(0.0, AmountParser.parse(""), 0.01)
    }

    @Test fun `parse returns zero for blank string`() {
        assertEquals(0.0, AmountParser.parse("   "), 0.01)
    }

    @Test fun `parse returns zero for no amount`() {
        assertEquals(0.0, AmountParser.parse("Your OTP is 394821"), 0.01)
    }

    @Test fun `parse returns zero for non-monetary numbers`() {
        assertEquals(0.0, AmountParser.parse("Your order 12345 has been shipped"), 0.01)
    }

    @Test fun `parse handles real bank SMS`() {
        val sms = "HDFC Bank: Rs 2,499.00 debited from a/c **1234 on 15-01-25. Avl Bal: Rs 45,678.90. UPI Ref: 501234567890"
        assertEquals(2499.0, AmountParser.parse(sms), 0.01)
    }

    @Test fun `parse handles GPay notification`() {
        assertEquals(150.0, AmountParser.parse("Payment of ₹150 to FreshMart successful"), 0.01)
    }

    @Test fun `parse handles PhonePe notification`() {
        assertEquals(89.0, AmountParser.parse("Paid ₹89.00 to Chai Point"), 0.01)
    }

    @Test fun `parse case insensitive INR`() {
        assertEquals(500.0, AmountParser.parse("inr 500 debited"), 0.01)
    }

    @Test fun `parse with plus prefix`() {
        assertEquals(1000.0, AmountParser.parse("+₹1,000 credited to your account"), 0.01)
    }
}
