package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WealthTest {
    private val d = LocalDate.of(2026, 10, 10)

    @Test
    fun investmentValueFromNavOrManualValue() {
        val sip = Investment("1", "Index fund", monthlySip = 5_000, sipDay = 5,
            txns = listOf(InvestmentTxn(LocalDate.of(2026, 8, 5), 5_000, 50.0), InvestmentTxn(LocalDate.of(2026, 9, 5), 5_000, 48.0)),
            nav = 110.0)
        assertEquals(10_000, sip.invested)
        assertEquals(10_780, sip.value)
        assertEquals(780, sip.gain)
        assertEquals(7.8, sip.gainPercent!!, 0.001)
        val fd = Investment("2", "FD", InvestmentKind.FD, txns = listOf(InvestmentTxn(d, 100_000)), currentValue = 103_000)
        assertEquals(103_000, fd.value)
        assertEquals(InvestmentSummary(110_000, 113_780), Investments.summary(listOf(sip, fd)))
        assertEquals(10_000, Investment("3", "Cash", txns = listOf(InvestmentTxn(d, 10_000))).value)
    }

    @Test
    fun sipDueUntilThisMonthsInstalmentIsRecorded() {
        val sip = Investment("1", "Fund", monthlySip = 5_000, sipDay = 5, txns = listOf(InvestmentTxn(LocalDate.of(2026, 9, 5), 5_000)))
        assertFalse(Investments.sipDue(sip, LocalDate.of(2026, 10, 4)))
        assertTrue(Investments.sipDue(sip, d))
        val paid = Investments.addSip(sip, LocalDate.of(2026, 10, 6))
        assertFalse(Investments.sipDue(paid, d))
        assertEquals(10_000, paid.invested)
        assertFalse(Investments.sipDue(Investment("2", "FD", monthlySip = 0), d))
    }

    @Test
    fun netWorthAndHistory() {
        val loan = Loan("car", "Car", 100_000, 0.0, 10_000, LocalDate.of(2026, 9, 1))
        val nw = NetWorthCalc.compute(
            listOf(PodBalance("Emergency pod", 50_000, emptyList()), PodBalance("Debt pod", 20_000, emptyList())),
            LedgerSummary(othersOweMe = 15_000, iOwe = 40_000),
            listOf(Investment("1", "FD", txns = listOf(InvestmentTxn(d, 30_000)))),
            listOf(loan), d,
        )
        assertEquals(115_000, nw.assets)
        assertEquals(40_000 + 80_000, nw.liabilities) // two EMIs paid by 10 Oct
        assertEquals(-5_000, nw.total)

        var h = NetWorthCalc.record(emptyList(), LocalDate.of(2026, 8, 20), 100)
        h = NetWorthCalc.record(h, LocalDate.of(2026, 9, 3), 150)
        h = NetWorthCalc.record(h, LocalDate.of(2026, 9, 28), 200) // replaces September
        h = NetWorthCalc.record(h, LocalDate.of(2026, 10, 1), 260)
        assertEquals(listOf(100L, 200L, 260L), h.map { it.total })
        assertEquals(LocalDate.of(2026, 9, 1), h[1].month)
        assertEquals(160L, NetWorthCalc.changeOver(h, 2))
        assertNull(NetWorthCalc.changeOver(h, 6))
    }
}
