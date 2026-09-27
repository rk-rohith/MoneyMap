package com.moneymap.core

import java.time.LocalDate

/**
 * The hard-coded monthly financial plan. A cycle starts on salary day (25th) and
 * ends on the 24th of the following month. Cycles are identified by their start date.
 */
object Plan {
    const val SALARY = 160_000L
    const val SALARY_DAY = 25
    const val REVIEW_DAY = 24

    const val RENT_RECEIVED = 8_000L
    const val RENT_PAID = 18_000L
    const val RENT_PAID_DAY = 7
    /** Rent is received on salary days up to and including this date. */
    val LAST_RENT_RECEIVED: LocalDate = LocalDate.of(2027, 1, 25)
    /** Last rent payment. */
    val LAST_RENT_PAID: LocalDate = LocalDate.of(2027, 1, 7)

    const val TERM_INSURANCE = 3_300L
    const val TERM_INSURANCE_DAY = 3
    const val CAR_EMI = 20_000L
    const val CAR_EMI_DAY = 17
    const val HDFC_SIP = 2_000L
    const val JUPITER_SIP = 18_000L
    const val SIP_DAY = 1
    const val UTILITY = 20_000L
    const val SPEND_BUDGET = 20_000L

    const val LOAN_TOTAL = 200_000L
    const val LENDER_NAME = "Lender"
    val DEBT_SCHEDULE: List<Pair<LocalDate, Long>> = listOf(
        LocalDate.of(2026, 10, 25) to 60_000L,
        LocalDate.of(2026, 11, 25) to 60_000L,
        LocalDate.of(2026, 12, 25) to 60_000L,
        LocalDate.of(2027, 1, 25) to 20_000L,
    )

    /** Nothing dated before this is ever treated as overdue. */
    val TRACK_START: LocalDate = LocalDate.of(2026, 9, 27)

    const val HDFC = "HDFC"
    const val JUPITER = "Jupiter"

    fun cycleStartFor(date: LocalDate): LocalDate =
        if (date.dayOfMonth >= SALARY_DAY) date.withDayOfMonth(SALARY_DAY)
        else date.minusMonths(1).withDayOfMonth(SALARY_DAY)

    fun cycleEnd(cycleStart: LocalDate): LocalDate = cycleStart.plusMonths(1).withDayOfMonth(REVIEW_DAY)

    fun cycleLabel(cycleStart: LocalDate): String = "${cycleStart.monthYear()} cycle"

    fun debtFor(cycleStart: LocalDate): Long =
        DEBT_SCHEDULE.filter { it.first == cycleStart }.sumOf { it.second }

    fun rentReceivedIn(cycleStart: LocalDate): Long =
        if (!cycleStart.isAfter(LAST_RENT_RECEIVED)) RENT_RECEIVED else 0L

    /** Rent paid on the 7th that falls inside this cycle. */
    fun rentPaidIn(cycleStart: LocalDate): Long {
        val rentDate = cycleStart.plusMonths(1).withDayOfMonth(RENT_PAID_DAY)
        return if (!rentDate.isAfter(LAST_RENT_PAID)) RENT_PAID else 0L
    }

    fun budget(cycleStart: LocalDate): CycleBudget {
        val rentIn = rentReceivedIn(cycleStart)
        val rentOut = rentPaidIn(cycleStart)
        val debt = debtFor(cycleStart)
        val income = SALARY + rentIn
        val hdfcHold = TERM_INSURANCE + CAR_EMI + HDFC_SIP + rentOut
        val toJupiter = income - UTILITY - hdfcHold
        val emergency = toJupiter - debt - JUPITER_SIP - SPEND_BUDGET
        return CycleBudget(
            cycleStart = cycleStart,
            salary = SALARY,
            rentReceived = rentIn,
            income = income,
            utility = UTILITY,
            termInsurance = TERM_INSURANCE,
            carEmi = CAR_EMI,
            hdfcSip = HDFC_SIP,
            rentPaid = rentOut,
            hdfcHold = hdfcHold,
            transferToJupiter = toJupiter,
            debtPod = debt,
            sipPod = JUPITER_SIP,
            jupiterMain = SPEND_BUDGET,
            emergencyPod = emergency,
        )
    }

