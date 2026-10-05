package com.example.expensetracker

import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class TransactionFilterTest {

    // ── Should trigger (legitimate debits) ───────────────────────────────
    @Test fun `standard debit triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Rs 500 debited from your a/c"))
    }

    @Test fun `UPI payment triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Rs 150 paid to FreshMart via UPI"))
    }

    @Test fun `purchase triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Purchase of Rs 999 at Amazon"))
    }

    @Test fun `withdrawal triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Rs 5000 withdrawn from ATM"))
    }

    @Test fun `sent triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Rs 200 sent to merchant via UPI"))
    }

    @Test fun `used triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Rs 150 used at BigBasket"))
    }

    @Test fun `auto-renewed triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Netflix Rs 499 auto-renewed from card"))
    }

    @Test fun `charged triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Rs 299 charged to your credit card"))
    }

    @Test fun `deducted triggers`() {
        assertTrue(TransactionFilter.isLegitimateDebit("Rs 1000 deducted for EMI"))
    }

    // ── Should NOT trigger (credits) ─────────────────────────────────────
    @Test fun `credit message skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit("Rs 25000 credited to your a/c"))
    }

    @Test fun `salary credit skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit("Rs 50000 received via NEFT"))
    }

    @Test fun `refund skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit("Refund of Rs 500 credited to your a/c"))
    }

    // ── Should NOT trigger (false positives) ─────────────────────────────
    @Test fun `refund with debit word skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "The amount of INR 500 debited on 12-Aug has been refunded to your a/c"
        ))
    }

    @Test fun `promotional message skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "Get 5% cashback when you have spent Rs 10000 on your credit card"
        ))
    }

    @Test fun `payment reminder skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "Your auto-pay is set. Rs 5000 will be deducted tomorrow"
        ))
    }

    @Test fun `failed transaction skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "Txn of Rs 500 failed. If debited, the amount will be reversed"
        ))
    }

    @Test fun `payment due reminder skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "Your EMI payment of Rs 5000 is due on 15th"
        ))
    }

    @Test fun `payment request skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "David has requested payment of Rs 500"
        ))
    }

    @Test fun `future charge skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "Payment of Rs 999 will be charged on 10th"
        ))
    }

    @Test fun `pending approval skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "Rs 1000 payment is pending approval"
        ))
    }

    @Test fun `self transfer skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(
            "Rs 10000 paid to your own account"
        ))
    }

    // ── Should NOT trigger (no debit keyword) ────────────────────────────
    @Test fun `balance check skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit("Your balance is Rs 45000"))
    }

    @Test fun `OTP message skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit("Your OTP is 394821 for Rs 100 txn"))
    }

    @Test fun `plain number skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit("Your order 12345 has been shipped"))
    }

    @Test fun `empty string skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit(""))
    }

    @Test fun `promotional offer skipped`() {
        assertFalse(TransactionFilter.isLegitimateDebit("Get Rs 500 off on your next order"))
    }
}
