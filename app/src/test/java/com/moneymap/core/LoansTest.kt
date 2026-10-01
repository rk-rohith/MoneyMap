package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LoansTest {
    // ₹5,00,000 at 9% for 36 months.
    private val emi = LoanMath.emiFor(500_000, 9.0, 36)
    private val car = Loan("car", "Car loan", 500_000, 9.0, emi, LocalDate.of(2026, 1, 17))

    @Test
    fun emiAndScheduleBalanceOut() {
        assertEquals(15_900, emi)
        val s = LoanMath.schedule(car)
        assertTrue(s.endsWithinLimit)
        assertEquals(36, s.months)
        assertEquals(0, s.rows.last().balance)
        assertEquals(LocalDate.of(2028, 12, 17), s.payoffDate)
        assertEquals(car.principal, s.rows.sumOf { it.principal + it.extra })
        assertEquals(3_750, s.rows.first().interest) // 5,00,000 × 0.75%
        // Total interest for a standard 9% / 3-year loan is about ₹72k.
        assertTrue(s.totalInterest in 72_000..73_000)
    }

    @Test
    fun outstandingTracksPayments() {
        assertEquals(500_000, LoanMath.outstandingOn(car, LocalDate.of(2025, 12, 31)))
        val s = LoanMath.schedule(car)
        assertEquals(s.rows[11].balance, LoanMath.outstandingOn(car, LocalDate.of(2026, 12, 20)))
        assertEquals(0, LoanMath.outstandingOn(car, LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun prepaymentShortensTheLoan() {
        val effect = LoanMath.prepaymentEffect(car, 100_000, LocalDate.of(2026, 6, 1))
        assertTrue(effect.monthsSaved in 6..8)
        assertTrue(effect.interestSaved > 10_000)
        assertTrue(effect.newPayoff!!.isBefore(LocalDate.of(2028, 12, 17)))
        // Paying it all off ends the schedule that month.
        val all = LoanMath.schedule(car, listOf(Prepayment(LocalDate.of(2026, 3, 1), 1_000_000)))
        assertEquals(LocalDate.of(2026, 3, 17), all.payoffDate)
        assertEquals(0, all.rows.last().balance)
    }

    @Test
    fun emiBelowInterestNeverEnds() {
        val bad = car.copy(emi = 3_000)
        assertFalse(LoanMath.schedule(bad).endsWithinLimit)
        assertEquals(10_000, LoanMath.emiFor(120_000, 0.0, 12))
    }
}
