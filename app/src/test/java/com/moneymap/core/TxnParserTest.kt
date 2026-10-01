package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class TxnParserTest {
    private val at = LocalDateTime.of(2026, 10, 3, 13, 5)
    private fun parse(text: String, title: String? = null) = TxnParser.parse(title, text, at, "test")

    @Test
    fun bankDebitToUpiMerchant() {
        val s = parse("Rs.250.00 debited from A/c XX1234 on 03-10-26 to VPA zomato.order@hdfcbank. Ref 1234567")!!
        assertEquals(250, s.amount)
        assertEquals("zomato order", s.merchant)
        assertEquals(ExpenseCategory.FOOD, s.category)
    }

    @Test
    fun cardSpendAndRupeeSymbol() {
        val s = parse("₹1,499.50 spent on your HDFC Bank Credit Card ending 4321 at AMAZON PAY on 2026-10-03")!!
        assertEquals(1_500, s.amount)
        assertEquals("Amazon Pay", s.merchant)
        assertEquals(ExpenseCategory.SHOPPING, s.category)
    }

    @Test
    fun upiAppPaidMessage() {
        val s = parse("Paid ₹120 to Uber India", title = "Payment successful")!!
        assertEquals(120, s.amount)
        assertEquals("Uber India", s.merchant)
        assertEquals(ExpenseCategory.TRANSPORT, s.category)
    }

    @Test
    fun inrWithoutMerchant() {
        val s = parse("INR 2000 withdrawn at ATM")
        assertNotNull(s)
        assertEquals(2_000, s!!.amount)
    }

    @Test
    fun ignoresCreditsOtpsAndReminders() {
        assertNull(parse("Rs.50,000.00 credited to your A/c XX1234 by NEFT. Avl bal is Rs 1,20,000"))
        assertNull(parse("123456 is your OTP for a transaction of Rs.499 at Amazon"))
        assertNull(parse("Your credit card statement is ready. Minimum due Rs 2,500, due date 15 Oct"))
        assertNull(parse("Rs.18000 will be debited on 07-10 for your SIP"))
        assertNull(parse("You received ₹300 from Mom"))
        assertNull(parse("Hello, see you at 5"))
    }

    @Test
    fun mergeSkipsTheSamePaymentFromTwoApps() {
        val a = parse("Rs 250 debited to VPA zomato@icici")!!
        val b = TxnParser.parse("Payment successful", "Paid ₹250 to Zomato", at.plusMinutes(1), "gpay")!!
        val c = TxnParser.parse(null, "Rs 90 debited to VPA chai@okaxis", at.plusMinutes(1), "bank")!!
        val merged = TxnParser.merge(TxnParser.merge(TxnParser.merge(emptyList(), a), b), c)
        assertEquals(listOf(90L, 250L).sorted(), merged.map { it.amount }.sorted())
        assertEquals(50, (1..80).fold(emptyList<SuggestedExpense>()) { acc, i ->
            TxnParser.merge(acc, a.copy(id = "$i", at = at.plusHours(i.toLong())))
        }.size)
    }
}