    /** All plan items in the cycle that starts on [cycleStart], sorted by date. */
    fun items(cycleStart: LocalDate): List<PlanItem> {
        val b = budget(cycleStart)
        val next = cycleStart.plusMonths(1)
        val out = mutableListOf<PlanItem>()
        val sd = cycleStart

        out += PlanItem(id(sd, "salary"), sd, "Salary credited", SALARY, HDFC, ItemKind.INCOME,
            detail = "Salary arrives in HDFC")
        if (b.rentReceived > 0) {
            out += PlanItem(id(sd, "rent-in"), sd, "Rent received", b.rentReceived, HDFC, ItemKind.INCOME,
                detail = "Rent received on salary day")
        }
        out += PlanItem(
            id(sd, "salary-day"), sd, "Salary-day routine", b.transferToJupiter, "$HDFC → $JUPITER",
            ItemKind.SALARY_DAY,
            detail = "Send ${formatInr(b.transferToJupiter)} to Jupiter and split into pods",
            steps = buildList {
                add("Pay house utility ${formatInr(UTILITY)} from HDFC")
                add("Send ${formatInr(b.transferToJupiter)} to Jupiter")
                if (b.debtPod > 0) add("Debt pod: ${formatInr(b.debtPod)}")
                add("Emergency pod: ${formatInr(b.emergencyPod)}")
                add("SIP pod: ${formatInr(b.sipPod)}")
                add("Keep ${formatInr(b.jupiterMain)} in Jupiter main for spending")
                add("Leave ${formatInr(b.hdfcHold)} in HDFC for bills")
            },
        )
        out += PlanItem(id(sd, "utility"), sd, "House utility", UTILITY, HDFC, ItemKind.BILL,
            detail = "Pay from HDFC on salary day")
        if (b.debtPod > 0) {
            out += PlanItem(id(sd, "debt"), sd, "Repay ${LENDER_NAME}", b.debtPod, "Debt pod", ItemKind.DEBT,
                detail = "Instalment on the ₹2,00,000 personal loan")
        }

        val sipDate = next.withDayOfMonth(SIP_DAY)
        out += PlanItem(id(sipDate, "sip-move"), sipDate, "Move SIP money to Jupiter main", JUPITER_SIP,
            JUPITER, ItemKind.TRANSFER, detail = "SIP pod → Jupiter main before the SIP autopay")
        out += PlanItem(id(sipDate, "sip-jupiter"), sipDate, "Jupiter SIP autopay", JUPITER_SIP, JUPITER,
            ItemKind.AUTOPAY, detail = "Debits on the 1st/2nd")
        out += PlanItem(id(sipDate, "sip-hdfc"), sipDate, "HDFC SIP autopay", HDFC_SIP, HDFC,
            ItemKind.AUTOPAY, detail = "Debits on the 1st/2nd")

        val termDate = next.withDayOfMonth(TERM_INSURANCE_DAY)
        out += PlanItem(id(termDate, "term"), termDate, "Term insurance", TERM_INSURANCE, HDFC, ItemKind.AUTOPAY,
            detail = "HDFC autopay")

        if (b.rentPaid > 0) {
            val rentDate = next.withDayOfMonth(RENT_PAID_DAY)
            out += PlanItem(id(rentDate, "rent-out"), rentDate, "Pay rent", b.rentPaid, HDFC, ItemKind.BILL,
                detail = if (rentDate == LAST_RENT_PAID) "Last rent payment" else "Rent paid on the 7th")
        }

        val carDate = next.withDayOfMonth(CAR_EMI_DAY)
        out += PlanItem(id(carDate, "car-emi"), carDate, "Car loan EMI", CAR_EMI, HDFC, ItemKind.AUTOPAY,
            detail = "HDFC autopay")

        val reviewDate = next.withDayOfMonth(REVIEW_DAY)
        out += PlanItem(id(reviewDate, "review"), reviewDate, "Monthly review", 0, "", ItemKind.REVIEW,
            detail = "Check spending, pods and who owes what")

        return out.sortedWith(compareBy({ it.date }, { it.kind.order }))
    }

    /** Items whose dates fall within [from, to] inclusive. */
    fun itemsBetween(from: LocalDate, to: LocalDate): List<PlanItem> {
        val result = mutableListOf<PlanItem>()
        var cycle = cycleStartFor(from)
        while (!cycle.isAfter(to)) {
            result += items(cycle).filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            cycle = cycle.plusMonths(1)
        }
        return result
    }

    fun itemById(id: String): PlanItem? {
        val date = runCatching { LocalDate.parse(id.substringBefore(':')) }.getOrNull() ?: return null
        return items(cycleStartFor(date)).firstOrNull { it.id == id }
    }

    fun isDebtItem(id: String): Boolean = id.endsWith(":debt")

    private fun id(date: LocalDate, key: String) = "$date:$key"
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
) {
    /** Income is informational; everything else gets reminders. */
    val notifies: Boolean get() = kind != ItemKind.INCOME
    val tracked: Boolean get() = !date.isBefore(Plan.TRACK_START)
}

data class CycleBudget(
    val cycleStart: LocalDate,
    val salary: Long,
    val rentReceived: Long,
    val income: Long,
    val utility: Long,
    val termInsurance: Long,
    val carEmi: Long,
    val hdfcSip: Long,
    val rentPaid: Long,
    val hdfcHold: Long,
    val transferToJupiter: Long,
    val debtPod: Long,
    val sipPod: Long,
    val jupiterMain: Long,
    val emergencyPod: Long,
) {
    val totalOut: Long get() = utility + hdfcHold + debtPod + sipPod + jupiterMain
}
