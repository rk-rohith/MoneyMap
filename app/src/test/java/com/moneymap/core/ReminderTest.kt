package com.moneymap.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderTest {
    private val now = LocalDateTime.of(2026, 9, 27, 12, 0)

    @Test
    fun paymentRemindersUseThreeSlots() {
        val r = ReminderPlanner.plan(now, emptySet(), emptyList())
        val car = r.filter { it.itemId == "2026-10-17:car-emi" }
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 16, 20, 0), LocalDateTime.of(2026, 10, 17, 9, 0), LocalDateTime.of(2026, 10, 17, 19, 0)),
            car.map { it.at },
        )
        assertTrue(r.all { it.at.isAfter(now) })
        assertTrue(r.none { it.itemId?.endsWith(":salary") == true })
        assertTrue(r.last().at.toLocalDate() <= now.toLocalDate().plusDays(ReminderPlanner.HORIZON_DAYS))
        assertEquals(r.size, r.map { it.key }.toSet().size)
    }

    @Test
    fun doneItemsAreSkipped() {
        val r = ReminderPlanner.plan(now, setOf("2026-10-17:car-emi"), emptyList())
        assertTrue(r.none { it.itemId == "2026-10-17:car-emi" })
    }

    @Test
    fun weeklyOnSundays() {
        val weekly = ReminderPlanner.plan(now, emptySet(), emptyList()).filter { it.type == ReminderType.WEEKLY }
        assertTrue(weekly.isNotEmpty())
        assertTrue(weekly.all { it.at.dayOfWeek == DayOfWeek.SUNDAY && it.at.hour == 10 })
    }

    @Test
    fun ledgerDueRemindersGroupedPerPerson() = runTest {
        val store = InMemoryLedgerStore()
        LedgerService(store).seedIfEmpty()
        val entries = store.allEntries()
        val due = ReminderPlanner.plan(now, emptySet(), entries).filter { it.type == ReminderType.LEDGER_DUE && it.person == "Friend" }
        val dates = due.map { it.at.toLocalDate() }
        assertEquals(LocalDate.of(2026, 11, 12), dates[0])
        assertEquals(LocalDate.of(2026, 11, 15), dates[1])
        assertEquals(LocalDate.of(2026, 11, 18), dates[2])
        assertEquals(LocalDate.of(2026, 11, 21), dates[3])
        assertEquals(dates.size, dates.toSet().size) // one per day even with two entries

        val text = ReminderPlanner.ledgerText("Friend", Direction.LENT,
            Ledger.outstandingFor(entries, "Friend", Direction.LENT), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 18))
        assertEquals("Friend still owes ₹50,298", text.title)
        assertTrue(text.text.startsWith("Overdue by 3 days"))
    }

    @Test
    fun reviewTextIncludesTotals() {
        val review = Plan.itemById("2026-10-24:review")!!
        val t = ReminderPlanner.paymentText(review, Slot.MORNING, LedgerSummary(88_874, 200_000))
        assertTrue(t.text.contains("₹88,874"))
        assertTrue(t.text.contains("₹2,00,000"))
        val car = ReminderPlanner.paymentText(Plan.itemById("2026-10-17:car-emi")!!, Slot.EVENING_BEFORE, null)
        assertEquals("Tomorrow: Car loan EMI ₹20,000", car.title)
    }

    @Test
    fun weeklyText() {
        val t = ReminderPlanner.weeklyText(7_700, LocalDate.of(2026, 10, 11))
        assertEquals("₹12,300 left to spend this cycle", t.title)
        assertFalse(t.text.isEmpty())
    }
}
