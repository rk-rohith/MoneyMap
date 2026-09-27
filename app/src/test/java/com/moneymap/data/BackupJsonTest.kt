package com.moneymap.data

import com.moneymap.core.DefaultPlan
import com.moneymap.core.Flow
import com.moneymap.core.PlanEngine
import com.moneymap.core.RecurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BackupJsonTest {
    @Test
    fun planRoundTrip() {
        val extra = RecurringItem("phone", "Phone EMI", 2_500, 10, Flow.POD, "Jupiter", autopay = true, pod = "Phone pod",
            startDate = LocalDate.of(2027, 3, 1), endDate = null)
        val settings = DefaultPlan.settings
            .withVersion(LocalDate.of(2027, 4, 25), DefaultPlan.config.copy(salary = 180_000, items = DefaultPlan.items + extra))
        val back = PlanJson.decode(PlanJson.encode(settings))
        assertEquals(settings.sortedVersions, back.sortedVersions)
        assertEquals(settings.debt, back.debt)
        assertEquals(PlanEngine(settings).budget(LocalDate.of(2027, 5, 25)), PlanEngine(back).budget(LocalDate.of(2027, 5, 25)))
    }

    @Test
    fun backupRoundTripIncludesNewData() {
        val data = BackupData(
            entries = listOf(EntryEntity(1, "Mom", "LENT", 33_000, "Cot", 20_000, null, "EMERGENCY", "", "mom-cot", 1, 2)),
            txns = listOf(TxnEntity(1, 1, "GIVEN", 33_000, 20_000, "Cot", null)),
            expenses = listOf(ExpenseEntity(1, 450, "Lunch", 20_001, 3, "FOOD")),
            done = listOf(DoneEntity("2026-10-25:salary-day", 4)),
            podMoves = listOf(PodMoveEntity(1, "Emergency pod", 6_700, 20_001, "Salary-day split", "2026-10-25:salary-day")),
            goals = listOf(GoalEntity(1, "Sri Lanka trip", 100_000, 20_070, "Sri Lanka pod", 5)),
            plan = PlanJson.encode(DefaultPlan.settings),
        )
        val back = Backup.fromJson(Backup.toJson(data))
        assertEquals(data.entries, back.entries)
        assertEquals(data.txns, back.txns)
        assertEquals(data.expenses, back.expenses)
        assertEquals(data.done, back.done)
        assertEquals(data.podMoves, back.podMoves)
        assertEquals(data.goals, back.goals)
        assertEquals(DefaultPlan.settings.sortedVersions, PlanJson.decode(back.plan!!).sortedVersions)
    }

    @Test
    fun oldBackupWithoutNewFieldsStillImports() {
        val v1 = """
            {"app":"MoneyMap","version":1,
             "entries":[{"id":1,"person":"Madhu","direction":"LENT","amount":3008,"reason":"HDFC card spend",
               "date":"2026-09-27","dueDate":null,"returnPod":"JUPITER_MAIN","notes":"","seedKey":null,"createdAt":0,"updatedAt":0}],
             "transactions":[],
             "expenses":[{"id":1,"amount":200,"note":"Tea","date":"2026-09-28","createdAt":0}],
             "done":[]}
        """.trimIndent()
        val back = Backup.fromJson(v1)
        assertEquals("OTHER", back.expenses.single().category)
        assertEquals(0, back.podMoves.size)
        assertNull(back.plan)
    }
}
