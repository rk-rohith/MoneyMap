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
    fun alarmWindowKeepsOnlyTheNextTwoWeeks() = runTest {
        val store = InMemoryLedgerStore()
        LedgerService(store).seedIfEmpty()
        val planned = ReminderPlanner.plan(now, emptySet(), store.allEntries())
        val window = ReminderPlanner.alarmWindow(planned, now)
        val end = now.plusDays(ReminderPlanner.ALARM_WINDOW_DAYS)
        assertTrue(window.isNotEmpty())
        assertTrue(window.size < planned.size)
        assertTrue(window.all { it.at.isAfter(now) && !it.at.isAfter(end) })
        assertEquals(planned.filter { !it.at.isAfter(end) }, window)
    }

    @Test
    fun alarmWindowIsCapped() {
        val many = (1..1_000).map {
            Reminder("k$it", now.plusMinutes(it.toLong()), ReminderType.WEEKLY, Slot.WEEKLY)
        }.shuffled()
        val window = ReminderPlanner.alarmWindow(many, now)
        assertEquals(ReminderPlanner.MAX_ALARMS, window.size)
        assertEquals((1..ReminderPlanner.MAX_ALARMS).map { "k$it" }, window.map { it.key })
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
        val review = DefaultPlan.engine.itemById("2026-10-24:review")!!
        val t = ReminderPlanner.paymentText(review, Slot.MORNING, LedgerSummary(88_874, 200_000))
        assertTrue(t.text.contains("₹88,874"))
        assertTrue(t.text.contains("₹2,00,000"))
        val car = ReminderPlanner.paymentText(DefaultPlan.engine.itemById("2026-10-17:car-emi")!!, Slot.EVENING_BEFORE, null)
        assertEquals("Tomorrow: Car loan EMI ₹20,000", car.title)
    }

    @Test
    fun weeklyText() {
        val t = ReminderPlanner.weeklyText(7_700, LocalDate.of(2026, 10, 11), 20_000)
        assertEquals("₹12,300 left to spend this cycle", t.title)
        assertFalse(t.text.isEmpty())
    }
}

class ReminderPrefsTest {
    private val now = LocalDateTime.of(2026, 9, 27, 12, 0)

    @Test
    fun customTimesAndSwitches() {
        val prefs = ReminderPrefs(eveningBeforeEnabled = false, morning = java.time.LocalTime.of(7, 30), weeklyEnabled = false)
        val r = ReminderPlanner.plan(now, emptySet(), emptyList(), prefs = prefs)
        val car = r.filter { it.itemId == "2026-10-17:car-emi" }
        assertEquals(listOf(LocalDateTime.of(2026, 10, 17, 7, 30), LocalDateTime.of(2026, 10, 17, 19, 0)), car.map { it.at })
        assertTrue(r.none { it.type == ReminderType.WEEKLY })
        assertTrue(ReminderPlanner.plan(now, emptySet(), emptyList(), prefs = ReminderPrefs(paymentsEnabled = false))
            .none { it.type == ReminderType.PAYMENT })
    }

    @Test
    fun ledgerSwitchAndTime() = runTest {
        val store = InMemoryLedgerStore()
        LedgerService(store).seedIfEmpty()
        val entries = store.allEntries()
        assertTrue(ReminderPlanner.plan(now, emptySet(), entries, prefs = ReminderPrefs(ledgerEnabled = false))
            .none { it.type == ReminderType.LEDGER_DUE })
        val due = ReminderPlanner.plan(now, emptySet(), entries, prefs = ReminderPrefs(ledger = java.time.LocalTime.of(18, 0)))
            .filter { it.type == ReminderType.LEDGER_DUE }
        assertTrue(due.isNotEmpty() && due.all { it.at.hour == 18 })
    }

    @Test
    fun shareMessageListsOpenItems() = runTest {
        val store = InMemoryLedgerStore()
        val service = LedgerService(store)
        service.seedIfEmpty()
        val friendLoan = store.allEntries().first { it.entry.seedKey == "friend-loan-emi" }
        service.recordSettlement(friendLoan.entry.id, 4_282, LocalDate.of(2026, 10, 1))
        val msg = Ledger.reminderMessage(store.allEntries(), "friend")!!
        assertTrue(msg.startsWith("Hi Friend, a gentle reminder: ₹46,016 is pending, due 15 Nov 2026."))
        assertTrue(msg.contains("• 2 loan EMIs: ₹40,000 (of ₹44,282)"))
        assertTrue(msg.contains("• 2 mobile EMIs: ₹6,016"))
        assertEquals(null, Ledger.reminderMessage(store.allEntries(), "Samrat"))
        assertEquals(null, Ledger.reminderMessage(store.allEntries(), "Lender"))
    }
}
