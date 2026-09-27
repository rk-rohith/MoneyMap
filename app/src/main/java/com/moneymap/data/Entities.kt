package com.moneymap.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.moneymap.core.Direction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.Goal
import com.moneymap.core.LedgerEntry
import com.moneymap.core.LedgerTxn
import com.moneymap.core.PodMove
import com.moneymap.core.ReturnPod
import com.moneymap.core.TxnType
import java.time.LocalDate

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val direction: String,
    val amount: Long,
    val reason: String,
    val dateEpochDay: Long,
    val dueEpochDay: Long?,
    val returnPod: String,
    val notes: String,
    val seedKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "txns", indices = [Index("entryId"), Index("linkKey")])
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val type: String,
    val amount: Long,
    val dateEpochDay: Long,
    val note: String,
    val linkKey: String?,
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val note: String,
    val dateEpochDay: Long,
    val createdAt: Long,
    val category: String = ExpenseCategory.OTHER.name,
)

@Entity(tableName = "pod_moves", indices = [Index("linkKey")])
data class PodMoveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pod: String,
    val amount: Long,
    val dateEpochDay: Long,
    val note: String,
    val linkKey: String?,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    val targetEpochDay: Long?,
    val pod: String,
    val createdAt: Long,
)

/** Simple key/value store; holds the plan settings as JSON. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "done_items")
data class DoneEntity(
    @PrimaryKey val itemId: String,
    val doneAt: Long,
)

data class EntryWithTxnsRow(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId") val txns: List<TxnEntity>,
)

data class Expense(
    val id: Long = 0,
    val amount: Long,
    val note: String,
    val date: LocalDate,
    val createdAt: Long = 0,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
)

fun EntryEntity.toDomain() = LedgerEntry(
    id = id,
    person = person,
    direction = Direction.valueOf(direction),
    amount = amount,
    reason = reason,
    date = LocalDate.ofEpochDay(dateEpochDay),
    dueDate = dueEpochDay?.let(LocalDate::ofEpochDay),
    returnPod = runCatching { ReturnPod.valueOf(returnPod) }.getOrDefault(ReturnPod.JUPITER_MAIN),
    notes = notes,
    seedKey = seedKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun LedgerEntry.toEntity() = EntryEntity(
    id = id,
    person = person,
    direction = direction.name,
    amount = amount,
    reason = reason,
    dateEpochDay = date.toEpochDay(),
    dueEpochDay = dueDate?.toEpochDay(),
    returnPod = returnPod.name,
    notes = notes,
    seedKey = seedKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TxnEntity.toDomain() = LedgerTxn(
    id = id,
    entryId = entryId,
    type = TxnType.valueOf(type),
    amount = amount,
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
    linkKey = linkKey,
)

fun LedgerTxn.toEntity() = TxnEntity(
    id = id,
    entryId = entryId,
    type = type.name,
    amount = amount,
    dateEpochDay = date.toEpochDay(),
    note = note,
    linkKey = linkKey,
)

fun EntryWithTxnsRow.toDomain() = EntryWithTxns(entry.toDomain(), txns.map { it.toDomain() })

fun ExpenseEntity.toDomain() =
    Expense(id, amount, note, LocalDate.ofEpochDay(dateEpochDay), createdAt, ExpenseCategory.parse(category))

fun Expense.toEntity() = ExpenseEntity(id, amount, note, date.toEpochDay(), createdAt, category.name)

fun PodMoveEntity.toDomain() = PodMove(id, pod, amount, LocalDate.ofEpochDay(dateEpochDay), note, linkKey)

fun PodMove.toEntity() = PodMoveEntity(id, pod, amount, date.toEpochDay(), note, linkKey)

fun GoalEntity.toDomain() = Goal(id, name, target, targetEpochDay?.let(LocalDate::ofEpochDay), pod, createdAt)

fun Goal.toEntity() = GoalEntity(id, name, target, targetDate?.toEpochDay(), pod, createdAt)
