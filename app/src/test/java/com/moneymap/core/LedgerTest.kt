package com.moneymap.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InMemoryLedgerStore : LedgerStore {
    val entries = linkedMapOf<Long, LedgerEntry>()
    val txns = linkedMapOf<Long, LedgerTxn>()
    private var nextEntry = 1L
    private var nextTxn = 1L

    override suspend fun allEntries() = entries.values.map { e -> EntryWithTxns(e, txns.values.filter { it.entryId == e.id }) }
    override suspend fun entry(id: Long) = entries[id]?.let { e -> EntryWithTxns(e, txns.values.filter { it.entryId == id }) }
    override suspend fun entryBySeedKey(key: String) = entries.values.firstOrNull { it.seedKey == key }
    override suspend fun insertEntry(entry: LedgerEntry): Long {
        val id = nextEntry++
        entries[id] = entry.copy(id = id)
        return id
    }
    override suspend fun updateEntry(entry: LedgerEntry) { entries[entry.id] = entry }
    override suspend fun deleteEntry(id: Long) {
        entries.remove(id)
        txns.values.removeAll { it.entryId == id }
    }
    override suspend fun insertTxn(txn: LedgerTxn): Long {
        val id = nextTxn++
        txns[id] = txn.copy(id = id)
        return id
    }
    override suspend fun updateTxn(txn: LedgerTxn) { txns[txn.id] = txn }
    override suspend fun deleteTxn(id: Long) { txns.remove(id) }
    override suspend fun txnByLinkKey(key: String) = txns.values.firstOrNull { it.linkKey == key }
    override suspend fun entryCount() = entries.size
}

class LedgerTest {
    private val today = LocalDate.of(2026, 9, 27)
    private val store = InMemoryLedgerStore()
    private val service = LedgerService(store) { 1_000L }

    @Test
    fun seedTotals() = runTest {
        assertTrue(service.seedIfEmpty())
        val s = Ledger.summary(store.allEntries())
        assertEquals(88_874L, s.othersOweMe)
        assertEquals(200_000L, s.iOwe)
        assertEquals(-111_126L, s.net)
        // Seeding only happens once.
        assertEquals(false, service.seedIfEmpty())
        assertEquals(9, store.entryCount())
    }

    @Test
    fun seedStatuses() = runTest {
        service.seedIfEmpty()
        val all = store.allEntries()
        fun byKey(k: String) = all.first { it.entry.seedKey == k }
        assertEquals(EntryStatus.SETTLED, byKey("goutham-borrowed").status)
        assertEquals(EntryStatus.SETTLED, byKey("samrat").status)
        assertEquals(EntryStatus.SETTLED, byKey("goutham-car-service").status)
        assertEquals(EntryStatus.OPEN, byKey("mom-cot").status)
        assertEquals(ReturnPod.SRI_LANKA, byKey("friend-loan-emi").entry.returnPod)
        assertEquals(50_298L, Ledger.outstandingFor(all, "friend", Direction.LENT))
        assertEquals(0L, Ledger.netForPerson(all, "Goutham"))
        assertEquals(-200_000L, Ledger.netForPerson(all, "Lender"))
    }

    @Test
    fun partialReturnsAndStatus() = runTest {
        val id = service.addEntry("Asha", Direction.LENT, 10_000, "Tickets", today, pod = ReturnPod.EMERGENCY)
        assertEquals(EntryStatus.OPEN, store.entry(id)!!.status)
        assertEquals(10_000L, store.entry(id)!!.outstanding)

        val s1 = service.recordSettlement(id, 4_000, today)
        assertNotNull(s1)
        assertEquals("Move ₹4,000 to Emergency pod today", s1!!.moveHint)
        assertEquals(6_000L, store.entry(id)!!.outstanding)
        assertEquals(EntryStatus.PARTLY_SETTLED, store.entry(id)!!.status)
        assertEquals(0.4f, store.entry(id)!!.progress, 0.001f)

        service.recordSettlement(id, 2_500, today)
        assertEquals(3_500L, store.entry(id)!!.outstanding)

        // Over-payment is capped at the outstanding amount.
        val s3 = service.recordSettlement(id, 99_999, today)
        assertEquals(3_500L, s3!!.amount)
        assertEquals(0L, store.entry(id)!!.outstanding)
        assertEquals(EntryStatus.SETTLED, store.entry(id)!!.status)
        assertNull(service.recordSettlement(id, 100, today))

        // Undo reopens the entry.
        val undone = service.undoLast(id)
        assertEquals(3_500L, undone!!.amount)
        assertEquals(EntryStatus.PARTLY_SETTLED, store.entry(id)!!.status)
        assertEquals(3_500L, store.entry(id)!!.outstanding)
    }

