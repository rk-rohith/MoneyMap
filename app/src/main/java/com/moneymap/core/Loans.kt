package com.moneymap.core

import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong

/** A one-time extra payment towards a loan's principal. */
data class Prepayment(val date: LocalDate, val amount: Long)

/** A reducing-balance loan (car, home, personal…) paid by a fixed monthly EMI. */
data class Loan(
    val id: String,
    val name: String,
    val principal: Long,
    /** Yearly interest rate in percent, e.g. 9.5. */
    val annualRate: Double,
    val emi: Long,
    /** Date of the first EMI; later EMIs fall on the same day each month. */
    val firstEmi: LocalDate,
    val prepayments: List<Prepayment> = emptyList(),
)

data class AmortRow(
    val number: Int,
    val date: LocalDate,
    val payment: Long,
    val interest: Long,
    val principal: Long,
    /** Extra principal paid in this month (prepayments due since the previous EMI). */
    val extra: Long,
    val balance: Long,
)

data class LoanSchedule(val rows: List<AmortRow>, val endsWithinLimit: Boolean) {
    val payoffDate: LocalDate? get() = rows.lastOrNull()?.date
    val totalInterest: Long get() = rows.sumOf { it.interest }
    val months: Int get() = rows.size
}

data class PrepaymentEffect(val monthsSaved: Int, val interestSaved: Long, val newPayoff: LocalDate?)

object LoanMath {
    /** Schedules longer than this are treated as never ending (the EMI doesn't cover the interest). */
    const val MAX_MONTHS = 600

    fun schedule(loan: Loan, prepayments: List<Prepayment> = loan.prepayments): LoanSchedule {
        val r = loan.annualRate / 1200.0
        var balance = loan.principal
        val pending = prepayments.filter { it.amount > 0 }.sortedBy { it.date }.toMutableList()
        val rows = mutableListOf<AmortRow>()
        var n = 0
        while (balance > 0 && n < MAX_MONTHS) {
            val date = loan.firstEmi.plusMonths(n.toLong())
            // Prepayments made before this EMI reduce the balance first.
            var extra = 0L
            while (pending.isNotEmpty() && !pending.first().date.isAfter(date)) {
                extra += pending.removeAt(0).amount
            }
            extra = extra.coerceAtMost(balance)
            balance -= extra
            if (balance <= 0) {
                rows += AmortRow(n + 1, date, 0, 0, 0, extra, 0)
                break
            }
            val interest = (balance * r).roundToLong()
            val payment = minOf(loan.emi, balance + interest)
            val principalPaid = payment - interest
            if (principalPaid <= 0) return LoanSchedule(rows, endsWithinLimit = false)
            balance -= principalPaid
            n++
            rows += AmortRow(n, date, payment, interest, principalPaid, extra, balance)
        }
        return LoanSchedule(rows, endsWithinLimit = balance <= 0)
    }

    /** Balance left after every EMI and prepayment up to and including [date]. */
    fun outstandingOn(loan: Loan, date: LocalDate): Long {
        if (date.isBefore(loan.firstEmi)) {
            return (loan.principal - loan.prepayments.filter { !it.date.isAfter(date) }.sumOf { it.amount }).coerceAtLeast(0)
        }
        val rows = schedule(loan).rows
        return rows.lastOrNull { !it.date.isAfter(date) }?.balance ?: loan.principal
    }

    /** What an extra payment of [amount] on [date] would save, compared with the current plan. */
    fun prepaymentEffect(loan: Loan, amount: Long, date: LocalDate): PrepaymentEffect {
        val now = schedule(loan)
        val after = schedule(loan, loan.prepayments + Prepayment(date, amount))
        return PrepaymentEffect(
            monthsSaved = now.months - after.months,
            interestSaved = now.totalInterest - after.totalInterest,
            newPayoff = after.payoffDate,
        )
    }

    /** EMI for borrowing [principal] at [annualRate]% over [months] months. */
    fun emiFor(principal: Long, annualRate: Double, months: Int): Long {
        require(months > 0)
        val r = annualRate / 1200.0
        if (r == 0.0) return (principal + months - 1) / months
        val f = (1 + r).pow(months)
        return (principal * r * f / (f - 1)).roundToLong()
    }
}
