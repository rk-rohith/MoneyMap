package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuickEntryTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun amountFirstWithMerchantCategory() {
        assertEquals(ParsedEntry(250, "lunch zomato", ExpenseCategory.FOOD, today), QuickEntry.parse("250 lunch zomato", today))
    }

    @Test
    fun amountAnywhereSymbolsAndK() {
        assertEquals(ParsedEntry(1_500, "petrol", ExpenseCategory.TRANSPORT, today), QuickEntry.parse("petrol ₹1,500", today))
        assertEquals(ParsedEntry(2_000, "shoes", ExpenseCategory.SHOPPING, today), QuickEntry.parse("shoes 2k", today))
        assertEquals(90L, QuickEntry.parse("Rs.90 chai", today).amount)
        assertEquals(500L, QuickEntry.parse("500/- medicine", today).amount)
    }

    @Test
    fun dateWordsAndExplicitCategory() {
        val p = QuickEntry.parse("450 movie with friends yesterday", today)
        assertEquals(LocalDate.of(2026, 10, 2), p.date)
        assertEquals("movie with friends", p.note)
        assertEquals(ExpenseCategory.FUN, p.category)
        assertEquals(ExpenseCategory.BILLS, QuickEntry.parse("300 swiggy bills", today).category)
        // Only the first number is the amount; later ones stay in the note.
        assertEquals("2 coffees", QuickEntry.parse("240 2 coffees", today).note)
    }

    @Test
    fun unknownAndMissing() {
        val p = QuickEntry.parse("something odd", today)
        assertNull(p.amount)
        assertEquals(ExpenseCategory.OTHER, p.category)
        assertNull(QuickEntry.parse("", today).amount)
    }
}
