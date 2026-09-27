package com.moneymap.data

import androidx.room.withTransaction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.LedgerEntry
import com.moneymap.core.LedgerStore
import com.moneymap.core.LedgerTxn

class RoomLedgerStore(private val db: MoneyDatabase) : LedgerStore {
    private val dao = db.dao()

    override suspend fun allEntries(): List<EntryWithTxns> = dao.entries().map { it.toDomain() }
    override suspend fun entry(id: Long): EntryWithTxns? = dao.entry(id)?.toDomain()
    override suspend fun entryBySeedKey(key: String): LedgerEntry? = dao.entryBySeedKey(key)?.toDomain()
    override suspend fun insertEntry(entry: LedgerEntry): Long = dao.insertEntry(entry.toEntity().copy(id = 0))
    override suspend fun updateEntry(entry: LedgerEntry) = dao.updateEntry(entry.toEntity())
    override suspend fun deleteEntry(id: Long) = db.withTransaction {
        dao.deleteTxnsFor(id)
        dao.deleteEntryRow(id)
    }
    override suspend fun insertTxn(txn: LedgerTxn): Long = dao.insertTxn(txn.toEntity().copy(id = 0))
    override suspend fun updateTxn(txn: LedgerTxn) = dao.updateTxn(txn.toEntity())
    override suspend fun deleteTxn(id: Long) = dao.deleteTxn(id)
    override suspend fun txnByLinkKey(key: String): LedgerTxn? = dao.txnByLinkKey(key)?.toDomain()
    override suspend fun entryCount(): Int = dao.entryCount()
}
