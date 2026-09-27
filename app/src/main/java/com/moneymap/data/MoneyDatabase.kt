package com.moneymap.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyDao {
    @Transaction
    @Query("SELECT * FROM entries ORDER BY dateEpochDay DESC, id DESC")
    fun observeEntries(): Flow<List<EntryWithTxnsRow>>

    @Transaction
    @Query("SELECT * FROM entries ORDER BY dateEpochDay DESC, id DESC")
    suspend fun entries(): List<EntryWithTxnsRow>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: Long): EntryWithTxnsRow?

    @Query("SELECT * FROM entries WHERE seedKey = :key LIMIT 1")
    suspend fun entryBySeedKey(key: String): EntryEntity?

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntryRow(id: Long)

    @Query("DELETE FROM txns WHERE entryId = :entryId")
    suspend fun deleteTxnsFor(entryId: Long)

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun entryCount(): Int

    @Insert
    suspend fun insertTxn(txn: TxnEntity): Long

    @Update
    suspend fun updateTxn(txn: TxnEntity)

    @Query("DELETE FROM txns WHERE id = :id")
    suspend fun deleteTxn(id: Long)

    @Query("SELECT * FROM txns WHERE linkKey = :key LIMIT 1")
    suspend fun txnByLinkKey(key: String): TxnEntity?

    @Query("SELECT * FROM txns")
    suspend fun allTxns(): List<TxnEntity>

    @Query("SELECT * FROM expenses ORDER BY dateEpochDay DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY dateEpochDay DESC, id DESC")
    suspend fun expenses(): List<ExpenseEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE dateEpochDay BETWEEN :from AND :to")
    suspend fun spentBetween(from: Long, to: Long): Long

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query("SELECT * FROM done_items")
    fun observeDone(): Flow<List<DoneEntity>>

    @Query("SELECT * FROM done_items")
    suspend fun done(): List<DoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDone(done: DoneEntity)

    @Query("DELETE FROM done_items WHERE itemId = :itemId")
    suspend fun deleteDone(itemId: String)

    @Query("SELECT COUNT(*) FROM done_items WHERE itemId = :itemId")
    suspend fun doneCount(itemId: String): Int

    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM txns")
    suspend fun clearTxns()

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    @Query("DELETE FROM done_items")
    suspend fun clearDone()
}

@Database(
    entities = [EntryEntity::class, TxnEntity::class, ExpenseEntity::class, DoneEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        fun create(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "moneymap.db").build()
    }
}
