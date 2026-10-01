package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsTest {
    private val plan = DefaultPlan.engine
    private val oct = LocalDate.of(2026, 10, 25)

    @Test
    fun salaryDayTickCreditsEveryPod() {
        val item = plan.items(oct).first { it.kind == ItemKind.SALARY_DAY }
        val moves = Pods.movesForTick(item, plan.budget(oct))
        assertEquals(
            mapOf(Plan.DEBT_POD to 60_000L, Plan.EMERGENCY_POD to 6_700L, "SIP pod" to 18_000L),
            moves.associate { it.pod to it.amount },
        )
        assertTrue(moves.all { it.linkKey == item.id })
    }

    @Test
    fun debtAndMoveTicksDebitPods() {
        val items = plan.items(oct)
        val debt = items.first { it.kind == ItemKind.DEBT }
        val move = items.first { it.kind == ItemKind.TRANSFER }
        val salaryDay = items.first { it.kind == ItemKind.SALARY_DAY }
        val all = Pods.movesForTick(salaryDay, plan.budget(oct)) +
            Pods.movesForTick(debt, plan.budget(oct)) +
            Pods.movesForTick(move, plan.budget(oct))
        assertEquals(0L, Pods.balanceOf(all, Plan.DEBT_POD))
        assertEquals(0L, Pods.balanceOf(all, "SIP pod"))
        assertEquals(6_700L, Pods.balanceOf(all, Plan.EMERGENCY_POD))
        assertTrue(Pods.movesForTick(items.first { it.id.endsWith(":car-emi") }, plan.budget(oct)).isEmpty())
    }

    @Test
    fun returnsGoToTheirPod() {
        val m = Pods.moveForReturn(ReturnPod.SRI_LANKA, 44_282, oct, "Friend", 7)!!
        assertEquals("Sri Lanka pod", m.pod)
        assertEquals("txn:7", m.linkKey)
        assertNull(Pods.moveForReturn(ReturnPod.JUPITER_MAIN, 3_008, oct, "Madhu", 8))
    }

    @Test
    fun balancesListDefaultAndExtraPods() {
        val moves = listOf(
            PodMove(1, Plan.EMERGENCY_POD, 10_000, oct),
            PodMove(2, Plan.EMERGENCY_POD, -2_500, oct.plusDays(3)),
            PodMove(3, "Car service pod", 4_000, oct),
        )
        val b = Pods.balances(moves, listOf("SIP pod"))
        assertEquals(listOf(Plan.EMERGENCY_POD, Plan.DEBT_POD, "Sri Lanka pod", "SIP pod", "Car service pod"), b.map { it.pod })
        assertEquals(7_500L, b.first().balance)
        assertEquals(2L, b.first().moves.first().id) // newest first
    }

    @Test
    fun goalPerCycle() {
        val goal = Goal(name = "Sri Lanka trip", target = 100_000, targetDate = LocalDate.of(2026, 12, 10), pod = "Sri Lanka pod")
        val p = Pods.goalProgress(goal, saved = 40_000, today = LocalDate.of(2026, 9, 27))
        // Sep, Oct and Nov cycles are left before the Dec 10 target (which falls in the Nov cycle).
        assertEquals(3L, p.cyclesLeft)
        assertEquals(60_000L, p.remaining)
        assertEquals(20_000L, p.perCycle)
        assertEquals(0.4f, p.progress, 0.001f)

        val done = Pods.goalProgress(goal, saved = 120_000, today = LocalDate.of(2026, 9, 27))
        assertTrue(done.reached)
        assertEquals(0L, done.perCycle)
        assertNull(Pods.goalProgress(goal.copy(targetDate = null), 0, LocalDate.of(2026, 9, 27)).perCycle)
        // A past target date still asks for the rest in one cycle.
        assertEquals(1L, Pods.goalProgress(goal, 0, LocalDate.of(2027, 3, 1)).cyclesLeft)
    }

    @Test
    fun categoryBreakdown() {
        val b = Spending.breakdown(listOf(
            ExpenseCategory.FOOD to 3_000L,
            ExpenseCategory.TRANSPORT to 1_000L,
            ExpenseCategory.FOOD to 1_000L,
        ))
        assertEquals(listOf(ExpenseCategory.FOOD, ExpenseCategory.TRANSPORT), b.map { it.category })
        assertEquals(4_000L, b[0].amount)
        assertEquals(0.8f, b[0].share, 0.001f)
        assertTrue(Spending.breakdown(emptyList()).isEmpty())
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.parse(null))
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.parse("FOOD"))
    }
}

class OneOffAndSearchTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun engineWith(vararg o: OneOff) = PlanEngine(DefaultPlan.settings.copy(oneOffs = o.toList()))

    @Test
    fun oneOffInSingleCycle() {
        val premium = OneOff("p", "Car insurance", 18_000, d(2027, 3, 10), fundFrom = d(2026, 9, 25))
        val e = engineWith(premium)
        // Due 10 Mar 2027 falls in the Feb 2027 cycle.
        assertEquals(76_700L - 18_000L, e.budget(d(2027, 2, 25)).emergencyPod)
        assertEquals(18_000L, e.budget(d(2027, 2, 25)).pod("Car insurance pod"))
        assertEquals(76_700L, e.budget(d(2027, 3, 25)).emergencyPod)
        val items = e.items(d(2027, 2, 25))
        assertEquals(d(2027, 3, 10), items.first { it.id == "2027-03-10:oneoff-p" }.date)
        assertEquals("Car insurance pod", items.first { it.id == "2027-03-10:move-oneoff-p" }.pod)
        assertTrue(items.first { it.kind == ItemKind.SALARY_DAY }.steps.any { it == "Car insurance pod: ₹18,000" })
    }

    @Test
    fun oneOffSpreadOverCycles() {
        val trip = OneOff("t", "Goa trip", 30_001, d(2027, 5, 1), spreadCycles = 3, fundFrom = d(2026, 9, 25))
        assertEquals(listOf(d(2027, 2, 25), d(2027, 3, 25), d(2027, 4, 25)), trip.fundingCycles())
        assertEquals(10_000L, trip.fundingFor(d(2027, 2, 25)))
        assertEquals(10_001L, trip.fundingFor(d(2027, 4, 25)))
        assertEquals(0L, trip.fundingFor(d(2027, 1, 25)))
        val e = engineWith(trip)
        assertEquals(66_700L, e.budget(d(2027, 2, 25)).emergencyPod)
        assertEquals(76_700L, e.budget(d(2027, 5, 25)).emergencyPod)
    }

    @Test
    fun oneOffNeverFundsPastCycles() {
        // Planned in the Mar 2027 cycle for 6 cycles, but due in the Apr cycle: only Mar and Apr can save.
        val o = OneOff("x", "Phone", 20_000, d(2027, 5, 5), spreadCycles = 6, fundFrom = d(2027, 3, 26))
        assertEquals(listOf(d(2027, 3, 25), d(2027, 4, 25)), o.fundingCycles())
        assertEquals(10_000L, o.fundingFor(d(2027, 3, 25)))
    }

    @Test
    fun oneOffTickMovesPods() {
        val o = OneOff("p", "Car insurance", 18_000, d(2027, 3, 10), fundFrom = d(2026, 9, 25))
        val e = engineWith(o)
        val cycle = d(2027, 2, 25)
        val items = e.items(cycle)
        val moves = Pods.movesForTick(items.first { it.kind == ItemKind.SALARY_DAY }, e.budget(cycle)) +
            Pods.movesForTick(items.first { it.id.endsWith("move-oneoff-p") }, e.budget(cycle))
        assertEquals(0L, Pods.balanceOf(moves, "Car insurance pod"))
    }

    @Test
    fun searchMatchesTextAndAmounts() {
        assertTrue(Search.matches("friend emi", listOf("Friend", "2 loan EMIs")))
        assertTrue(Search.matches("1,500", listOf("Petrol"), listOf(1_500)))
        assertTrue(Search.matches("₹1500", listOf("Petrol"), listOf(1_500)))
        assertTrue(Search.matches("15", listOf("Petrol"), listOf(1_500)))
        assertFalse(Search.matches("food 900", listOf("Food"), listOf(1_500)))
        assertFalse(Search.matches("   ", listOf("Anything")))
    }
}

class SplitTest {
    @org.junit.Test
    fun equalSplitRemainderStaysWithMe() {
        val (mine, shares) = Split.equal(1_000, listOf("Asha", " Ravi ", "asha", ""))
        org.junit.Assert.assertEquals(334, mine)
        org.junit.Assert.assertEquals(listOf(SplitShare("Asha", 333), SplitShare("Ravi", 333)), shares)
        org.junit.Assert.assertEquals(1_000, mine + shares.sumOf { it.amount })
        org.junit.Assert.assertEquals(500L to emptyList<SplitShare>(), Split.equal(500, emptyList()))
    }
}

class GoalAutopilotTest {
    private val today = java.time.LocalDate.of(2026, 10, 1)
    private fun progress(name: String, target: Long, saved: Long, date: java.time.LocalDate?) =
        Pods.goalProgress(Goal(name = name, target = target, targetDate = date, pod = "$name pod"), saved, today)

    @org.junit.Test
    fun enoughForDatedGoalsThenHalfTheRestToUndated() {
        val trip = progress("Trip", 60_000, 0, java.time.LocalDate.of(2027, 3, 1)) // 6 cycles → 10,000
        val bike = progress("Bike", 100_000, 20_000, null)
        val tv = progress("TV", 40_000, 0, null)
        val done = progress("Phone", 10_000, 10_000, null)
        val plan = GoalAutopilot.plan(listOf(trip, bike, tv, done), 30_000)
        org.junit.Assert.assertEquals(listOf("Trip", "Bike", "TV"), plan.allocations.map { it.goal.name })
        org.junit.Assert.assertEquals(10_000, plan.allocations[0].suggested)
        // 20,000 left, half = 10,000 split 80k:40k.
        org.junit.Assert.assertEquals(6_666, plan.allocations[1].suggested)
        org.junit.Assert.assertEquals(3_333, plan.allocations[2].suggested)
        org.junit.Assert.assertEquals(0, plan.shortBy)
        org.junit.Assert.assertEquals(30_000 - 19_999, plan.keep)
    }

    @org.junit.Test
    fun notEnoughScalesDatedGoalsEvenly() {
        val a = progress("A", 60_000, 0, java.time.LocalDate.of(2027, 3, 1)) // 10,000
        val b = progress("B", 30_000, 0, java.time.LocalDate.of(2027, 3, 1)) // 5,000
        val plan = GoalAutopilot.plan(listOf(a, b), 6_000)
        org.junit.Assert.assertEquals(listOf(4_000L, 2_000L), plan.allocations.map { it.suggested })
        org.junit.Assert.assertEquals(9_000, plan.shortBy)
        org.junit.Assert.assertEquals(0L, GoalAutopilot.plan(listOf(a), -500).total)
    }
}
