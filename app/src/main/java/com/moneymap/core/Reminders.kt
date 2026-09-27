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

/** Decides which reminders exist and what they say. Pure logic so it can be unit tested. */
object ReminderPlanner {
    const val HORIZON_DAYS = 92L
    val EVENING_BEFORE_TIME: LocalTime = LocalTime.of(20, 0)
    val MORNING_TIME: LocalTime = LocalTime.of(9, 0)
    val EVENING_TIME: LocalTime = LocalTime.of(19, 0)
    val LEDGER_TIME: LocalTime = LocalTime.of(9, 30)
    val WEEKLY_TIME: LocalTime = LocalTime.of(10, 0)
    const val DUE_REPEAT_DAYS = 3L

    fun plan(
        now: LocalDateTime,
        done: Set<String>,
        entries: List<EntryWithTxns>,
        engine: PlanEngine = DefaultPlan.engine,
    ): List<Reminder> {
        val today = now.toLocalDate()
        val horizon = today.plusDays(HORIZON_DAYS)
        val out = mutableListOf<Reminder>()

        // Payments: 8 pm the evening before, 9 am on the day, 7 pm on the day.
        val from = maxOf(today, Plan.TRACK_START)
        for (item in engine.itemsBetween(from, horizon)) {
            if (!item.notifies || item.id in done) continue
            listOf(
                Slot.EVENING_BEFORE to item.date.minusDays(1).atTime(EVENING_BEFORE_TIME),
                Slot.MORNING to item.date.atTime(MORNING_TIME),
                Slot.EVENING to item.date.atTime(EVENING_TIME),
            ).forEach { (slot, at) ->
                if (at.isAfter(now)) {
                    out += Reminder("pay:${item.id}:$slot", at, ReminderType.PAYMENT, slot, itemId = item.id)
                }
            }
        }

        // Ledger: 3 days before, on the due date, then every 3 days while open.
        entries.filter { it.entry.dueDate != null && it.status != EntryStatus.SETTLED }
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
                    val at = date.atTime(LEDGER_TIME)
                    if (at.isAfter(now)) {
                        out += Reminder("due:$personKey:$direction:$due:$date", at, ReminderType.LEDGER_DUE, slot,
                            person = person, direction = direction, dueDate = due)
                    }
                }
            }

        // Weekly: Sundays at 10 am.
        var sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        while (!sunday.isAfter(horizon)) {
            val at = sunday.atTime(WEEKLY_TIME)
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
