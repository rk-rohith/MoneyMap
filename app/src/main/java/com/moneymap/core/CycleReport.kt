package com.moneymap.core

import java.time.LocalDate

data class ReportSection(val title: String, val rows: List<Pair<String, String>>)

/** The content of the monthly (cycle) report, independent of how it's drawn. */
object CycleSummary {
    fun title(cycleStart: LocalDate) =
        "${Plan.cycleLabel(cycleStart)} (${cycleStart.short()} – ${Plan.cycleEnd(cycleStart).short()} ${Plan.cycleEnd(cycleStart).year})"

    fun build(
        cycleStart: LocalDate,
        engine: PlanEngine,
        expenses: List<SpendRecord>,
        done: Set<String>,
        ledger: LedgerSummary,
        pods: List<PodBalance>,
        netWorth: NetWorth?,
        today: LocalDate,
    ): List<ReportSection> {
        val b = engine.budget(cycleStart)
        val spent = Reports.inCycle(expenses, cycleStart)
        val total = spent.sumOf { it.amount }
        val budget = engine.spendBudget(cycleStart)
        val items = engine.items(cycleStart).filter { it.notifies && it.kind != ItemKind.REVIEW }
        val due = items.filter { !it.date.isAfter(today) }
        val missed = due.filter { it.id !in done && it.tracked }
        val sections = mutableListOf<ReportSection>()

        sections += ReportSection("Income and plan", buildList {
            add("Salary" to formatInr(b.salary))
            b.otherIncome.forEach { add(it.label to formatInr(it.amount)) }
            add("Bills from ${Plan.HDFC}" to formatInr(b.salaryDayTotal + b.hdfcHold))
            if (b.debtPod > 0) add("Loan repayments" to formatInr(b.debtPod))
            if (b.podTotal > 0) add("Set aside in pods" to formatInr(b.podTotal))
            add("Spending budget" to formatInr(budget))
            add(Plan.EMERGENCY_POD to formatInr(b.emergencyPod))
        })
        sections += ReportSection("Spending", buildList {
            add("Spent" to "${formatInr(total)} of ${formatInr(budget)}")
            add(if (total <= budget) "Left" to formatInr(budget - total) else "Over budget" to formatInr(total - budget))
            add("Expenses logged" to spent.size.toString())
            Spending.breakdown(spent.map { it.category to it.amount }).forEach {
                add(it.category.label to "${formatInr(it.amount)} · ${(it.share * 100).toInt()}%")
            }
            spent.maxByOrNull { it.amount }?.let { add("Largest" to "${formatInr(it.amount)} ${it.note.ifBlank { it.category.label }}") }
        })
        sections += ReportSection("Payments", buildList {
            add("Done" to "${due.count { it.id in done }} of ${due.size} due so far")
            if (items.size > due.size) add("Still to come" to (items.size - due.size).toString())
            missed.forEach { add("Not ticked: ${it.title}" to "${formatInr(it.amount)} · ${it.date.short()}") }
        })
        sections += ReportSection("People", listOf(
            "Others owe you" to formatInr(ledger.othersOweMe),
            "You owe" to formatInr(ledger.iOwe),
        ))
        val nonEmpty = pods.filter { it.balance != 0L }
        if (nonEmpty.isNotEmpty()) {
            sections += ReportSection("Pods", nonEmpty.map { it.pod to formatInr(it.balance) } +
                ("Total" to formatInr(nonEmpty.sumOf { it.balance })))
        }
        netWorth?.let {
            sections += ReportSection("Net worth", listOf(
                "Assets" to formatInr(it.assets), "Liabilities" to formatInr(it.liabilities), "Net worth" to formatInr(it.total),
            ))
        }
        return sections
    }
}
