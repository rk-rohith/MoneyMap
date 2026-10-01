package com.moneymap.core

import java.time.LocalDate

/**
 * Who the plan belongs to: salary day, account names and the tracked loan. Existing installs keep these defaults
 * (the original setup); a new install picks its own values on the setup screen.
 */
data class Profile(
    /** Day of the month the salary arrives, 1–28. A cycle runs from this day to the day before it next month. */
    val salaryDay: Int = 25,
    /** Where salary lands and bills are paid from. */
    val salaryAccount: String = "HDFC",
    /** Where spending money and savings pods live. */
    val spendAccount: String = "Jupiter",
    /** Nothing dated before this is ever treated as overdue. */
    val trackStart: LocalDate = LocalDate.of(2026, 9, 27),
    val lenderName: String = "Lender",
    val loanTotal: Long = 200_000,
    /** Name of the extra savings pod people's returns can go to. */
    val goalPod: String = "Sri Lanka pod",
    /** False until the first-run setup is finished. */
    val setupDone: Boolean = true,
    /** When the salary day falls on a Saturday or Sunday, salary arrives on the Friday before. */
    val weekendSalaryEarly: Boolean = false,
) {
    init {
        require(salaryDay in 1..28) { "Salary day must be between 1 and 28" }
    }

    companion object {
        /** Starting point for a brand-new install, before the setup screen fills it in. */
        fun fresh(today: LocalDate) = Profile(
            salaryAccount = "Bank", spendAccount = "Spending", trackStart = today, lenderName = "Lender",
            loanTotal = 0, goalPod = "Goal pod", setupDone = false,
        )
    }
}

/** Cycle rules. They read the active [profile], which the app loads at start-up. */
object Plan {
    @Volatile
    var profile: Profile = Profile()

    val SALARY_DAY: Int get() = profile.salaryDay

    val LOAN_TOTAL: Long get() = profile.loanTotal
    val LENDER_NAME: String get() = profile.lenderName

    /** Nothing dated before this is ever treated as overdue. */
    val TRACK_START: LocalDate get() = profile.trackStart

    /** Salary account (bills are paid from here). The name is kept from the original HDFC setup. */
    val HDFC: String get() = profile.salaryAccount
    /** Spending account that holds the pods. */
    val JUPITER: String get() = profile.spendAccount
    val SPEND_MAIN: String get() = "${profile.spendAccount} main"

    const val EMERGENCY_POD = "Emergency pod"
    const val DEBT_POD = "Debt pod"

    fun cycleStartFor(date: LocalDate): LocalDate =
        if (date.dayOfMonth >= SALARY_DAY) date.withDayOfMonth(SALARY_DAY)
        else date.minusMonths(1).withDayOfMonth(SALARY_DAY)

    /** Last day of the cycle: the day before the next salary day. */
    fun cycleEnd(cycleStart: LocalDate): LocalDate = cycleStart.plusMonths(1).minusDays(1)

    fun cycleLabel(cycleStart: LocalDate): String = "${cycleStart.monthYear()} cycle"

    /** The day salary actually arrives for the cycle starting [cycleStart] (earlier when it would hit a weekend). */
    fun salaryDate(cycleStart: LocalDate): LocalDate = when {
        !profile.weekendSalaryEarly -> cycleStart
        cycleStart.dayOfWeek == java.time.DayOfWeek.SATURDAY -> cycleStart.minusDays(1)
        cycleStart.dayOfWeek == java.time.DayOfWeek.SUNDAY -> cycleStart.minusDays(2)
        else -> cycleStart
    }

    /** Date on which something due on [day] of the month falls inside the cycle starting [cycleStart]. */
    fun occurrence(cycleStart: LocalDate, day: Int): LocalDate {
        val month = if (day >= SALARY_DAY) cycleStart else cycleStart.plusMonths(1)
        return month.withDayOfMonth(day.coerceIn(1, month.lengthOfMonth()))
    }

    fun isDebtItem(id: String): Boolean = Regex(":debt\\d*$").containsMatchIn(id)
}

/** Names are stored in plan JSON, so HDFC/POD stay as they were; labels follow the profile's account names. */
enum class Flow {
    INCOME,
    HDFC,
    POD;

    val label: String
        get() = when (this) {
            INCOME -> "Income"
            HDFC -> "Paid from ${Plan.HDFC}"
            POD -> "Paid from a ${Plan.JUPITER} pod"
        }
}

/** A fixed monthly item: income, a bill/autopay from the salary account, or a payment funded by a pod. */
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

/** Income outside the regular plan (bonus, refund, freelance payment). It adds to its cycle's emergency pod. */
data class ExtraIncome(val id: String, val title: String, val amount: Long, val date: LocalDate)

