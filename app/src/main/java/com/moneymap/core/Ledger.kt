package com.moneymap.core

import java.time.LocalDate

enum class Direction { LENT, BORROWED }

enum class TxnType { GIVEN, RECEIVED, BORROWED, REPAID }

enum class ReturnPod(val label: String) {
    JUPITER_MAIN("Jupiter main"),
    EMERGENCY("Emergency pod"),
    SRI_LANKA("Sri Lanka pod"),
    DEBT("Debt pod"),
}

enum class EntryStatus(val label: String) {
    OPEN("Open"),
    PARTLY_SETTLED("Partly settled"),
    SETTLED("Settled"),
}

data class LedgerEntry(
    val id: Long = 0,
    val person: String,
    val direction: Direction,
    val amount: Long,
    val reason: String,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val returnPod: ReturnPod = ReturnPod.JUPITER_MAIN,
    val notes: String = "",
    /** Stable key for seeded entries that other features refer to (e.g. the ₹2L loan). */
    val seedKey: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class LedgerTxn(
    val id: Long = 0,
    val entryId: Long,
    val type: TxnType,
    val amount: Long,
    val date: LocalDate,
    val note: String = "",
    /** Plan item id when this transaction was created by ticking a debt instalment. */
    val linkKey: String? = null,
)

fun Direction.openingType(): TxnType = if (this == Direction.LENT) TxnType.GIVEN else TxnType.BORROWED
fun Direction.settleType(): TxnType = if (this == Direction.LENT) TxnType.RECEIVED else TxnType.REPAID

data class EntryWithTxns(val entry: LedgerEntry, val txns: List<LedgerTxn>) {
    /** Returns / repayments, oldest first. */
    val settlements: List<LedgerTxn>
        get() = txns.filter { it.type == entry.direction.settleType() }.sortedWith(compareBy({ it.date }, { it.id }))

    val settled: Long get() = settlements.sumOf { it.amount }
    val outstanding: Long get() = (entry.amount - settled).coerceAtLeast(0)
    val status: EntryStatus
        get() = when {
            outstanding == 0L -> EntryStatus.SETTLED
            settled > 0 -> EntryStatus.PARTLY_SETTLED
            else -> EntryStatus.OPEN
        }
    val progress: Float get() = if (entry.amount <= 0) 1f else (settled.toFloat() / entry.amount).coerceIn(0f, 1f)

    fun isOverdue(today: LocalDate): Boolean =
        status != EntryStatus.SETTLED && entry.dueDate != null && entry.dueDate.isBefore(today)

    /** Timeline, oldest first. */
    val timeline: List<LedgerTxn> get() = txns.sortedWith(compareBy({ it.date }, { it.type != TxnType.GIVEN && it.type != TxnType.BORROWED }, { it.id }))
}

data class LedgerSummary(val othersOweMe: Long, val iOwe: Long) {
    val net: Long get() = othersOweMe - iOwe
}

data class PersonBalance(val person: String, val net: Long, val entries: List<EntryWithTxns>)

enum class LedgerFilter(val label: String) {
    ALL("All"), THEY_OWE("They owe me"), I_OWE("I owe"), SETTLED("Settled");

    fun matches(e: EntryWithTxns): Boolean = when (this) {
        ALL -> true
        THEY_OWE -> e.entry.direction == Direction.LENT && e.status != EntryStatus.SETTLED
        I_OWE -> e.entry.direction == Direction.BORROWED && e.status != EntryStatus.SETTLED
        SETTLED -> e.status == EntryStatus.SETTLED
    }
}

object Ledger {
    fun summary(entries: List<EntryWithTxns>) = LedgerSummary(
        othersOweMe = entries.filter { it.entry.direction == Direction.LENT }.sumOf { it.outstanding },
        iOwe = entries.filter { it.entry.direction == Direction.BORROWED }.sumOf { it.outstanding },
    )

    /** Positive: they owe me. Negative: I owe them. */
    fun netFor(entries: List<EntryWithTxns>): Long = entries.sumOf {
        if (it.entry.direction == Direction.LENT) it.outstanding else -it.outstanding
    }

    fun personKey(name: String) = name.trim().lowercase()

    /** Groups (filtered) entries by person; balance is computed over all of that person's entries. */
    fun byPerson(all: List<EntryWithTxns>, filter: LedgerFilter = LedgerFilter.ALL): List<PersonBalance> {
        val groups = all.groupBy { personKey(it.entry.person) }
        return groups.mapNotNull { (_, list) ->
            val shown = list.filter(filter::matches)
            if (shown.isEmpty()) return@mapNotNull null
            PersonBalance(
                person = list.maxByOrNull { it.entry.updatedAt }!!.entry.person.trim(),
                net = netFor(list),
                entries = shown.sortedWith(
                    compareBy<EntryWithTxns> { it.status == EntryStatus.SETTLED }.thenByDescending { it.entry.date }
                ),
            )
        }.sortedWith(compareBy<PersonBalance> { it.net == 0L }.thenByDescending { kotlin.math.abs(it.net) }.thenBy { it.person })
    }

    fun netForPerson(all: List<EntryWithTxns>, person: String): Long =
        netFor(all.filter { personKey(it.entry.person) == personKey(person) })

    /** Outstanding for one person in one direction (used by reminders: "Friend still owes ₹50,298"). */
    fun outstandingFor(all: List<EntryWithTxns>, person: String, direction: Direction): Long =
        all.filter { personKey(it.entry.person) == personKey(person) && it.entry.direction == direction }
            .sumOf { it.outstanding }

    fun people(all: List<EntryWithTxns>): List<String> =
        all.map { it.entry.person.trim() }.filter { it.isNotEmpty() }.distinctBy { personKey(it) }.sorted()

    /**
     * Friendly reminder to send to someone who owes me, listing each open item.
     * Returns null when they owe nothing.
     */
    fun reminderMessage(all: List<EntryWithTxns>, person: String): String? {
        val open = all.filter {
            personKey(it.entry.person) == personKey(person) && it.entry.direction == Direction.LENT &&
                it.status != EntryStatus.SETTLED
        }.sortedBy { it.entry.date }
        if (open.isEmpty()) return null
        val name = open.first().entry.person.trim()
        val total = open.sumOf { it.outstanding }
        val due = open.mapNotNull { it.entry.dueDate }.minOrNull()
        return buildString {
            append("Hi $name, a gentle reminder: ${formatInr(total)} is pending")
            if (due != null) append(", due ${due.long()}")
            append(".")
            if (open.size > 1 || open.first().entry.reason.isNotBlank()) {
                append("\n")
                open.forEach { e ->
                    append("\n• ${e.entry.reason.ifBlank { "Amount" }}: ${formatInr(e.outstanding)}")
                    if (e.settled > 0) append(" (of ${formatInr(e.entry.amount)})")
                }
            }
            append("\n\nThanks!")
        }
    }
}
