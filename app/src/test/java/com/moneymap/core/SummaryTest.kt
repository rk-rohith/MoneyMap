package com.moneymap.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SummaryTest {
    @Test
    fun widgetShowsNextItemBudgetAndOwed() = runTest {
        val store = InMemoryLedgerStore()
        LedgerService(store).seedIfEmpty()
        val today = LocalDate.of(2026, 10, 16)
        val w = Summary.widget(today, DefaultPlan.engine, done = setOf("2026-10-01:move-sip-jupiter",
            "2026-10-01:sip-jupiter", "2026-10-01:sip-hdfc", "2026-10-03:term", "2026-10-07:rent-out"),
            entries = store.allEntries(), spentThisCycle = 7_700)
        assertEquals("Car loan EMI ₹20,000", w.nextTitle)
        assertEquals("Tomorrow", w.nextDetail)
        assertEquals(12_300L, w.leftToSpend)
        assertEquals(12_300L / 9, w.perDay)
        assertEquals(88_874L, w.othersOweMe)
    }

    @Test
    fun overdueTakesPriority() {
        val w = Summary.widget(LocalDate.of(2026, 10, 16), DefaultPlan.engine, emptySet(), emptyList(), 0)
        assertEquals("5 overdue", w.nextTitle)
        assertEquals("Next: Car loan EMI", w.nextDetail)
    }
}