/**
 * A one-off planned expense (yearly premium, service, festival budget). Money is set aside in its own pod
 * on salary days — spread over up to [spreadCycles] cycles ending with the cycle it's due in, but never
 * before [fundFrom] — and paid from that pod on [date].
 */
data class OneOff(
    val id: String,
    val title: String,
    val amount: Long,
    val date: LocalDate,
    val spreadCycles: Int = 1,
    /** First cycle that can set money aside (the cycle it was planned in). */
    val fundFrom: LocalDate = Plan.cycleStartFor(date),
) {
    val pod: String get() = "$title pod"
    val dueCycle: LocalDate get() = Plan.cycleStartFor(date)

    /** Cycles that set money aside, oldest first. */
    fun fundingCycles(): List<LocalDate> {
        val wanted = dueCycle.minusMonths((spreadCycles.coerceIn(1, 24) - 1).toLong())
        val first = maxOf(wanted, Plan.cycleStartFor(fundFrom)).let { if (it.isAfter(dueCycle)) dueCycle else it }
        val out = mutableListOf<LocalDate>()
        var c = first
        while (!c.isAfter(dueCycle)) {
            out += c
            c = c.plusMonths(1)
        }
        return out
    }

    /** Amount set aside in the cycle starting [cycleStart]; the last cycle takes any rounding remainder. */
    fun fundingFor(cycleStart: LocalDate): Long {
        val cycles = fundingCycles()
        val i = cycles.indexOf(cycleStart)
        if (i < 0) return 0
        val base = amount / cycles.size
        return if (i == cycles.lastIndex) amount - base * (cycles.size - 1) else base
    }
}

