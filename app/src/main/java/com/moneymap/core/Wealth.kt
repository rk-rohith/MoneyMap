package com.moneymap.core

import java.time.LocalDate
import kotlin.math.roundToLong

enum class InvestmentKind(val label: String) {
    SIP("SIP / mutual fund"), STOCKS("Stocks"), FD("Fixed deposit"), GOLD("Gold"), RETIREMENT("PPF / EPF / NPS"), OTHER("Other");

    companion object {
        fun parse(name: String?): InvestmentKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Money put in (positive) or taken out (negative), with fund units when known. */
data class InvestmentTxn(val date: LocalDate, val amount: Long, val units: Double? = null)

data class Investment(
    val id: String,
    val name: String,
    val kind: InvestmentKind = InvestmentKind.SIP,
    /** Monthly SIP amount, 0 when it isn't a SIP. */
    val monthlySip: Long = 0,
    val sipDay: Int = 1,
    val txns: List<InvestmentTxn> = emptyList(),
    /** Latest value entered by hand (used when there is no NAV). */
    val currentValue: Long? = null,
    /** Latest NAV / price per unit; with units this gives the value. */
    val nav: Double? = null,
    val valueUpdated: LocalDate? = null,
) {
    val invested: Long get() = txns.sumOf { it.amount }
    val units: Double get() = txns.sumOf { it.units ?: 0.0 }
    val value: Long
        get() = when {
            nav != null && units > 0 -> (units * nav).roundToLong()
            currentValue != null -> currentValue
            else -> invested
        }
    val gain: Long get() = value - invested
    val gainPercent: Double? get() = if (invested > 0) gain * 100.0 / invested else null
}

data class InvestmentSummary(val invested: Long, val value: Long) {
    val gain: Long get() = value - invested
    val gainPercent: Double? get() = if (invested > 0) gain * 100.0 / invested else null
}

object Investments {
    fun summary(list: List<Investment>) = InvestmentSummary(list.sumOf { it.invested }, list.sumOf { it.value })

    /** True when this month's SIP date has passed and nothing was invested since then. */
    fun sipDue(inv: Investment, today: LocalDate): Boolean {
        if (inv.monthlySip <= 0) return false
        val due = today.withDayOfMonth(inv.sipDay.coerceIn(1, today.lengthOfMonth()))
        if (today.isBefore(due)) return false
        return inv.txns.none { !it.date.isBefore(due) && it.amount > 0 }
    }

    /** Records this month's SIP instalment; [units] when the fund statement shows them. */
    fun addSip(inv: Investment, date: LocalDate, units: Double? = null): Investment =
        inv.copy(txns = inv.txns + InvestmentTxn(date, inv.monthlySip, units))
}

/** What you own minus what you owe, at one moment. */
data class NetWorth(
    val pods: Long,
    val owedToMe: Long,
    val investments: Long,
    val iOwe: Long,
    val loans: Long,
) {
    val assets: Long get() = pods + owedToMe + investments
    val liabilities: Long get() = iOwe + loans
    val total: Long get() = assets - liabilities
}

/** Net worth at the end of a month (keyed by the 1st of that month). */
data class NetWorthPoint(val month: LocalDate, val total: Long)

object NetWorthCalc {
    fun compute(
        podBalances: List<PodBalance>,
        ledger: LedgerSummary,
        investments: List<Investment>,
        loans: List<Loan>,
        today: LocalDate,
    ) = NetWorth(
        pods = podBalances.sumOf { it.balance },
        owedToMe = ledger.othersOweMe,
        investments = Investments.summary(investments).value,
        iOwe = ledger.iOwe,
        loans = loans.sumOf { LoanMath.outstandingOn(it, today) },
    )

    /** Stores [total] as this month's point, replacing an earlier one from the same month. Oldest first. */
    fun record(history: List<NetWorthPoint>, today: LocalDate, total: Long, keep: Int = 120): List<NetWorthPoint> {
        val month = today.withDayOfMonth(1)
        return (history.filter { it.month != month } + NetWorthPoint(month, total)).sortedBy { it.month }.takeLast(keep)
    }

    /** Change over the last [months] months, or null without history that far back. */
    fun changeOver(history: List<NetWorthPoint>, months: Int): Long? {
        val last = history.lastOrNull() ?: return null
        val from = last.month.minusMonths(months.toLong())
        val base = history.lastOrNull { !it.month.isAfter(from) } ?: return null
        return last.total - base.total
    }
}
