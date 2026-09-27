package com.moneymap.core

import java.time.LocalDate

/** Cycle rules and fixed facts that don't change when the plan is edited. */
object Plan {
    /** Salary day. A cycle runs from the 25th to the 24th of the next month. */
    const val SALARY_DAY = 25
    const val REVIEW_DAY = 24

    const val LOAN_TOTAL = 200_000L
    const val LENDER_NAME = "Lender"

    /** Nothing dated before this is ever treated as overdue. */
    val TRACK_START: LocalDate = LocalDate.of(2026, 9, 27)

    const val HDFC = "HDFC"
    const val JUPITER = "Jupiter"

    const val EMERGENCY_POD = "Emergency pod"
    const val DEBT_POD = "Debt pod"

    fun cycleStartFor(date: LocalDate): LocalDate =
        if (date.dayOfMonth >= SALARY_DAY) date.withDayOfMonth(SALARY_DAY)
        else date.minusMonths(1).withDayOfMonth(SALARY_DAY)

    fun cycleEnd(cycleStart: LocalDate): LocalDate = cycleStart.plusMonths(1).withDayOfMonth(REVIEW_DAY)

    fun cycleLabel(cycleStart: LocalDate): String = "${cycleStart.monthYear()} cycle"

    /** Date on which something due on [day] of the month falls inside the cycle starting [cycleStart]. */
    fun occurrence(cycleStart: LocalDate, day: Int): LocalDate {
        val month = if (day >= SALARY_DAY) cycleStart else cycleStart.plusMonths(1)
        return month.withDayOfMonth(day.coerceIn(1, month.lengthOfMonth()))
    }

    fun isDebtItem(id: String): Boolean = Regex(":debt\\d*$").containsMatchIn(id)
}

enum class Flow(val label: String) {
    INCOME("Income"),
    HDFC("Paid from HDFC"),
    POD("Paid from a Jupiter pod"),
}

/** A fixed monthly item: income, a bill/autopay from HDFC, or a payment funded by a Jupiter pod. */
data class RecurringItem(
    val id: String,
    val title: String,
    val amount: Long,
    val day: Int,
    val flow: Flow,
    val account: String,
    val autopay: Boolean = false,
    /** Pod that holds the money until the payment (only for [Flow.POD]). */
    val pod: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
) {
    fun occursOn(date: LocalDate): Boolean =
        (startDate == null || !date.isBefore(startDate)) && (endDate == null || !date.isAfter(endDate))
}

data class PlanConfig(
    val salary: Long,
    val spendBudget: Long,
    val items: List<RecurringItem>,
)

/** A plan that applies from the cycle starting [from] until the next version. */
data class PlanVersion(val from: LocalDate, val config: PlanConfig)

data class DebtInstalment(val date: LocalDate, val amount: Long)

data class PlanSettings(
    val versions: List<PlanVersion>,
    val debt: List<DebtInstalment>,
) {
    init {
        require(versions.isNotEmpty()) { "A plan needs at least one version" }
    }

    val sortedVersions: List<PlanVersion> get() = versions.sortedBy { it.from }

    /** The plan in force for the cycle starting [cycleStart]. Cycles before the first version use the first. */
    fun configFor(cycleStart: LocalDate): PlanConfig =
        sortedVersions.lastOrNull { !it.from.isAfter(cycleStart) }?.config ?: sortedVersions.first().config

    /** Adds or replaces the version starting at [from]. Later versions are kept. */
    fun withVersion(from: LocalDate, config: PlanConfig): PlanSettings =
        copy(versions = versions.filter { it.from != from } + PlanVersion(from, config))

    fun withoutVersion(from: LocalDate): PlanSettings {
        val left = versions.filter { it.from != from }
        return if (left.isEmpty()) this else copy(versions = left)
    }
}

/** The plan as first set up. Editing in the app stores new versions on top of this. */
object DefaultPlan {
    val BASE_CYCLE: LocalDate = LocalDate.of(2026, 9, 25)

    val items: List<RecurringItem> = listOf(
        RecurringItem("rent-in", "Rent received", 8_000, 25, Flow.INCOME, Plan.HDFC,
            endDate = LocalDate.of(2027, 1, 25)),
        RecurringItem("utility", "House utility", 20_000, 25, Flow.HDFC, Plan.HDFC),
        RecurringItem("sip-jupiter", "Jupiter SIP autopay", 18_000, 1, Flow.POD, Plan.JUPITER,
            autopay = true, pod = "SIP pod"),
        RecurringItem("sip-hdfc", "HDFC SIP autopay", 2_000, 1, Flow.HDFC, Plan.HDFC, autopay = true),
        RecurringItem("term", "Term insurance", 3_300, 3, Flow.HDFC, Plan.HDFC, autopay = true),
        RecurringItem("rent-out", "Pay rent", 18_000, 7, Flow.HDFC, Plan.HDFC,
            endDate = LocalDate.of(2027, 1, 7)),
        RecurringItem("car-emi", "Car loan EMI", 20_000, 17, Flow.HDFC, Plan.HDFC, autopay = true),
    )

