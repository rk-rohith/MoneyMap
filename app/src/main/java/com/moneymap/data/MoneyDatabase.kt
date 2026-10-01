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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

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

    @Query("SELECT * FROM pod_moves ORDER BY dateEpochDay DESC, id DESC")
    fun observePodMoves(): Flow<List<PodMoveEntity>>

    @Query("SELECT * FROM pod_moves")
    suspend fun podMoves(): List<PodMoveEntity>

    @Insert
    suspend fun insertPodMove(move: PodMoveEntity): Long

    @Query("DELETE FROM pod_moves WHERE id = :id")
    suspend fun deletePodMove(id: Long)

    @Query("DELETE FROM pod_moves WHERE linkKey = :key")
    suspend fun deletePodMovesByLink(key: String)

    @Query("DELETE FROM pod_moves")
    suspend fun clearPodMoves()

    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals")
    suspend fun goals(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalEntity): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM goals")
    suspend fun clearGoals()

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observeSetting(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun setting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: SettingEntity)
}

/** v1 → v2: expense categories, pod balances, goals and editable plan settings. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expenses` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pod_moves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`pod` TEXT NOT NULL, `amount` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `linkKey` TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pod_moves_linkKey` ON `pod_moves` (`linkKey`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `target` INTEGER NOT NULL, `targetEpochDay` INTEGER, " +
                "`pod` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
        )
    }
}

/** Every migration, oldest first. Add new ones here so the app and the migration test both pick them up. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

@Database(
    entities = [
        EntryEntity::class, TxnEntity::class, ExpenseEntity::class, DoneEntity::class,
        PodMoveEntity::class, GoalEntity::class, SettingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        fun create(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "moneymap.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
