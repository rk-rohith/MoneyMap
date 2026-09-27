package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val plan = DefaultPlan.engine

    @Test
    fun cycleRunsFrom25thTo24th() {
        assertEquals(d(2026, 9, 25), Plan.cycleStartFor(d(2026, 9, 27)))
        assertEquals(d(2026, 9, 25), Plan.cycleStartFor(d(2026, 10, 24)))
        assertEquals(d(2026, 10, 25), Plan.cycleStartFor(d(2026, 10, 25)))
        assertEquals(d(2026, 12, 25), Plan.cycleStartFor(d(2027, 1, 5)))
        assertEquals(d(2026, 11, 24), Plan.cycleEnd(d(2026, 10, 25)))
        assertEquals(d(2026, 11, 3), Plan.occurrence(d(2026, 10, 25), 3))
        assertEquals(d(2026, 10, 28), Plan.occurrence(d(2026, 10, 25), 28))
        assertEquals(d(2027, 1, 31), Plan.occurrence(d(2027, 1, 25), 31))
        assertEquals(d(2026, 11, 30), Plan.occurrence(d(2026, 11, 25), 31))
    }

    @Test
    fun emergencyFundPerCycle() {
        assertEquals(6_700L, plan.budget(d(2026, 10, 25)).emergencyPod)
        assertEquals(6_700L, plan.budget(d(2026, 11, 25)).emergencyPod)
        assertEquals(6_700L, plan.budget(d(2026, 12, 25)).emergencyPod)
        assertEquals(64_700L, plan.budget(d(2027, 1, 25)).emergencyPod)
        assertEquals(76_700L, plan.budget(d(2027, 2, 25)).emergencyPod)
        assertEquals(76_700L, plan.budget(d(2027, 8, 25)).emergencyPod)
    }

    @Test
    fun budgetBalancesAndIncomeIncludesRent() {
        val b = plan.budget(d(2026, 10, 25))
        assertEquals(168_000L, b.income)
        assertEquals(20_000L, b.salaryDayTotal)
        assertEquals(43_300L, b.hdfcHold)
        assertEquals(104_700L, b.transferToJupiter)
        assertEquals(60_000L, b.debtPod)
        assertEquals(18_000L, b.pod("SIP pod"))
        assertEquals(20_000L, b.jupiterMain)
        assertEquals(b.income, b.totalOut + b.emergencyPod)
        for (m in 0 until 24) {
            val x = plan.budget(d(2026, 9, 25).plusMonths(m.toLong()))
            assertEquals(x.income, x.totalOut + x.emergencyPod)
        }
    }

    @Test
    fun debtSchedule() {
        assertEquals(0L, plan.budget(d(2026, 9, 25)).debtPod)
        assertEquals(60_000L, plan.budget(d(2026, 10, 25)).debtPod)
        assertEquals(20_000L, plan.budget(d(2027, 1, 25)).debtPod)
        assertEquals(0L, plan.budget(d(2027, 2, 25)).debtPod)
        assertEquals(Plan.LOAN_TOTAL, DefaultPlan.debt.sumOf { it.amount })
        val debtItems = plan.itemsBetween(d(2026, 9, 27), d(2027, 6, 30)).filter { it.kind == ItemKind.DEBT }
        assertEquals(listOf(d(2026, 10, 25), d(2026, 11, 25), d(2026, 12, 25), d(2027, 1, 25)), debtItems.map { it.date })
        assertTrue(debtItems.all { Plan.isDebtItem(it.id) && it.pod == Plan.DEBT_POD })
        assertFalse(Plan.isDebtItem("2026-10-25:debt-free"))
    }

    @Test
    fun rentEndsAfterJan2027() {
        val rentPaid = plan.itemsBetween(d(2026, 9, 27), d(2027, 12, 31)).filter { it.id.endsWith(":rent-out") }
        assertEquals(listOf(d(2026, 10, 7), d(2026, 11, 7), d(2026, 12, 7), d(2027, 1, 7)), rentPaid.map { it.date })
        assertTrue(rentPaid.all { it.amount == 18_000L })
        assertEquals("Last payment", rentPaid.last().detail)

        val rentIn = plan.itemsBetween(d(2026, 9, 25), d(2027, 12, 31)).filter { it.id.endsWith(":rent-in") }
        assertEquals(d(2027, 1, 25), rentIn.last().date)
        assertTrue(plan.budget(d(2027, 2, 25)).otherIncome.isEmpty())
        assertEquals(0L, plan.budget(d(2027, 1, 25)).hdfcBills.filter { it.label == "Pay rent" }.sumOf { it.amount })
    }

    @Test
    fun cycleItemsHaveExpectedDates() {
        val items = plan.items(d(2026, 10, 25))
        fun date(key: String) = items.first { it.id.endsWith(":$key") }.date
        assertEquals(d(2026, 10, 25), date("salary"))
        assertEquals(d(2026, 10, 25), date("utility"))
        assertEquals(d(2026, 11, 1), date("move-sip-jupiter"))
        assertEquals(d(2026, 11, 1), date("sip-jupiter"))
        assertEquals(d(2026, 11, 3), date("term"))
        assertEquals(d(2026, 11, 7), date("rent-out"))
        assertEquals(d(2026, 11, 17), date("car-emi"))
        assertEquals(d(2026, 11, 24), date("review"))
        assertEquals(items.sortedBy { it.date }.map { it.date }, items.map { it.date })
        val salaryDay = items.first { it.kind == ItemKind.SALARY_DAY }
        assertTrue(salaryDay.steps.any { it.contains("₹1,04,700") })
        assertTrue(salaryDay.steps.any { it == "SIP pod: ₹18,000" })
        assertEquals("SIP pod", items.first { it.id.endsWith(":move-sip-jupiter") }.pod)
    }

    @Test
    fun itemLookupAndTracking() {
        val item = plan.itemById("2026-11-17:car-emi")
        assertNotNull(item)
        assertEquals(20_000L, item!!.amount)
        assertNull(plan.itemById("garbage"))
        assertFalse(plan.itemById("2026-09-25:utility")!!.tracked)
        assertTrue(plan.itemById("2026-10-01:move-sip-jupiter")!!.tracked)
    }

    @Test
    fun salaryChangeAppliesFromChosenCycleOnly() {
        val raised = DefaultPlan.config.copy(salary = 180_000)
        val engine = PlanEngine(DefaultPlan.settings.withVersion(d(2027, 4, 25), raised))
        assertEquals(76_700L, engine.budget(d(2027, 3, 25)).emergencyPod)
        assertEquals(96_700L, engine.budget(d(2027, 4, 25)).emergencyPod)
        assertEquals(96_700L, engine.budget(d(2028, 1, 25)).emergencyPod)
        assertEquals(180_000L, engine.itemById("2027-05-25:salary")!!.amount)
        assertEquals(160_000L, engine.itemById("2027-03-25:salary")!!.amount)
        // Cycles before the first version fall back to it.
        assertEquals(160_000L, engine.config(d(2026, 1, 25)).salary)
        // Removing the change restores the old plan.
        val back = PlanEngine(engine.settings.withoutVersion(d(2027, 4, 25)))
        assertEquals(76_700L, back.budget(d(2027, 4, 25)).emergencyPod)
        // The only version can't be removed.
        assertEquals(1, DefaultPlan.settings.withoutVersion(DefaultPlan.BASE_CYCLE).versions.size)
    }

    @Test
    fun addedItemsWithStartAndEnd() {
        val newEmi = RecurringItem("phone", "Phone EMI", 2_500, 10, Flow.HDFC, Plan.HDFC, autopay = true,
            startDate = d(2027, 3, 1), endDate = d(2027, 5, 31))
        val cfg = DefaultPlan.config.copy(items = DefaultPlan.config.items + newEmi)
        val engine = PlanEngine(DefaultPlan.settings.withVersion(DefaultPlan.BASE_CYCLE, cfg))
        val dates = engine.itemsBetween(d(2027, 1, 1), d(2027, 12, 31)).filter { it.id.endsWith(":phone") }.map { it.date }
        assertEquals(listOf(d(2027, 3, 10), d(2027, 4, 10), d(2027, 5, 10)), dates)
        assertEquals(74_200L, engine.budget(d(2027, 2, 25)).emergencyPod)
        assertEquals(76_700L, engine.budget(d(2027, 5, 25)).emergencyPod)
        assertEquals(ItemKind.AUTOPAY, engine.itemById("2027-03-10:phone")!!.kind)
    }

    @Test
    fun indianNumberFormat() {
        assertEquals("₹0", formatInr(0))
        assertEquals("₹999", formatInr(999))
        assertEquals("₹1,000", formatInr(1_000))
        assertEquals("₹20,000", formatInr(20_000))
        assertEquals("₹1,68,000", formatInr(168_000))
        assertEquals("₹2,00,000", formatInr(200_000))
        assertEquals("₹12,34,567", formatInr(1_234_567))
        assertEquals("₹1,23,45,678", formatInr(12_345_678))
        assertEquals("-₹6,700", formatInr(-6_700))
        assertEquals("88,874", formatInr(88_874, withSymbol = false))
    }

    @Test
    fun parsesAmounts() {
        assertEquals(168_000L, parseAmount("1,68,000"))
        assertEquals(2_500L, parseAmount("₹ 2500"))
        assertNull(parseAmount(""))
        assertNull(parseAmount("-5"))
        assertNull(parseAmount("abc"))
    }
}
