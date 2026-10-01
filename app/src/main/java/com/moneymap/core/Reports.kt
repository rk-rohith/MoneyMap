package com.moneymap.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One logged expense, as the reports see it. */
data class SpendRecord(val date: LocalDate, val amount: Long, val category: ExpenseCategory, val note: String = "")

data class CycleReport(
    val cycleStart: LocalDate,
    val spent: Long,
    val budget: Long,
    val byCategory: Map<ExpenseCategory, Long>,
) {
    val over: Boolean get() = spent > budget
}

data class CategoryTrend(val category: ExpenseCategory, val thisCycle: Long, val average: Long) {
    /** Change against the average of earlier cycles, or null when there is no history. */
    val change: Float? get() = if (average <= 0) null else (thisCycle - average).toFloat() / average
}

data class CategoryBudgetStatus(val category: ExpenseCategory, val spent: Long, val budget: Long) {
    val ratio: Float get() = if (budget <= 0) 0f else spent.toFloat() / budget
    val left: Long get() = budget - spent
}

/** A payment that looks like it repeats every month (subscription, rent paid by hand, a gym…). */
data class RecurringGuess(
    val title: String,
    val category: ExpenseCategory,
    val typicalAmount: Long,
    val months: Int,
    val lastDate: LocalDate,
) {
    /** Day of the month it usually comes on, clamped to 1–28 so it exists in every month. */
    val day: Int get() = lastDate.dayOfMonth.coerceIn(1, 28)
}

object Reports {
    fun inCycle(records: List<SpendRecord>, cycleStart: LocalDate): List<SpendRecord> {
        val end = Plan.cycleEnd(cycleStart)
        return records.filter { !it.date.isBefore(cycleStart) && !it.date.isAfter(end) }
    }

    /** The last [count] cycles up to and including the one containing [today], oldest first. */
    fun cycles(records: List<SpendRecord>, engine: PlanEngine, today: LocalDate, count: Int = 6): List<CycleReport> {
        val current = Plan.cycleStartFor(today)
        return (count - 1 downTo 0).map { back ->
            val start = current.minusMonths(back.toLong())
            val list = inCycle(records, start)
            CycleReport(
                cycleStart = start,
                spent = list.sumOf { it.amount },
                budget = engine.spendBudget(start),
                byCategory = list.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } },
            )
        }
    }

    /**
     * Each category's spending this cycle against its average over the earlier cycles in [reports]
     * (only cycles that had any spending count, so a new install isn't compared with empty months).
     */
    fun trends(reports: List<CycleReport>): List<CategoryTrend> {
        val current = reports.lastOrNull() ?: return emptyList()
        val earlier = reports.dropLast(1).filter { it.spent > 0 }
        return ExpenseCategory.entries.mapNotNull { c ->
            val now = current.byCategory[c] ?: 0
            val avg = if (earlier.isEmpty()) 0 else earlier.sumOf { it.byCategory[c] ?: 0 } / earlier.size
            if (now == 0L && avg == 0L) null else CategoryTrend(c, now, avg)
        }.sortedByDescending { it.thisCycle }
    }

    fun budgetStatus(records: List<SpendRecord>, cycleStart: LocalDate, budgets: Map<ExpenseCategory, Long>): List<CategoryBudgetStatus> {
        val list = inCycle(records, cycleStart)
        return budgets.filterValues { it > 0 }.map { (c, b) ->
            CategoryBudgetStatus(c, list.filter { it.category == c }.sumOf { it.amount }, b)
        }.sortedByDescending { it.ratio }
    }

    /** 80 or 100 when adding [added] pushes [category] past that share of its budget, else null. */
    fun budgetAlert(spentBefore: Long, added: Long, budget: Long): Int? {
        if (budget <= 0 || added <= 0) return null
        val after = spentBefore + added
        return when {
            spentBefore <= budget && after > budget -> 100
            spentBefore * 10 < budget * 8 && after * 10 >= budget * 8 -> 80
            else -> null
        }
    }

    /**
     * Payments with the same note in at least three different months of the last six, at a steady amount
     * (each within 20% of the median). [known] titles (already regular plan items) are left out.
     */
    fun recurring(records: List<SpendRecord>, today: LocalDate, known: Collection<String> = emptyList()): List<RecurringGuess> {
        val since = today.minusMonths(6)
        val knownKeys = known.map(::key).toSet()
        return records.filter { it.note.isNotBlank() && it.date.isAfter(since) && !it.date.isAfter(today) }
            .groupBy { key(it.note) }
            .filterKeys { it.length >= 3 && it !in knownKeys }
            .mapNotNull { (_, list) ->
                val months = list.map { it.date.withDayOfMonth(1) }.distinct().size
                if (months < 3) return@mapNotNull null
                val median = list.map { it.amount }.sorted().let { it[it.size / 2] }
                if (list.any { abs(it.amount - median) * 5 > median }) return@mapNotNull null
                val last = list.maxBy { it.date }
                // Stale: nothing in the last ~45 days means it has probably stopped.
                if (ChronoUnit.DAYS.between(last.date, today) > 45) return@mapNotNull null
                RecurringGuess(last.note.trim(), last.category, median, months, last.date)
            }
            .sortedByDescending { it.typicalAmount }
    }

    private fun key(note: String) = note.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()
}