    val config = PlanConfig(salary = 160_000, spendBudget = 20_000, items = items)

    val debt: List<DebtInstalment> = listOf(
        DebtInstalment(LocalDate.of(2026, 10, 25), 60_000),
        DebtInstalment(LocalDate.of(2026, 11, 25), 60_000),
        DebtInstalment(LocalDate.of(2026, 12, 25), 60_000),
        DebtInstalment(LocalDate.of(2027, 1, 25), 20_000),
    )

    val settings = PlanSettings(listOf(PlanVersion(BASE_CYCLE, config)), debt)
    val engine = PlanEngine(settings)
}

enum class ItemKind(val order: Int) {
    INCOME(0), SALARY_DAY(1), BILL(2), DEBT(3), TRANSFER(4), AUTOPAY(5), REVIEW(6)
}

data class PlanItem(
    val id: String,
    val date: LocalDate,
    val title: String,
    val amount: Long,
    val account: String,
    val kind: ItemKind,
    val detail: String = "",
    val steps: List<String> = emptyList(),
    /** Pod that money leaves when this is ticked (debt instalments, pod → Jupiter main moves). */
    val pod: String = "",
) {
    /** Income is informational; everything else gets reminders. */
    val notifies: Boolean get() = kind != ItemKind.INCOME
    val tracked: Boolean get() = !date.isBefore(Plan.TRACK_START)
}

data class Line(val label: String, val amount: Long)

data class CycleBudget(
    val cycleStart: LocalDate,
    val salary: Long,
    val otherIncome: List<Line>,
    val income: Long,
    /** HDFC payments made on salary day (e.g. house utility). */
    val salaryDayPayments: List<Line>,
    /** HDFC bills later in the cycle; this money stays in HDFC. */
    val hdfcBills: List<Line>,
    val hdfcHold: Long,
    val transferToJupiter: Long,
    val debtPod: Long,
    /** Jupiter pods that fund later payments, e.g. SIP pod. */
    val pods: List<Line>,
    val jupiterMain: Long,
    val emergencyPod: Long,
) {
    val salaryDayTotal: Long get() = salaryDayPayments.sumOf { it.amount }
    val podTotal: Long get() = pods.sumOf { it.amount }
    fun pod(name: String): Long = pods.filter { it.label == name }.sumOf { it.amount }
    val totalOut: Long get() = salaryDayTotal + hdfcHold + debtPod + podTotal + jupiterMain

    /** Money that goes into each pod when the salary-day routine is done. */
    val podCredits: List<Line>
        get() = buildList {
            if (debtPod > 0) add(Line(Plan.DEBT_POD, debtPod))
            if (emergencyPod != 0L) add(Line(Plan.EMERGENCY_POD, emergencyPod))
            pods.groupBy { it.label }.forEach { (name, lines) -> add(Line(name, lines.sumOf { it.amount })) }
        }
}

/** Turns [PlanSettings] into cycles, budgets and dated items. */
class PlanEngine(val settings: PlanSettings) {

    fun config(cycleStart: LocalDate): PlanConfig = settings.configFor(cycleStart)

    fun spendBudget(cycleStart: LocalDate): Long = config(cycleStart).spendBudget

    fun debtFor(cycleStart: LocalDate): List<DebtInstalment> {
        val end = Plan.cycleEnd(cycleStart)
        return settings.debt.filter { !it.date.isBefore(cycleStart) && !it.date.isAfter(end) }.sortedBy { it.date }
    }

    private fun occurrences(cycleStart: LocalDate, cfg: PlanConfig): List<Pair<RecurringItem, LocalDate>> =
        cfg.items.mapNotNull { item ->
            val date = Plan.occurrence(cycleStart, item.day)
            if (item.amount > 0 && item.occursOn(date)) item to date else null
        }

    fun budget(cycleStart: LocalDate): CycleBudget = budget(cycleStart, config(cycleStart))

    /** Budget for [cycleStart] using [cfg] (lets the settings screen preview unsaved edits). */
    fun budget(cycleStart: LocalDate, cfg: PlanConfig): CycleBudget {
        val occ = occurrences(cycleStart, cfg)
        val otherIncome = occ.filter { it.first.flow == Flow.INCOME }.map { Line(it.first.title, it.first.amount) }
        val hdfc = occ.filter { it.first.flow == Flow.HDFC }
        val salaryDay = hdfc.filter { it.second == cycleStart }.map { Line(it.first.title, it.first.amount) }
        val bills = hdfc.filter { it.second != cycleStart }.map { Line(it.first.title, it.first.amount) }
        val pods = occ.filter { it.first.flow == Flow.POD }
            .map { Line(it.first.pod.ifBlank { "${it.first.title} pod" }, it.first.amount) }
        val income = cfg.salary + otherIncome.sumOf { it.amount }
        val hold = bills.sumOf { it.amount }
        val toJupiter = income - salaryDay.sumOf { it.amount } - hold
        val debt = debtFor(cycleStart).sumOf { it.amount }
        val emergency = toJupiter - debt - pods.sumOf { it.amount } - cfg.spendBudget
        return CycleBudget(
            cycleStart = cycleStart,
            salary = cfg.salary,
            otherIncome = otherIncome,
            income = income,
            salaryDayPayments = salaryDay,
            hdfcBills = bills,
            hdfcHold = hold,
            transferToJupiter = toJupiter,
            debtPod = debt,
            pods = pods,
            jupiterMain = cfg.spendBudget,
            emergencyPod = emergency,
        )
    }

