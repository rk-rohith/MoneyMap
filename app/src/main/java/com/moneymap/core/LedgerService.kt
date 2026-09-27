package com.moneymap.core

import java.time.LocalDate

/** Persistence used by [LedgerService]. Implemented with Room in the app and in memory in tests. */
interface LedgerStore {
    suspend fun allEntries(): List<EntryWithTxns>
    suspend fun entry(id: Long): EntryWithTxns?
    suspend fun entryBySeedKey(key: String): LedgerEntry?
    suspend fun insertEntry(entry: LedgerEntry): Long
    suspend fun updateEntry(entry: LedgerEntry)
    suspend fun deleteEntry(id: Long)
    suspend fun insertTxn(txn: LedgerTxn): Long
    suspend fun updateTxn(txn: LedgerTxn)
    suspend fun deleteTxn(id: Long)
    suspend fun txnByLinkKey(key: String): LedgerTxn?
    suspend fun entryCount(): Int
}

/** Result of recording money coming back (or going out to repay). */
data class Settlement(
    val entryId: Long,
    val person: String,
    val direction: Direction,
    val amount: Long,
    val pod: ReturnPod,
    val outstandingAfter: Long,
) {
    /** "Move ₹X to <pod> today" — only meaningful when money came back to me. */
    val moveHint: String? get() = if (direction == Direction.LENT) "Move ${formatInr(amount)} to ${pod.label} today" else null
}

class LedgerService(
    private val store: LedgerStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun seedIfEmpty(): Boolean {
        if (store.entryCount() > 0) return false
        val t = now()
        for (seed in SeedData.entries) {
            val id = store.insertEntry(seed.entry.copy(createdAt = t, updatedAt = t))
            store.insertTxn(LedgerTxn(entryId = id, type = seed.entry.direction.openingType(),
                amount = seed.entry.amount, date = seed.entry.date, note = seed.entry.reason))
            for (s in seed.settlements) {
                store.insertTxn(s.copy(entryId = id))
            }
        }
        return true
    }

    suspend fun addEntry(
        person: String,
        direction: Direction,
        amount: Long,
        reason: String,
        date: LocalDate,
        dueDate: LocalDate? = null,
        pod: ReturnPod = ReturnPod.JUPITER_MAIN,
        notes: String = "",
    ): Long {
        require(amount > 0) { "Amount must be positive" }
        require(person.isNotBlank()) { "Person is required" }
        val t = now()
        val id = store.insertEntry(
            LedgerEntry(person = person.trim(), direction = direction, amount = amount, reason = reason.trim(),
                date = date, dueDate = dueDate, returnPod = pod, notes = notes.trim(), createdAt = t, updatedAt = t)
        )
        store.insertTxn(LedgerTxn(entryId = id, type = direction.openingType(), amount = amount, date = date,
            note = reason.trim()))
        return id
    }

    /** Saves edits. The opening transaction follows the entry's amount, date and direction. */
    suspend fun editEntry(updated: LedgerEntry) {
        require(updated.amount > 0) { "Amount must be positive" }
        val existing = store.entry(updated.id) ?: return
        store.updateEntry(updated.copy(person = updated.person.trim(), updatedAt = now(), createdAt = existing.entry.createdAt))
        val opening = existing.txns.firstOrNull { it.type == TxnType.GIVEN || it.type == TxnType.BORROWED }
        if (opening != null) {
            store.updateTxn(opening.copy(type = updated.direction.openingType(), amount = updated.amount,
                date = updated.date, note = updated.reason))
        }
        if (existing.entry.direction != updated.direction) {
            // Flip settlement types so partial returns stay attached to the entry.
            existing.settlements.forEach { store.updateTxn(it.copy(type = updated.direction.settleType())) }
        }
    }

    suspend fun deleteEntry(id: Long) = store.deleteEntry(id)

    /** Records a full or partial return/repayment. The amount is capped at what is outstanding. */
    suspend fun recordSettlement(
        entryId: Long,
        amount: Long,
        date: LocalDate,
        note: String = "",
        linkKey: String? = null,
    ): Settlement? {
        val e = store.entry(entryId) ?: return null
        val pay = amount.coerceAtMost(e.outstanding)
        if (pay <= 0) return null
        store.insertTxn(LedgerTxn(entryId = entryId, type = e.entry.direction.settleType(), amount = pay,
            date = date, note = note.trim(), linkKey = linkKey))
        store.updateEntry(e.entry.copy(updatedAt = now()))
        return Settlement(entryId, e.entry.person, e.entry.direction, pay, e.entry.returnPod, e.outstanding - pay)
    }

    /** Removes the most recent return/repayment. Returns it so callers can unlink plan ticks. */
    suspend fun undoLast(entryId: Long): LedgerTxn? {
        val e = store.entry(entryId) ?: return null
        val last = e.settlements.lastOrNull() ?: return null
        store.deleteTxn(last.id)
        store.updateEntry(e.entry.copy(updatedAt = now()))
        return last
    }

    /**
     * Ticking a debt instalment on the Month screen adds a REPAID transaction to the ₹2L loan;
     * unticking removes it. Idempotent in both directions.
     */
    suspend fun onDebtTick(itemId: String, amount: Long, date: LocalDate, checked: Boolean) {
        val existing = store.txnByLinkKey(itemId)
        if (checked) {
            if (existing != null) return
            val loan = store.entryBySeedKey(SeedData.LENDER_KEY) ?: return
            val loanWithTxns = store.entry(loan.id) ?: return
            val pay = amount.coerceAtMost(loanWithTxns.outstanding)
            if (pay <= 0) return
            store.insertTxn(LedgerTxn(entryId = loan.id, type = TxnType.REPAID, amount = pay, date = date,
                note = "Instalment (ticked on Month screen)", linkKey = itemId))
            store.updateEntry(loan.copy(updatedAt = now()))
        } else if (existing != null) {
            store.deleteTxn(existing.id)
        }
    }

    suspend fun loanProgress(): EntryWithTxns? {
        val loan = store.entryBySeedKey(SeedData.LENDER_KEY) ?: return null
        return store.entry(loan.id)
    }
}
