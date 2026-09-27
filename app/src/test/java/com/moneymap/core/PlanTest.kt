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

    @Test
    fun cycleRunsFrom25thTo24th() {
        assertEquals(d(2026, 9, 25), Plan.cycleStartFor(d(2026, 9, 27)))
        assertEquals(d(2026, 9, 25), Plan.cycleStartFor(d(2026, 10, 24)))
        assertEquals(d(2026, 10, 25), Plan.cycleStartFor(d(2026, 10, 25)))
        assertEquals(d(2026, 12, 25), Plan.cycleStartFor(d(2027, 1, 5)))
        assertEquals(d(2026, 11, 24), Plan.cycleEnd(d(2026, 10, 25)))
    }

    @Test
    fun emergencyFundPerCycle() {
        assertEquals(6_700L, Plan.budget(d(2026, 10, 25)).emergencyPod)
        assertEquals(6_700L, Plan.budget(d(2026, 11, 25)).emergencyPod)
        assertEquals(6_700L, Plan.budget(d(2026, 12, 25)).emergencyPod)
        assertEquals(64_700L, Plan.budget(d(2027, 1, 25)).emergencyPod)
        assertEquals(76_700L, Plan.budget(d(2027, 2, 25)).emergencyPod)
        assertEquals(76_700L, Plan.budget(d(2027, 8, 25)).emergencyPod)
    }

    @Test
    fun budgetBalancesAndIncomeIncludesRent() {
        val b = Plan.budget(d(2026, 10, 25))
        assertEquals(168_000L, b.income)
        assertEquals(43_300L, b.hdfcHold)
        assertEquals(104_700L, b.transferToJupiter)
        assertEquals(60_000L, b.debtPod)
        assertEquals(18_000L, b.sipPod)
        assertEquals(20_000L, b.jupiterMain)
        assertEquals(b.income, b.totalOut + b.emergencyPod)
        for (m in 0 until 24) {
            val c = d(2026, 9, 25).plusMonths(m.toLong())
            val x = Plan.budget(c)
            assertEquals(x.income, x.totalOut + x.emergencyPod)
        }
    }

    @Test
    fun debtSchedule() {
        assertEquals(0L, Plan.debtFor(d(2026, 9, 25)))
        assertEquals(60_000L, Plan.debtFor(d(2026, 10, 25)))
        assertEquals(60_000L, Plan.debtFor(d(2026, 11, 25)))
        assertEquals(60_000L, Plan.debtFor(d(2026, 12, 25)))
        assertEquals(20_000L, Plan.debtFor(d(2027, 1, 25)))
        assertEquals(0L, Plan.debtFor(d(2027, 2, 25)))
        assertEquals(Plan.LOAN_TOTAL, Plan.DEBT_SCHEDULE.sumOf { it.second })
        val debtItems = Plan.itemsBetween(d(2026, 9, 27), d(2027, 6, 30)).filter { it.kind == ItemKind.DEBT }
        assertEquals(listOf(d(2026, 10, 25), d(2026, 11, 25), d(2026, 12, 25), d(2027, 1, 25)), debtItems.map { it.date })
        assertTrue(debtItems.all { Plan.isDebtItem(it.id) })
    }

    @Test
    fun rentEndsAfterJan2027() {
        val rentPaid = Plan.itemsBetween(d(2026, 9, 27), d(2027, 12, 31)).filter { it.id.endsWith(":rent-out") }
        assertEquals(listOf(d(2026, 10, 7), d(2026, 11, 7), d(2026, 12, 7), d(2027, 1, 7)), rentPaid.map { it.date })
        assertTrue(rentPaid.all { it.amount == 18_000L })

        val rentIn = Plan.itemsBetween(d(2026, 9, 25), d(2027, 12, 31)).filter { it.id.endsWith(":rent-in") }
        assertEquals(d(2027, 1, 25), rentIn.last().date)
        assertEquals(0L, Plan.rentReceivedIn(d(2027, 2, 25)))
        assertEquals(0L, Plan.rentPaidIn(d(2027, 1, 25)))
        assertEquals(18_000L, Plan.rentPaidIn(d(2026, 12, 25)))
    }

    @Test
    fun cycleItemsHaveExpectedDates() {
        val items = Plan.items(d(2026, 10, 25))
        fun date(key: String) = items.first { it.id.endsWith(":$key") }.date
        assertEquals(d(2026, 10, 25), date("salary"))
        assertEquals(d(2026, 10, 25), date("utility"))
        assertEquals(d(2026, 11, 1), date("sip-move"))
        assertEquals(d(2026, 11, 1), date("sip-jupiter"))
        assertEquals(d(2026, 11, 3), date("term"))
        assertEquals(d(2026, 11, 7), date("rent-out"))
        assertEquals(d(2026, 11, 17), date("car-emi"))
        assertEquals(d(2026, 11, 24), date("review"))
        assertEquals(items.sortedBy { it.date }.map { it.date }, items.map { it.date })
        val salaryDay = items.first { it.kind == ItemKind.SALARY_DAY }
        assertTrue(salaryDay.steps.any { it.contains("₹1,04,700") })
    }

    @Test
    fun itemLookupAndTracking() {
        val item = Plan.itemById("2026-11-17:car-emi")
        assertNotNull(item)
        assertEquals(20_000L, item!!.amount)
        assertNull(Plan.itemById("garbage"))
        assertFalse(Plan.itemById("2026-09-25:utility")!!.tracked)
        assertTrue(Plan.itemById("2026-10-01:sip-move")!!.tracked)
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
