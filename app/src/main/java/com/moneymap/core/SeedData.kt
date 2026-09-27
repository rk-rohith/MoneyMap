package com.moneymap.core

import java.time.LocalDate

/** First-launch ledger contents. */
object SeedData {
    const val LENDER_KEY = "lender-loan"

    class Seed(val entry: LedgerEntry, val settlements: List<LedgerTxn> = emptyList())

    private val start: LocalDate = Plan.TRACK_START

    val entries: List<Seed> = listOf(
        Seed(
            LedgerEntry(
                person = Plan.LENDER_NAME, direction = Direction.BORROWED, amount = Plan.LOAN_TOTAL,
                reason = "Personal loan (interest-free)", date = LocalDate.of(2026, 9, 25),
                dueDate = Plan.DEBT_SCHEDULE.last().first, returnPod = ReturnPod.DEBT,
                notes = "Repay ₹60,000 on 25 Oct, 25 Nov, 25 Dec 2026 and ₹20,000 on 25 Jan 2027 from the Debt pod",
                seedKey = LENDER_KEY,
            )
        ),
        Seed(
            LedgerEntry(person = "Goutham", direction = Direction.BORROWED, amount = 13_000, reason = "Borrowed",
                date = start, returnPod = ReturnPod.JUPITER_MAIN, seedKey = "goutham-borrowed"),
            listOf(LedgerTxn(entryId = 0, type = TxnType.REPAID, amount = 13_000, date = start, note = "Repaid in full")),
        ),
        Seed(
            LedgerEntry(person = "Mom", direction = Direction.LENT, amount = 33_000, reason = "Cot",
                date = start, returnPod = ReturnPod.EMERGENCY, seedKey = "mom-cot")
        ),
        Seed(
            LedgerEntry(person = "Friend", direction = Direction.LENT, amount = 44_282, reason = "2 loan EMIs",
                date = start, dueDate = LocalDate.of(2026, 11, 15), returnPod = ReturnPod.SRI_LANKA,
                notes = "Needed back before the Sri Lanka trip in Dec", seedKey = "friend-loan-emi")
        ),
        Seed(
            LedgerEntry(person = "Friend", direction = Direction.LENT, amount = 6_016, reason = "2 mobile EMIs",
                date = start, dueDate = LocalDate.of(2026, 11, 15), returnPod = ReturnPod.SRI_LANKA,
                notes = "Needed back before the Sri Lanka trip in Dec", seedKey = "friend-mobile-emi")
        ),
        Seed(
            LedgerEntry(person = "Madhu", direction = Direction.LENT, amount = 3_008, reason = "HDFC card spend",
                date = start, returnPod = ReturnPod.JUPITER_MAIN, seedKey = "madhu-card")
        ),
        Seed(
            LedgerEntry(person = "Sudeep", direction = Direction.LENT, amount = 2_568, reason = "HDFC card spend",
                date = start, returnPod = ReturnPod.JUPITER_MAIN, seedKey = "sudeep-card")
        ),
        Seed(
            LedgerEntry(person = "Samrat", direction = Direction.LENT, amount = 4_518, reason = "Lent",
                date = start, returnPod = ReturnPod.JUPITER_MAIN, seedKey = "samrat"),
            listOf(LedgerTxn(entryId = 0, type = TxnType.RECEIVED, amount = 4_518, date = start, note = "Received in full")),
        ),
        Seed(
            LedgerEntry(person = "Goutham", direction = Direction.LENT, amount = 9_500, reason = "Car service",
                date = start, returnPod = ReturnPod.JUPITER_MAIN, seedKey = "goutham-car-service"),
            listOf(LedgerTxn(entryId = 0, type = TxnType.RECEIVED, amount = 9_500, date = start, note = "Received in full")),
        ),
    )
}
