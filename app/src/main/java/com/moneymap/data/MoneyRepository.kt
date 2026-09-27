package com.moneymap.data

import androidx.room.withTransaction
import com.moneymap.core.DefaultPlan
import com.moneymap.core.Direction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.Goal
import com.moneymap.core.LedgerEntry
import com.moneymap.core.LedgerService
import com.moneymap.core.Plan
import com.moneymap.core.PlanEngine
import com.moneymap.core.PlanSettings
import com.moneymap.core.PodMove
import com.moneymap.core.ReminderPrefs
import com.moneymap.core.Pods
import com.moneymap.core.ReturnPod
import com.moneymap.core.Settlement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Single entry point for app data. Every mutation calls [onChanged] so alarms get rescheduled.
 */
class MoneyRepository(
    private val db: MoneyDatabase,
    private val onChanged: () -> Unit,
) {
    private val dao = db.dao()
    val ledger = LedgerService(RoomLedgerStore(db))

    val entries: Flow<List<EntryWithTxns>> = dao.observeEntries().map { rows -> rows.map { it.toDomain() } }
    val doneIds: Flow<Set<String>> = dao.observeDone().map { list -> list.map { it.itemId }.toSet() }
    val expenses: Flow<List<Expense>> = dao.observeExpenses().map { list -> list.map { it.toDomain() } }
    val podMoves: Flow<List<PodMove>> = dao.observePodMoves().map { list -> list.map { it.toDomain() } }
    val goals: Flow<List<Goal>> = dao.observeGoals().map { list -> list.map { it.toDomain() } }
    val plan: Flow<PlanEngine> = dao.observeSetting(PlanJson.SETTING_KEY).map { PlanEngine(decodePlan(it)) }
    val reminderPrefs: Flow<ReminderPrefs> =
        dao.observeSetting(ReminderPrefsJson.SETTING_KEY).map { ReminderPrefsJson.decode(it) }

    private fun decodePlan(text: String?): PlanSettings =
        text?.let { runCatching { PlanJson.decode(it) }.getOrNull() } ?: DefaultPlan.settings

    suspend fun init() {
        if (ledger.seedIfEmpty()) onChanged()
    }

    // Snapshots used by notifications and background work.
    suspend fun planNow(): PlanEngine = PlanEngine(decodePlan(dao.setting(PlanJson.SETTING_KEY)))
    suspend fun reminderPrefsNow(): ReminderPrefs = ReminderPrefsJson.decode(dao.setting(ReminderPrefsJson.SETTING_KEY))
    suspend fun entriesNow(): List<EntryWithTxns> = dao.entries().map { it.toDomain() }
    suspend fun doneNow(): Set<String> = dao.done().map { it.itemId }.toSet()
    suspend fun isDone(itemId: String): Boolean = dao.doneCount(itemId) > 0
    suspend fun spentInCycle(today: LocalDate): Long {
        val start = Plan.cycleStartFor(today)
        return dao.spentBetween(start.toEpochDay(), Plan.cycleEnd(start).toEpochDay())
    }

    suspend fun saveReminderPrefs(prefs: ReminderPrefs) {
        dao.putSetting(SettingEntity(ReminderPrefsJson.SETTING_KEY, ReminderPrefsJson.encode(prefs)))
        onChanged()
    }

    suspend fun savePlan(settings: PlanSettings) {
        dao.putSetting(SettingEntity(PlanJson.SETTING_KEY, PlanJson.encode(settings)))
        onChanged()
    }

    /**
     * Ticks or unticks a plan item. Debt instalments also update the ₹2L loan in the ledger, and
     * salary-day / debt / pod-move ticks update pod balances. Unticking reverses both.
     */
    suspend fun setDone(itemId: String, done: Boolean) {
        val engine = planNow()
        db.withTransaction {
            val item = engine.itemById(itemId)
            dao.deletePodMovesByLink(itemId)
            if (done) {
                dao.insertDone(DoneEntity(itemId, System.currentTimeMillis()))
                if (item != null) {
                    Pods.movesForTick(item, engine.budget(Plan.cycleStartFor(item.date)))
                        .forEach { dao.insertPodMove(it.toEntity()) }
                }
            } else {
                dao.deleteDone(itemId)
            }
            if (Plan.isDebtItem(itemId) && item != null) ledger.onDebtTick(item.id, item.amount, item.date, done)
        }
        onChanged()
    }

    suspend fun addEntry(
        person: String, direction: Direction, amount: Long, reason: String, date: LocalDate,
        dueDate: LocalDate?, pod: ReturnPod, notes: String,
    ): Long = ledger.addEntry(person, direction, amount, reason, date, dueDate, pod, notes).also { onChanged() }

    suspend fun editEntry(entry: LedgerEntry) {
        ledger.editEntry(entry)
        onChanged()
    }

    suspend fun deleteEntry(id: Long) {
        db.withTransaction {
            dao.entry(id)?.toDomain()?.txns?.forEach { t ->
                dao.deletePodMovesByLink(Pods.linkForTxn(t.id))
                t.linkKey?.let {
                    dao.deleteDone(it)
                    dao.deletePodMovesByLink(it)
                }
            }
            ledger.deleteEntry(id)
        }
        onChanged()
    }

    /** Records a return/repayment. Money coming back is added to the entry's return pod. */
    suspend fun recordSettlement(entryId: Long, amount: Long, date: LocalDate, note: String): Settlement? {
        val s = db.withTransaction {
            val s = ledger.recordSettlement(entryId, amount, date, note)
            if (s != null && s.direction == Direction.LENT) {
                Pods.moveForReturn(s.pod, s.amount, date, s.person, s.txnId)?.let { dao.insertPodMove(it.toEntity()) }
            }
            s
        }
        onChanged()
        return s
    }

    suspend fun undoLast(entryId: Long): Boolean {
        val removed = db.withTransaction {
            val t = ledger.undoLast(entryId)
            if (t != null) {
                dao.deletePodMovesByLink(Pods.linkForTxn(t.id))
                t.linkKey?.let {
                    dao.deleteDone(it)
                    dao.deletePodMovesByLink(it)
                }
            }
            t
        }
        onChanged()
        return removed != null
    }

    suspend fun addExpense(amount: Long, note: String, date: LocalDate, category: ExpenseCategory) {
        dao.insertExpense(ExpenseEntity(amount = amount, note = note.trim(), dateEpochDay = date.toEpochDay(),
            createdAt = System.currentTimeMillis(), category = category.name))
        onChanged()
    }

    /** Puts a deleted expense back with its original id (undo). */
    suspend fun restoreExpense(expense: Expense) {
        dao.insertExpense(expense.toEntity())
        onChanged()
    }

    suspend fun deleteExpense(id: Long) {
        dao.deleteExpense(id)
        onChanged()
    }

    /** "Paid for someone": the money is owed back, so it becomes a LENT entry instead of an expense. */
    suspend fun paidForSomeone(person: String, amount: Long, note: String, date: LocalDate): Long =
        addEntry(person, Direction.LENT, amount, note.ifBlank { "Paid for $person" }, date, null,
            ReturnPod.JUPITER_MAIN, "Added from Spend")

    /** Manual pod adjustment: positive adds money, negative withdraws. */
    suspend fun addPodMove(pod: String, amount: Long, note: String, date: LocalDate) {
        dao.insertPodMove(PodMoveEntity(pod = pod.trim(), amount = amount, dateEpochDay = date.toEpochDay(),
            note = note.trim(), linkKey = null))
    }

    suspend fun deletePodMove(id: Long) = dao.deletePodMove(id)

    suspend fun saveGoal(goal: Goal) {
        dao.upsertGoal(goal.copy(createdAt = goal.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteGoal(id: Long) = dao.deleteGoal(id)

    suspend fun snapshot(): BackupData = BackupData(
        entries = dao.entries().map { it.entry },
        txns = dao.allTxns(),
        expenses = dao.expenses(),
        done = dao.done(),
        podMoves = dao.podMoves(),
        goals = dao.goals(),
        plan = dao.setting(PlanJson.SETTING_KEY),
        reminders = dao.setting(ReminderPrefsJson.SETTING_KEY),
    )

    suspend fun replaceAll(data: BackupData) {
        db.withTransaction {
            dao.clearTxns()
            dao.clearEntries()
            dao.clearExpenses()
            dao.clearDone()
            dao.clearPodMoves()
            dao.clearGoals()
            // Keep original ids so transactions stay attached to their entries.
            data.entries.forEach { dao.insertEntry(it) }
            data.txns.forEach { dao.insertTxn(it) }
            data.expenses.forEach { dao.insertExpense(it) }
            data.done.forEach { dao.insertDone(it) }
            data.podMoves.forEach { dao.insertPodMove(it) }
            data.goals.forEach { dao.upsertGoal(it) }
            data.plan?.let { dao.putSetting(SettingEntity(PlanJson.SETTING_KEY, it)) }
            data.reminders?.let { dao.putSetting(SettingEntity(ReminderPrefsJson.SETTING_KEY, it)) }
        }
        onChanged()
    }
}

data class BackupData(
    val entries: List<EntryEntity>,
    val txns: List<TxnEntity>,
    val expenses: List<ExpenseEntity>,
    val done: List<DoneEntity>,
    val podMoves: List<PodMoveEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    /** Plan settings JSON, or null to keep the current plan (older backups). */
    val plan: String? = null,
    /** Reminder settings JSON, or null to keep the current ones. */
    val reminders: String? = null,
)
