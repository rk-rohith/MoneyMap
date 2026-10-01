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

class VariableIncomeTest {
    private val start = LocalDate.of(2026, 10, 25)

    @After
    fun restore() {
        Plan.profile = Profile()
    }

    @Test
    fun salaryOverrideAppliesToOneCycleOnly() {
        val settings = DefaultPlan.settings.copy(salaryOverrides = mapOf(start to 200_000L))
        val engine = PlanEngine(settings)
        val base = DefaultPlan.engine.budget(start)
        assertEquals(200_000, engine.budget(start).salary)
        assertEquals(base.emergencyPod + 40_000, engine.budget(start).emergencyPod)
        assertEquals(160_000, engine.budget(start.plusMonths(1)).salary)
        assertEquals(160_000, settings.baseConfigFor(start).salary)
        assertTrue(engine.items(start).first { it.id.endsWith(":salary") }.detail.contains("changed"))
    }

    @Test
    fun extraIncomeAddsToItsCycle() {
        val bonus = ExtraIncome("b1", "Diwali bonus", 25_000, LocalDate.of(2026, 11, 3))
        val engine = PlanEngine(DefaultPlan.settings.copy(extraIncome = listOf(bonus)))
        val b = engine.budget(start)
        assertTrue(b.otherIncome.any { it.label == "Diwali bonus" && it.amount == 25_000L })
        assertEquals(DefaultPlan.engine.budget(start).emergencyPod + 25_000, b.emergencyPod)
        val item = engine.itemById("2026-11-03:extra-b1")!!
        assertEquals(ItemKind.INCOME, item.kind)
        assertFalse(item.notifies)
        assertEquals(DefaultPlan.engine.budget(start.plusMonths(1)), engine.budget(start.plusMonths(1)))
    }

    @Test
    fun weekendSalaryArrivesOnFriday() {
        // 25 Oct 2026 is a Sunday.
        Plan.profile = Profile(weekendSalaryEarly = true)
        val items = DefaultPlan.engine.items(start)
        val salary = items.first { it.id == "2026-10-25:salary-day" }
        assertEquals(LocalDate.of(2026, 10, 23), salary.date)
        assertEquals(salary, DefaultPlan.engine.itemById("2026-10-25:salary-day"))
        // Found when looking at the days before the cycle starts.
        assertTrue(DefaultPlan.engine.itemsBetween(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 23))
            .any { it.id == "2026-10-25:salary-day" })
        Plan.profile = Profile()
        assertEquals(start, DefaultPlan.engine.items(start).first { it.id == "2026-10-25:salary-day" }.date)
    }
}

class CurrencyTest {
    @After
    fun restore() {
        Plan.profile = Profile()
    }

    @Test
    fun indianByDefaultInternationalOnRequest() {
        assertEquals("₹12,34,567", formatInr(1_234_567))
        Plan.profile = Profile(currencySymbol = "$", indianGrouping = false)
        assertEquals("$1,234,567", formatInr(1_234_567))
        assertEquals("-$999", formatInr(-999))
        assertEquals("1,000", formatInr(1_000, withSymbol = false))
        assertEquals(2_500L, parseAmount("$ 2,500"))
        assertEquals(2_500L, parseAmount("₹2500"))
        assertTrue(Search.matches("$1,500", listOf("x"), listOf(1_500)))
    }
}
