package com.moneymap.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A credit (+) or debit (−) to a pod. [linkKey] ties automatic moves to the tick or return that made them. */
data class PodMove(
    val id: Long = 0,
    val pod: String,
    val amount: Long,
    val date: LocalDate,
    val note: String = "",
    val linkKey: String? = null,
)

data class Goal(
    val id: Long = 0,
    val name: String,
    val target: Long,
    val targetDate: LocalDate? = null,
    val pod: String,
    val createdAt: Long = 0,
)

data class PodBalance(val pod: String, val balance: Long, val moves: List<PodMove>)

data class GoalProgress(
    val goal: Goal,
    val saved: Long,
    val remaining: Long,
    val progress: Float,
    /** Cycles left until the target date (at least 1), or null without a date. */
    val cyclesLeft: Long?,
    /** How much to put aside each cycle to hit the target on time. */
    val perCycle: Long?,
) {
    val reached: Boolean get() = remaining <= 0
}

object Pods {
    val DEFAULT_PODS: List<String> get() = listOf(Plan.EMERGENCY_POD, Plan.DEBT_POD, ReturnPod.SRI_LANKA.label)

    fun linkForTxn(txnId: Long) = "txn:$txnId"

    /**
     * Automatic pod moves when a plan item is ticked:
     * salary-day routine credits every pod in the split; a debt instalment or a pod → Jupiter main move debits its pod.
     */
    fun movesForTick(item: PlanItem, budget: CycleBudget): List<PodMove> = when (item.kind) {
        ItemKind.SALARY_DAY -> budget.podCredits.map {
            PodMove(pod = it.label, amount = it.amount, date = item.date, note = "Salary-day split", linkKey = item.id)
        }
        ItemKind.DEBT, ItemKind.TRANSFER ->
            if (item.pod.isBlank()) emptyList()
            else listOf(PodMove(pod = item.pod, amount = -item.amount, date = item.date, note = item.title, linkKey = item.id))
        else -> emptyList()
    }

    /** Money coming back from someone goes into its return pod (the spending account's main balance is not a pod). */
    fun moveForReturn(pod: ReturnPod, amount: Long, date: LocalDate, person: String, txnId: Long): PodMove? =
        if (pod == ReturnPod.JUPITER_MAIN || amount <= 0) null
        else PodMove(pod = pod.label, amount = amount, date = date, note = "Returned by $person", linkKey = linkForTxn(txnId))

    /** Balances for every known pod: defaults, plan pods, goal pods and anything with moves. */
    fun balances(moves: List<PodMove>, extraPods: Collection<String> = emptyList()): List<PodBalance> {
        val names = LinkedHashSet<String>()
        names += DEFAULT_PODS
        names += extraPods.filter { it.isNotBlank() }
        names += moves.map { it.pod }
        val byPod = moves.groupBy { it.pod }
        return names.map { name ->
            val list = byPod[name].orEmpty().sortedWith(compareByDescending<PodMove> { it.date }.thenByDescending { it.id })
            PodBalance(name, list.sumOf { it.amount }, list)
        }
    }

    fun balanceOf(moves: List<PodMove>, pod: String): Long = moves.filter { it.pod == pod }.sumOf { it.amount }

    fun planPods(engine: PlanEngine, cycleStart: LocalDate): List<String> =
        engine.budget(cycleStart).pods.map { it.label }.distinct()

    fun goalProgress(goal: Goal, saved: Long, today: LocalDate): GoalProgress {
        val remaining = (goal.target - saved).coerceAtLeast(0)
        val cyclesLeft = goal.targetDate?.let { date ->
            val from = Plan.cycleStartFor(today)
            val to = Plan.cycleStartFor(date)
            (ChronoUnit.MONTHS.between(from, to) + 1).coerceAtLeast(1)
        }
        val perCycle = cyclesLeft?.let { if (remaining == 0L) 0L else (remaining + it - 1) / it }
        val progress = if (goal.target <= 0) 1f else (saved.toFloat() / goal.target).coerceIn(0f, 1f)
        return GoalProgress(goal, saved, remaining, progress, cyclesLeft, perCycle)
    }
}

enum class ExpenseCategory(val label: String) {
    FOOD("Food & dining"),
    GROCERIES("Groceries"),
    TRANSPORT("Fuel & transport"),
    SHOPPING("Shopping"),
    BILLS("Bills & recharges"),
    HEALTH("Health"),
    FUN("Entertainment"),
    OTHER("Other");

    companion object {
        fun parse(name: String?): ExpenseCategory = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

data class CategoryTotal(val category: ExpenseCategory, val amount: Long, val share: Float)

object Spending {
    /** Totals per category, largest first. [share] is relative to the total spent. */
    fun breakdown(expenses: List<Pair<ExpenseCategory, Long>>): List<CategoryTotal> {
        val total = expenses.sumOf { it.second }
        if (total <= 0) return emptyList()
        return expenses.groupBy { it.first }
            .map { (cat, list) -> list.sumOf { it.second }.let { CategoryTotal(cat, it, it.toFloat() / total) } }
            .sortedByDescending { it.amount }
    }
}
