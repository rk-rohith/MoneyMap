package com.moneymap.data

import androidx.room.withTransaction
import com.moneymap.core.Direction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.LedgerEntry
import com.moneymap.core.LedgerService
import com.moneymap.core.Plan
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

    suspend fun init() {
        if (ledger.seedIfEmpty()) onChanged()
    }

    // Snapshots used by notifications.
    suspend fun entriesNow(): List<EntryWithTxns> = dao.entries().map { it.toDomain() }
    suspend fun doneNow(): Set<String> = dao.done().map { it.itemId }.toSet()
    suspend fun isDone(itemId: String): Boolean = dao.doneCount(itemId) > 0
    suspend fun spentInCycle(today: LocalDate): Long {
        val start = Plan.cycleStartFor(today)
        return dao.spentBetween(start.toEpochDay(), Plan.cycleEnd(start).toEpochDay())
    }

    /** Ticks or unticks a plan item. Debt instalments also update the ₹2L loan in the ledger. */
    suspend fun setDone(itemId: String, done: Boolean) {
        db.withTransaction {
            if (done) dao.insertDone(DoneEntity(itemId, System.currentTimeMillis())) else dao.deleteDone(itemId)
            if (Plan.isDebtItem(itemId)) {
                val item = Plan.itemById(itemId)
                if (item != null) ledger.onDebtTick(item.id, item.amount, item.date, done)
            }
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
            ledger.loanProgress()?.takeIf { it.entry.id == id }?.settlements?.forEach { t ->
                t.linkKey?.let { dao.deleteDone(it) }
            }
            ledger.deleteEntry(id)
        }
        onChanged()
    }

    suspend fun recordSettlement(entryId: Long, amount: Long, date: LocalDate, note: String): Settlement? =
        ledger.recordSettlement(entryId, amount, date, note).also { onChanged() }

    suspend fun undoLast(entryId: Long): Boolean {
        val removed = db.withTransaction {
            val t = ledger.undoLast(entryId)
            t?.linkKey?.let { dao.deleteDone(it) }
            t
        }
        onChanged()
        return removed != null
    }

    suspend fun addExpense(amount: Long, note: String, date: LocalDate) {
        dao.insertExpense(ExpenseEntity(amount = amount, note = note.trim(), dateEpochDay = date.toEpochDay(),
            createdAt = System.currentTimeMillis()))
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

    suspend fun snapshot(): BackupData = BackupData(
        entries = dao.entries().map { it.entry },
        txns = dao.allTxns(),
        expenses = dao.expenses(),
        done = dao.done(),
    )

    suspend fun replaceAll(data: BackupData) {
        db.withTransaction {
            dao.clearTxns()
            dao.clearEntries()
            dao.clearExpenses()
            dao.clearDone()
            // Keep original ids so transactions stay attached to their entries.
            data.entries.forEach { dao.insertEntry(it) }
            data.txns.forEach { dao.insertTxn(it) }
            data.expenses.forEach { dao.insertExpense(it) }
            data.done.forEach { dao.insertDone(it) }
        }
        onChanged()
    }
}

data class BackupData(
    val entries: List<EntryEntity>,
    val txns: List<TxnEntity>,
    val expenses: List<ExpenseEntity>,
    val done: List<DoneEntity>,
)
