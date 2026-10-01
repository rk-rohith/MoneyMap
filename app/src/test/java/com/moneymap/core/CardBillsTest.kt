package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CardBillsTest {
    private val received = LocalDate.of(2026, 9, 28)
    private fun parse(text: String, title: String? = null) = CardBillParser.parse(title, text, received)

    @Test
    fun hdfcStyleStatement() {
        val b = parse("HDFC Bank Credit Card XX1234 statement generated. Total due: Rs.12,345.60, Min due: Rs.620.00, " +
            "due by 15-Oct-2026. Pay now")!!
        assertEquals("HDFC card ••1234", b.card)
        assertEquals(12_346, b.amount)
        assertEquals(620L, b.minimumDue)
        assertEquals(LocalDate.of(2026, 10, 15), b.dueDate)
        assertEquals("1234-2026-10-15", b.id)
    }

    @Test
    fun otherFormats() {
        val icici = parse("Your ICICI Bank Credit Card ending 9876 bill of INR 4,500 is due on 05/10/26. Minimum amount due INR 225.")!!
        assertEquals(4_500, icici.amount)
        assertEquals(LocalDate.of(2026, 10, 5), icici.dueDate)
        assertEquals(225L, icici.minimumDue)
        val noYear = parse("Statement for your Axis Bank credit card **5555: Total amount due ₹ 8,000. Payment due date 3 Oct")!!
        assertEquals(LocalDate.of(2026, 10, 3), noYear.dueDate)
        assertEquals("AXIS card ••5555", noYear.card)
        // A day already passed this year means next year.
        assertEquals(LocalDate.of(2027, 1, 2), CardBillParser.parseDate("Jan 2", received))
        assertEquals(LocalDate.of(2026, 10, 21), CardBillParser.parseDate("21st Oct 2026", received))
    }

    @Test
    fun ignoresSpendsPaymentsAndNonCards() {
        assertNull(parse("Rs.500 spent on your HDFC Credit Card XX1234 at AMAZON"))
        assertNull(parse("Payment of Rs.12,345 received towards your HDFC credit card statement. Thank you"))
        assertNull(parse("Your electricity bill of Rs 900 is due on 10 Oct"))
        assertNull(parse("HDFC credit card statement is ready")) // no amount or date
    }

    @Test
    fun mergeReplacesSameCardAndDueDate() {
        val a = CardBill("1234-2026-10-15", "HDFC card ••1234", 12_000, null, LocalDate.of(2026, 10, 15))
        val b = a.copy(amount = 12_500)
        val old = CardBill("9-2026-06-01", "Old", 1, null, LocalDate.of(2026, 6, 1))
        assertEquals(listOf(b), CardBillParser.merge(listOf(old, a), b, received))
    }
}

class CardBillPlanTest {
    @Test
    fun cardBillBecomesAReminderNotABudgetLine() {
        val bill = CardBill("1234-2026-10-15", "HDFC card ••1234", 12_000, 600, LocalDate.of(2026, 10, 15))
        val engine = PlanEngine(DefaultPlan.settings.copy(cardBills = listOf(bill)))
        val start = LocalDate.of(2026, 9, 25)
        val item = engine.itemById("2026-10-15:card-1234-2026-10-15")!!
        assertEquals("Pay HDFC card ••1234 bill", item.title)
        assertEquals(ItemKind.BILL, item.kind)
        assertEquals(true, item.notifies)
        assertEquals(DefaultPlan.engine.budget(start), engine.budget(start))
    }
}