    /** All plan items in the cycle that starts on [cycleStart], sorted by date. */
    fun items(cycleStart: LocalDate): List<PlanItem> {
        val cfg = config(cycleStart)
        val b = budget(cycleStart, cfg)
        val sd = cycleStart
        val out = mutableListOf<PlanItem>()

        out += PlanItem(id(sd, "salary"), sd, "Salary credited", cfg.salary, Plan.HDFC, ItemKind.INCOME,
            detail = "Salary arrives in HDFC")
        out += PlanItem(
            id(sd, "salary-day"), sd, "Salary-day routine", b.transferToJupiter, "${Plan.HDFC} → ${Plan.JUPITER}",
            ItemKind.SALARY_DAY,
            detail = "Send ${formatInr(b.transferToJupiter)} to Jupiter and split into pods",
            steps = buildList {
                b.salaryDayPayments.forEach { add("Pay ${it.label.lowercaseFirst()} ${formatInr(it.amount)} from HDFC") }
                add("Send ${formatInr(b.transferToJupiter)} to Jupiter")
                b.podCredits.forEach { add("${it.label}: ${formatInr(it.amount)}") }
                add("Keep ${formatInr(b.jupiterMain)} in Jupiter main for spending")
                add("Leave ${formatInr(b.hdfcHold)} in HDFC for bills")
            },
        )

        for ((item, date) in occurrences(cycleStart, cfg)) {
            when (item.flow) {
                Flow.INCOME -> out += PlanItem(id(date, item.id), date, item.title, item.amount, item.account,
                    ItemKind.INCOME, detail = "Arrives in ${item.account}")
                Flow.HDFC -> out += PlanItem(id(date, item.id), date, item.title, item.amount, item.account,
                    if (item.autopay) ItemKind.AUTOPAY else ItemKind.BILL,
                    detail = when {
                        date == cycleStart -> "Pay from HDFC on salary day"
                        item.endDate != null && Plan.occurrence(cycleStart.plusMonths(1), item.day).isAfter(item.endDate) ->
                            "Last payment"
                        item.autopay -> "HDFC autopay"
                        else -> "Pay from HDFC"
                    })
                Flow.POD -> {
                    val pod = item.pod.ifBlank { "${item.title} pod" }
                    out += PlanItem(id(date, "move-${item.id}"), date, "Move ${pod} money to Jupiter main", item.amount,
                        Plan.JUPITER, ItemKind.TRANSFER, detail = "$pod → Jupiter main before the payment", pod = pod)
                    out += PlanItem(id(date, item.id), date, item.title, item.amount, item.account,
                        if (item.autopay) ItemKind.AUTOPAY else ItemKind.BILL,
                        detail = if (item.autopay) "${item.account} autopay" else "Pay from ${item.account}")
                }
            }
        }

        debtFor(cycleStart).forEachIndexed { i, d ->
            out += PlanItem(id(d.date, if (i == 0) "debt" else "debt$i"), d.date, "Repay ${Plan.LENDER_NAME}", d.amount,
                Plan.DEBT_POD, ItemKind.DEBT, detail = "Instalment on the ₹2,00,000 personal loan", pod = Plan.DEBT_POD)
        }

        val reviewDate = Plan.cycleEnd(cycleStart)
        out += PlanItem(id(reviewDate, "review"), reviewDate, "Monthly review", 0, "", ItemKind.REVIEW,
            detail = "Check spending, pods and who owes what")

        return out.sortedWith(compareBy({ it.date }, { it.kind.order }))
    }

    /** Items whose dates fall within [from, to] inclusive. */
    fun itemsBetween(from: LocalDate, to: LocalDate): List<PlanItem> {
        val result = mutableListOf<PlanItem>()
        var cycle = Plan.cycleStartFor(from)
        while (!cycle.isAfter(to)) {
            result += items(cycle).filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            cycle = cycle.plusMonths(1)
        }
        return result
    }

    fun itemById(id: String): PlanItem? {
        val date = runCatching { LocalDate.parse(id.substringBefore(':')) }.getOrNull() ?: return null
        return items(Plan.cycleStartFor(date)).firstOrNull { it.id == id }
    }

    private fun id(date: LocalDate, key: String) = "$date:$key"
}

private fun String.lowercaseFirst(): String = if (isEmpty()) this else this[0].lowercase() + substring(1)
