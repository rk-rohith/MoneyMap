package com.moneymap.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProfileTest {
    @After
    fun restore() {
        Plan.profile = Profile()
    }

    @Test
    fun defaultsMatchTheOriginalSetup() {
        assertEquals(LocalDate.of(2026, 10, 24), Plan.cycleEnd(LocalDate.of(2026, 9, 25)))
        assertEquals(LocalDate.of(2026, 9, 25), Plan.cycleStartFor(LocalDate.of(2026, 10, 24)))
        assertEquals("Jupiter main", ReturnPod.JUPITER_MAIN.label)
        assertEquals("Paid from HDFC", Flow.HDFC.label)
    }

    @Test
    fun salaryOnTheFirst() {
        Plan.profile = Profile(salaryDay = 1, salaryAccount = "SBI", spendAccount = "Wallet", goalPod = "House pod")
        assertEquals(LocalDate.of(2027, 2, 1), Plan.cycleStartFor(LocalDate.of(2027, 2, 28)))
        assertEquals(LocalDate.of(2027, 2, 28), Plan.cycleEnd(LocalDate.of(2027, 2, 1)))
        assertEquals(LocalDate.of(2027, 3, 31), Plan.cycleEnd(LocalDate.of(2027, 3, 1)))
        // Day 15 falls in the same month as the 1st.
        assertEquals(LocalDate.of(2027, 3, 15), Plan.occurrence(LocalDate.of(2027, 3, 1), 15))
        assertEquals("Wallet main", ReturnPod.JUPITER_MAIN.label)
        assertEquals("House pod", ReturnPod.SRI_LANKA.label)
        assertTrue(Pods.DEFAULT_PODS.contains("House pod"))
    }

    @Test
    fun planTextUsesAccountNames() {
        Plan.profile = Profile(salaryDay = 1, salaryAccount = "SBI", spendAccount = "Wallet", loanTotal = 0)
        val start = LocalDate.of(2027, 3, 1)
        val settings = PlanSettings(
            listOf(PlanVersion(start, PlanConfig(50_000, 15_000, listOf(
                RecurringItem("rent", "Rent", 12_000, 5, Flow.HDFC, "SBI"),
                RecurringItem("sip", "SIP", 5_000, 10, Flow.POD, "Wallet", autopay = true, pod = "SIP pod"),
            )))),
            debt = listOf(DebtInstalment(LocalDate.of(2027, 3, 20), 4_000)),
        )
        val items = PlanEngine(settings).items(start)
        val text = items.joinToString("\n") { "${it.title} ${it.detail} ${it.steps.joinToString()} ${it.account}" }
        assertFalse(text, text.contains("HDFC"))
        assertFalse(text, text.contains("Jupiter"))
        assertTrue(text.contains("Send ₹38,000 to Wallet"))
        assertTrue(text.contains("Leave ₹12,000 in SBI for bills"))
        assertTrue(text.contains("Move SIP pod money to Wallet main"))
        assertEquals("Loan instalment", items.first { it.kind == ItemKind.DEBT }.detail)
        assertEquals(LocalDate.of(2027, 3, 31), items.first { it.kind == ItemKind.REVIEW }.date)
        // 50,000 − 12,000 bills = 38,000 to Wallet; − 4,000 debt − 5,000 SIP − 15,000 spending = 14,000 emergency.
        assertEquals(14_000, PlanEngine(settings).budget(start).emergencyPod)
    }
}
