package com.moneymap.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class ReminderType { PAYMENT, LEDGER_DUE, WEEKLY }

enum class Slot { EVENING_BEFORE, MORNING, EVENING, BEFORE_DUE, DUE_DAY, AFTER_DUE, WEEKLY, SNOOZE }

data class Reminder(
    val key: String,
    val at: LocalDateTime,
    val type: ReminderType,
    val slot: Slot,
    val itemId: String? = null,
    val person: String? = null,
    val direction: Direction? = null,
    val dueDate: LocalDate? = null,
)

data class NotificationText(val title: String, val text: String)

/** User-adjustable reminder times and switches. Defaults match the original plan. */
data class ReminderPrefs(
    val paymentsEnabled: Boolean = true,
    val eveningBeforeEnabled: Boolean = true,
    val eveningBefore: LocalTime = LocalTime.of(20, 0),
    val morningEnabled: Boolean = true,
    val morning: LocalTime = LocalTime.of(9, 0),
    val eveningEnabled: Boolean = true,
    val evening: LocalTime = LocalTime.of(19, 0),
    val ledgerEnabled: Boolean = true,
    val ledger: LocalTime = LocalTime.of(9, 30),
    val weeklyEnabled: Boolean = true,
    val weekly: LocalTime = LocalTime.of(10, 0),
)

/** Decides which reminders exist and what they say. Pure logic so it can be unit tested. */
object ReminderPlanner {
    const val HORIZON_DAYS = 92L
    const val DUE_REPEAT_DAYS = 3L

    fun plan(
        now: LocalDateTime,
        done: Set<String>,
        entries: List<EntryWithTxns>,
        engine: PlanEngine = DefaultPlan.engine,
        prefs: ReminderPrefs = ReminderPrefs(),
    ): List<Reminder> {
        val today = now.toLocalDate()
        val horizon = today.plusDays(HORIZON_DAYS)
        val out = mutableListOf<Reminder>()

        // Payments: 8 pm the evening before, 9 am on the day, 7 pm on the day.
        val from = maxOf(today, Plan.TRACK_START)
        val slots = buildList {
            if (prefs.eveningBeforeEnabled) add(Slot.EVENING_BEFORE)
            if (prefs.morningEnabled) add(Slot.MORNING)
            if (prefs.eveningEnabled) add(Slot.EVENING)
        }
        for (item in if (prefs.paymentsEnabled) engine.itemsBetween(from, horizon) else emptyList()) {
            if (!item.notifies || item.id in done) continue
            slots.map { slot ->
                slot to when (slot) {
                    Slot.EVENING_BEFORE -> item.date.minusDays(1).atTime(prefs.eveningBefore)
                    Slot.MORNING -> item.date.atTime(prefs.morning)
                    else -> item.date.atTime(prefs.evening)
                }
            }.forEach { (slot, at) ->
                if (at.isAfter(now)) {
                    out += Reminder("pay:${item.id}:$slot", at, ReminderType.PAYMENT, slot, itemId = item.id)
                }
            }
        }

        // Ledger: 3 days before, on the due date, then every 3 days while open.
        entries.filter { prefs.ledgerEnabled && it.entry.dueDate != null && it.status != EntryStatus.SETTLED }
            .groupBy { Triple(Ledger.personKey(it.entry.person), it.entry.direction, it.entry.dueDate!!) }
            .forEach { (k, list) ->
                val (personKey, direction, due) = k
                val person = list.first().entry.person.trim()
                val dates = mutableListOf(due.minusDays(3) to Slot.BEFORE_DUE, due to Slot.DUE_DAY)
                var d = due.plusDays(DUE_REPEAT_DAYS)
                // Always keep the next overdue nudge even if the due date is long past.
                if (d.isBefore(today)) {
                    val periods = ChronoUnit.DAYS.between(d, today) / DUE_REPEAT_DAYS
                    d = d.plusDays(periods * DUE_REPEAT_DAYS)
                }
                while (!d.isAfter(horizon)) {
                    dates += d to Slot.AFTER_DUE
                    d = d.plusDays(DUE_REPEAT_DAYS)
                }
                for ((date, slot) in dates) {
                    val at = date.atTime(prefs.ledger)
                    if (at.isAfter(now)) {
                        out += Reminder("due:$personKey:$direction:$due:$date", at, ReminderType.LEDGER_DUE, slot,
                            person = person, direction = direction, dueDate = due)
                    }
                }
            }

        // Weekly: Sundays at 10 am.
        var sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        while (prefs.weeklyEnabled && !sunday.isAfter(horizon)) {
            val at = sunday.atTime(prefs.weekly)
            if (at.isAfter(now)) out += Reminder("weekly:$sunday", at, ReminderType.WEEKLY, Slot.WEEKLY)
            sunday = sunday.plusWeeks(1)
        }
        return out.sortedBy { it.at }
    }

    fun paymentText(item: PlanItem, slot: Slot, summary: LedgerSummary?): NotificationText {
        val amount = if (item.amount > 0) " ${formatInr(item.amount)}" else ""
        val prefix = when (slot) {
            Slot.EVENING_BEFORE -> "Tomorrow"
            Slot.EVENING -> "Not done yet"
            Slot.SNOOZE -> "Reminder"
            else -> "Today"
        }
        val body = when {
            item.kind == ItemKind.REVIEW && summary != null ->
                "Others owe me ${formatInr(summary.othersOweMe)} · I owe ${formatInr(summary.iOwe)}. " +
                    "Check spending and pods."
            item.kind == ItemKind.SALARY_DAY -> item.steps.joinToString("\n")
            else -> listOf(item.account, item.detail, item.date.withDay()).filter { it.isNotBlank() }.joinToString(" · ")
        }
        return NotificationText("$prefix: ${item.title}$amount", body)
    }

    fun ledgerText(person: String, direction: Direction, outstanding: Long, due: LocalDate, today: LocalDate): NotificationText {
        val title = if (direction == Direction.LENT) "$person still owes ${formatInr(outstanding)}"
        else "You still owe $person ${formatInr(outstanding)}"
        val days = ChronoUnit.DAYS.between(today, due)
        val text = when {
            days > 1 -> "Due in $days days (${due.long()})"
            days == 1L -> "Due tomorrow (${due.long()})"
            days == 0L -> "Due today"
            else -> "Overdue by ${-days} day${if (days == -1L) "" else "s"} (was due ${due.long()})"
        }
        return NotificationText(title, text)
    }

    fun weeklyText(spent: Long, today: LocalDate, budget: Long): NotificationText {
        val cycle = Plan.cycleStartFor(today)
        val left = budget - spent
        val daysLeft = ChronoUnit.DAYS.between(today, Plan.cycleEnd(cycle)) + 1
        val perDay = if (left > 0) left / daysLeft else 0
        val title = if (left >= 0) "${formatInr(left)} left to spend this cycle"
        else "Over budget by ${formatInr(-left)} this cycle"
        return NotificationText(title,
            "Spent ${formatInr(spent)} of ${formatInr(budget)} · $daysLeft days left · ${formatInr(perDay)}/day")
    }
}
