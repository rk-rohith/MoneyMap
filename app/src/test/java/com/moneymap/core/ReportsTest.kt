package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportsTest {
    private val today = LocalDate.of(2027, 1, 10) // Dec 2026 cycle (25 Dec – 24 Jan)
    private fun r(date: String, amount: Long, c: ExpenseCategory = ExpenseCategory.FOOD, note: String = "") =
        SpendRecord(LocalDate.parse(date), amount, c, note)

    @Test
    fun cyclesOldestFirstWithBudgets() {
        val records = listOf(r("2026-12-26", 500), r("2027-01-05", 700, ExpenseCategory.FUN), r("2026-11-30", 1_000))
        val reports = Reports.cycles(records, DefaultPlan.engine, today, count = 3)
        assertEquals(listOf("2026-10-25", "2026-11-25", "2026-12-25").map(LocalDate::parse), reports.map { it.cycleStart })
        assertEquals(listOf(0L, 1_000L, 1_200L), reports.map { it.spent })
        assertEquals(20_000, reports.last().budget)
        assertEquals(700L, reports.last().byCategory[ExpenseCategory.FUN])
    }

    @Test
    fun trendsAgainstEarlierCyclesWithSpending() {
        val records = listOf(r("2026-11-30", 1_000), r("2026-10-30", 3_000), r("2026-12-26", 4_000))
        val trends = Reports.trends(Reports.cycles(records, DefaultPlan.engine, today, count = 4))
        val food = trends.single()
        assertEquals(4_000, food.thisCycle)
        assertEquals(2_000, food.average) // empty Sep cycle doesn't count
        assertEquals(1f, food.change!!, 0.001f)
    }

    @Test
    fun budgetAlerts() {
        assertEquals(80, Reports.budgetAlert(7_000, 1_000, 10_000))
        assertEquals(100, Reports.budgetAlert(9_000, 1_500, 10_000))
        assertEquals(100, Reports.budgetAlert(1_000, 20_000, 10_000))
        assertNull(Reports.budgetAlert(8_500, 500, 10_000))
        assertNull(Reports.budgetAlert(11_000, 500, 10_000))
        assertNull(Reports.budgetAlert(0, 500, 0))
        val status = Reports.budgetStatus(listOf(r("2026-12-26", 4_000), r("2026-12-27", 300, ExpenseCategory.FUN)),
            LocalDate.of(2026, 12, 25), mapOf(ExpenseCategory.FOOD to 5_000L, ExpenseCategory.FUN to 1_000L, ExpenseCategory.HEALTH to 0L))
        assertEquals(listOf(ExpenseCategory.FOOD, ExpenseCategory.FUN), status.map { it.category })
        assertEquals(1_000, status.first().left)
    }

    @Test
    fun recurringNeedsThreeMonthsSteadyAmountAndRecentUse() {
        val records = listOf(
            r("2026-10-05", 649, ExpenseCategory.FUN, "Netflix"),
            r("2026-11-05", 649, ExpenseCategory.FUN, "netflix "),
            r("2026-12-05", 649, ExpenseCategory.FUN, "NETFLIX"),
            r("2027-01-05", 649, ExpenseCategory.FUN, "Netflix"),
            // Varies too much.
            r("2026-10-10", 200, note = "Lunch"), r("2026-11-10", 900, note = "Lunch"), r("2026-12-10", 450, note = "Lunch"),
            // Only two months.
            r("2026-12-01", 1_200, ExpenseCategory.HEALTH, "Gym"), r("2027-01-01", 1_200, ExpenseCategory.HEALTH, "Gym"),
            // Stopped in October.
            r("2026-08-02", 99, note = "Old app"), r("2026-09-02", 99, note = "Old app"), r("2026-10-02", 99, note = "Old app"),
            // Already in the plan.
            r("2026-10-07", 500, note = "Maid"), r("2026-11-07", 500, note = "Maid"), r("2026-12-07", 500, note = "Maid"),
        )
        val guesses = Reports.recurring(records, today, known = listOf("maid"))
        assertEquals(1, guesses.size)
        val n = guesses.single()
        assertEquals("Netflix", n.title)
        assertEquals(649, n.typicalAmount)
        assertEquals(4, n.months)
        assertEquals(5, n.day)
        assertTrue(Reports.recurring(emptyList(), today).isEmpty())
    }
}

class SearchFilterTest {
    private val today = java.time.LocalDate.of(2026, 11, 2) // Oct 2026 cycle

    @Test
    fun periodsAmountsAndCategory() {
        val f = SearchFilter(SearchPeriod.THIS_CYCLE, minAmount = 100, maxAmount = 1_000, category = ExpenseCategory.FOOD)
        assertTrue(f.active)
        assertTrue(f.accepts(java.time.LocalDate.of(2026, 10, 25), 500, today, ExpenseCategory.FOOD))
        org.junit.Assert.assertFalse(f.accepts(java.time.LocalDate.of(2026, 10, 24), 500, today, ExpenseCategory.FOOD))
        org.junit.Assert.assertFalse(f.accepts(java.time.LocalDate.of(2026, 10, 26), 50, today, ExpenseCategory.FOOD))
        org.junit.Assert.assertFalse(f.accepts(java.time.LocalDate.of(2026, 10, 26), 5_000, today, ExpenseCategory.FOOD))
        org.junit.Assert.assertFalse(f.accepts(java.time.LocalDate.of(2026, 10, 26), 500, today, ExpenseCategory.FUN))
        // Pod withdrawals are negative; the filter compares sizes.
        assertTrue(SearchFilter(minAmount = 100).accepts(today, -500, today))
        assertEquals(java.time.LocalDate.of(2026, 9, 25)..java.time.LocalDate.of(2026, 10, 24), SearchPeriod.LAST_CYCLE.range(today))
        org.junit.Assert.assertFalse(SearchFilter().active)
    }
}

class CycleSummaryTest {
    @Test
    fun sectionsCoverPlanSpendingPaymentsPeopleAndPods() {
        val start = java.time.LocalDate.of(2026, 9, 25)
        val today = java.time.LocalDate.of(2026, 10, 10)
        val expenses = listOf(
            SpendRecord(java.time.LocalDate.of(2026, 9, 30), 1_200, ExpenseCategory.FOOD, "Dinner"),
            SpendRecord(java.time.LocalDate.of(2026, 10, 2), 800, ExpenseCategory.TRANSPORT, ""),
            SpendRecord(java.time.LocalDate.of(2026, 10, 30), 5_000, ExpenseCategory.FUN, "Next cycle"),
        )
        val sections = CycleSummary.build(start, DefaultPlan.engine, expenses, setOf("2026-10-01:sip-jupiter"),
            LedgerSummary(5_000, 2_000), listOf(PodBalance("Emergency pod", 10_000, emptyList()), PodBalance("Empty", 0, emptyList())),
            null, today)
        assertEquals(listOf("Income and plan", "Spending", "Payments", "People", "Pods"), sections.map { it.title })
        val spending = sections[1].rows.toMap()
        assertEquals("₹2,000 of ₹20,000", spending["Spent"])
        assertEquals("₹18,000", spending["Left"])
        assertEquals("₹1,200 Dinner", spending["Largest"])
        val payments = sections[2].rows
        assertTrue(payments.first().second.startsWith("1 of "))
        assertTrue(payments.any { it.first == "Not ticked: Term insurance" })
        assertEquals(listOf("Emergency pod" to "₹10,000", "Total" to "₹10,000"), sections[4].rows)
        assertEquals("Sep 2026 cycle (25 Sep – 24 Oct 2026)", CycleSummary.title(start))
    }
}