data class PlanSettings(
    val versions: List<PlanVersion>,
    val debt: List<DebtInstalment>,
    val oneOffs: List<OneOff> = emptyList(),
    val extraIncome: List<ExtraIncome> = emptyList(),
    /** Salary for single cycles that differ from the plan (keyed by cycle start), e.g. a bonus month or unpaid leave. */
    val salaryOverrides: Map<LocalDate, Long> = emptyMap(),
) {
    init {
        require(versions.isNotEmpty()) { "A plan needs at least one version" }
    }

    val sortedVersions: List<PlanVersion> get() = versions.sortedBy { it.from }

    /** The plan in force for the cycle starting [cycleStart]. Cycles before the first version use the first. */
    fun configFor(cycleStart: LocalDate): PlanConfig {
        val base = sortedVersions.lastOrNull { !it.from.isAfter(cycleStart) }?.config ?: sortedVersions.first().config
        return salaryOverrides[cycleStart]?.let { base.copy(salary = it) } ?: base
    }

    /** The plan version's own config, ignoring a one-cycle salary override (what the plan editor edits). */
    fun baseConfigFor(cycleStart: LocalDate): PlanConfig =
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
        val otherIncome = occ.filter { it.first.flow == Flow.INCOME }.map { Line(it.first.title, it.first.amount) } +
            settings.extraIncome.filter { Plan.cycleStartFor(it.date) == cycleStart && it.amount > 0 }
                .map { Line(it.title, it.amount) }
        val hdfc = occ.filter { it.first.flow == Flow.HDFC }
        val salaryDay = hdfc.filter { it.second == cycleStart }.map { Line(it.first.title, it.first.amount) }
        val bills = hdfc.filter { it.second != cycleStart }.map { Line(it.first.title, it.first.amount) }
        val pods = occ.filter { it.first.flow == Flow.POD }
            .map { Line(it.first.pod.ifBlank { "${it.first.title} pod" }, it.first.amount) } +
            settings.oneOffs.mapNotNull { o -> o.fundingFor(cycleStart).takeIf { it > 0 }?.let { Line(o.pod, it) } }
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
        // Ids keep the nominal salary day so ticks still match when the weekend rule moves the date.
        val paid = Plan.salaryDate(cycleStart)
        val out = mutableListOf<PlanItem>()

        out += PlanItem(id(sd, "salary"), paid, "Salary credited", cfg.salary, Plan.HDFC, ItemKind.INCOME,
            detail = if (settings.salaryOverrides.containsKey(cycleStart)) "Salary arrives in ${Plan.HDFC} (changed for this cycle)"
            else "Salary arrives in ${Plan.HDFC}")
        out += PlanItem(
            id(sd, "salary-day"), paid, "Salary-day routine", b.transferToJupiter, "${Plan.HDFC} → ${Plan.JUPITER}",
            ItemKind.SALARY_DAY,
            detail = "Send ${formatInr(b.transferToJupiter)} to ${Plan.JUPITER} and split into pods",
            steps = buildList {
                b.salaryDayPayments.forEach { add("Pay ${it.label.lowercaseFirst()} ${formatInr(it.amount)} from ${Plan.HDFC}") }
                add("Send ${formatInr(b.transferToJupiter)} to ${Plan.JUPITER}")
                b.podCredits.forEach { add("${it.label}: ${formatInr(it.amount)}") }
                add("Keep ${formatInr(b.jupiterMain)} in ${Plan.SPEND_MAIN} for spending")
                add("Leave ${formatInr(b.hdfcHold)} in ${Plan.HDFC} for bills")
            },
        )

        for ((item, date) in occurrences(cycleStart, cfg)) {
            when (item.flow) {
                Flow.INCOME -> out += PlanItem(id(date, item.id), date, item.title, item.amount, item.account,
                    ItemKind.INCOME, detail = "Arrives in ${item.account}")
                Flow.HDFC -> out += PlanItem(id(date, item.id), date, item.title, item.amount, item.account,
                    if (item.autopay) ItemKind.AUTOPAY else ItemKind.BILL,
                    detail = when {
                        date == cycleStart -> "Pay from ${Plan.HDFC} on salary day"
                        item.endDate != null && Plan.occurrence(cycleStart.plusMonths(1), item.day).isAfter(item.endDate) ->
                            "Last payment"
                        item.autopay -> "${Plan.HDFC} autopay"
                        else -> "Pay from ${Plan.HDFC}"
                    })
                Flow.POD -> {
                    val pod = item.pod.ifBlank { "${item.title} pod" }
                    out += PlanItem(id(date, "move-${item.id}"), date, "Move $pod money to ${Plan.SPEND_MAIN}", item.amount,
                        Plan.JUPITER, ItemKind.TRANSFER, detail = "$pod → ${Plan.SPEND_MAIN} before the payment", pod = pod)
                    out += PlanItem(id(date, item.id), date, item.title, item.amount, item.account,
                        if (item.autopay) ItemKind.AUTOPAY else ItemKind.BILL,
                        detail = if (item.autopay) "${item.account} autopay" else "Pay from ${item.account}")
                }
            }
        }

        settings.extraIncome.filter { Plan.cycleStartFor(it.date) == cycleStart && it.amount > 0 }.forEach { x ->
            out += PlanItem(id(x.date, "extra-${x.id}"), x.date, x.title, x.amount, Plan.HDFC, ItemKind.INCOME,
                detail = "Extra income · goes to the ${Plan.EMERGENCY_POD.lowercase()}")
        }

        val end = Plan.cycleEnd(cycleStart)
        settings.oneOffs.filter { !it.date.isBefore(cycleStart) && !it.date.isAfter(end) && it.amount > 0 }.forEach { o ->
            out += PlanItem(id(o.date, "move-oneoff-${o.id}"), o.date, "Move ${o.pod} money to ${Plan.SPEND_MAIN}", o.amount,
                Plan.JUPITER, ItemKind.TRANSFER, detail = "${o.pod} → ${Plan.SPEND_MAIN} before paying", pod = o.pod)
            out += PlanItem(id(o.date, "oneoff-${o.id}"), o.date, o.title, o.amount, Plan.JUPITER, ItemKind.BILL,
                detail = if (o.fundingCycles().size > 1) "Planned one-off · saved over ${o.fundingCycles().size} cycles"
                else "Planned one-off")
        }

        debtFor(cycleStart).forEachIndexed { i, d ->
            out += PlanItem(id(d.date, if (i == 0) "debt" else "debt$i"), d.date, "Repay ${Plan.LENDER_NAME}", d.amount,
                Plan.DEBT_POD, ItemKind.DEBT, detail = if (Plan.LOAN_TOTAL > 0) "Instalment on the ${formatInr(Plan.LOAN_TOTAL)} loan" else "Loan instalment",
                pod = Plan.DEBT_POD)
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
        // One cycle past [to]: its salary may arrive early, before the cycle starts.
        while (!cycle.isAfter(to.plusDays(3))) {
            result += items(cycle).filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            cycle = cycle.plusMonths(1)
        }
        return result.sortedWith(compareBy({ it.date }, { it.kind.order }))
    }

    fun itemById(id: String): PlanItem? {
        val date = runCatching { LocalDate.parse(id.substringBefore(':')) }.getOrNull() ?: return null
        return items(Plan.cycleStartFor(date)).firstOrNull { it.id == id }
    }

    private fun id(date: LocalDate, key: String) = "$date:$key"
}

private fun String.lowercaseFirst(): String = if (isEmpty()) this else this[0].lowercase() + substring(1)