    @Test
    fun partialRepaymentOfBorrowed() = runTest {
        val id = service.addEntry("Ravi", Direction.BORROWED, 5_000, "Lunch", today)
        val s = service.recordSettlement(id, 2_000, today)
        assertNull(s!!.moveHint)
        val e = store.entry(id)!!
        assertEquals(3_000L, e.outstanding)
        assertEquals(TxnType.REPAID, e.settlements.single().type)
        assertEquals(TxnType.BORROWED, e.timeline.first().type)
    }

    @Test
    fun netPerPerson() = runTest {
        service.addEntry("Kiran", Direction.LENT, 5_000, "a", today)
        val b = service.addEntry("kiran ", Direction.BORROWED, 2_000, "b", today)
        service.addEntry("Meera", Direction.LENT, 1_000, "c", today)
        var all = store.allEntries()
        assertEquals(3_000L, Ledger.netForPerson(all, "Kiran"))
        val people = Ledger.byPerson(all)
        assertEquals(2, people.size)
        assertEquals(3_000L, people.first { it.person.equals("kiran", true) }.net)

        service.recordSettlement(b, 2_000, today)
        all = store.allEntries()
        assertEquals(5_000L, Ledger.netForPerson(all, "KIRAN"))
        assertEquals(1, Ledger.byPerson(all, LedgerFilter.SETTLED).size)
        assertEquals(2, Ledger.byPerson(all, LedgerFilter.THEY_OWE).size)
        assertEquals(0, Ledger.byPerson(all, LedgerFilter.I_OWE).size)
    }

    @Test
    fun debtTickAddsAndRemovesRepaid() = runTest {
        service.seedIfEmpty()
        val item = DefaultPlan.engine.itemsBetween(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25))
            .first { it.kind == ItemKind.DEBT }

        service.onDebtTick(item.id, item.amount, item.date, checked = true)
        var loan = service.loanProgress()!!
        assertEquals(140_000L, loan.outstanding)
        val txn = loan.settlements.single()
        assertEquals(TxnType.REPAID, txn.type)
        assertEquals(item.id, txn.linkKey)
        assertEquals(EntryStatus.PARTLY_SETTLED, loan.status)

        // Ticking twice doesn't double count.
        service.onDebtTick(item.id, item.amount, item.date, checked = true)
        assertEquals(140_000L, service.loanProgress()!!.outstanding)

        service.onDebtTick(item.id, item.amount, item.date, checked = false)
        loan = service.loanProgress()!!
        assertEquals(200_000L, loan.outstanding)
        assertTrue(loan.settlements.isEmpty())
        assertEquals(200_000L, Ledger.summary(store.allEntries()).iOwe)
    }

    @Test
    fun allInstalmentsSettleTheLoan() = runTest {
        service.seedIfEmpty()
        DefaultPlan.engine.itemsBetween(LocalDate.of(2026, 9, 27), LocalDate.of(2027, 3, 1))
            .filter { it.kind == ItemKind.DEBT }
            .forEach { service.onDebtTick(it.id, it.amount, it.date, true) }
        val loan = service.loanProgress()!!
        assertEquals(0L, loan.outstanding)
        assertEquals(EntryStatus.SETTLED, loan.status)
        assertEquals(0L, Ledger.summary(store.allEntries()).iOwe)
    }

    @Test
    fun editKeepsOpeningTxnInSync() = runTest {
        val id = service.addEntry("Zoe", Direction.LENT, 1_000, "Snacks", today)
        service.recordSettlement(id, 400, today)
        val e = store.entry(id)!!.entry
        service.editEntry(e.copy(amount = 1_500))
        val after = store.entry(id)!!
        assertEquals(1_100L, after.outstanding)
        assertEquals(1_500L, after.timeline.first().amount)
        service.deleteEntry(id)
        assertNull(store.entry(id))
        assertTrue(store.txns.isEmpty())
    }

    @Test
    fun overdueOnlyWhenOpen() = runTest {
        service.seedIfEmpty()
        val friend = store.allEntries().first { it.entry.seedKey == "friend-loan-emi" }
        assertEquals(false, friend.isOverdue(LocalDate.of(2026, 11, 15)))
        assertEquals(true, friend.isOverdue(LocalDate.of(2026, 11, 16)))
    }
}
