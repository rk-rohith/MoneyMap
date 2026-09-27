package com.moneymap.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the home-screen widget shows. */
data class WidgetSummary(
    val nextTitle: String,
    val nextDetail: String,
    val leftToSpend: Long,
    val perDay: Long,
    val othersOweMe: Long,
)

object Summary {
    fun widget(
        today: LocalDate,
        engine: PlanEngine,
        done: Set<String>,
        entries: List<EntryWithTxns>,
        spentThisCycle: Long,
    ): WidgetSummary {
        val next = engine.itemsBetween(maxOf(today, Plan.TRACK_START), today.plusDays(62))
            .firstOrNull { it.notifies && it.id !in done }
        val overdue = engine.itemsBetween(maxOf(Plan.TRACK_START, today.minusDays(62)), today.minusDays(1))
            .count { it.notifies && it.id !in done }
        val cycle = Plan.cycleStartFor(today)
        val left = engine.spendBudget(cycle) - spentThisCycle
        val daysLeft = (ChronoUnit.DAYS.between(today, Plan.cycleEnd(cycle)) + 1).coerceAtLeast(1)
        val (title, detail) = when {
            overdue > 0 -> "$overdue overdue" to (next?.let { "Next: ${it.title}" } ?: "Open the app to review")
            next == null -> "All caught up" to "Nothing due in the next two months"
            else -> {
                val amount = if (next.amount > 0) " ${formatInr(next.amount)}" else ""
                "${next.title}$amount" to when (ChronoUnit.DAYS.between(today, next.date)) {
                    0L -> "Today"
                    1L -> "Tomorrow"
                    else -> next.date.withDay()
                }
            }
        }
        return WidgetSummary(
            nextTitle = title,
            nextDetail = detail,
            leftToSpend = left,
            perDay = if (left > 0) left / daysLeft else 0,
            othersOweMe = Ledger.summary(entries).othersOweMe,
        )
    }
}
