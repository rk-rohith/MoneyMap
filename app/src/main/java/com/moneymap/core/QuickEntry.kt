package com.moneymap.core

import java.time.LocalDate

data class ParsedEntry(val amount: Long?, val note: String, val category: ExpenseCategory, val date: LocalDate)

/**
 * Reads one line such as "250 lunch zomato", "petrol 1,500", "₹90 chai yesterday" or "movie 450 fun" into an
 * expense: the first number is the amount, "today"/"yesterday" set the date, a category name or a known word
 * picks the category, and the remaining words become the note.
 */
object QuickEntry {
    private val amountToken = Regex("""^(?:₹|rs\.?|inr)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?:/-|k)?$""", RegexOption.IGNORE_CASE)

    private val words: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.FOOD to listOf("lunch", "dinner", "breakfast", "coffee", "tea", "chai", "snacks", "snack", "food",
            "biryani", "juice", "meal", "dining", "restaurant", "eat"),
        ExpenseCategory.GROCERIES to listOf("groceries", "grocery", "milk", "vegetables", "veggies", "fruits", "rice",
            "eggs", "bread", "provisions"),
        ExpenseCategory.TRANSPORT to listOf("petrol", "diesel", "fuel", "cab", "auto", "bus", "train", "metro", "taxi",
            "parking", "toll", "transport", "flight"),
        ExpenseCategory.SHOPPING to listOf("shopping", "clothes", "shoes", "shirt", "gift", "gadget"),
        ExpenseCategory.BILLS to listOf("bill", "bills", "recharge", "electricity", "wifi", "internet", "mobile", "dth"),
        ExpenseCategory.HEALTH to listOf("medicine", "medicines", "doctor", "pharmacy", "hospital", "health", "gym", "tablets"),
        ExpenseCategory.FUN to listOf("movie", "movies", "fun", "party", "drinks", "game", "games", "concert", "outing"),
    )

    fun parse(line: String, today: LocalDate): ParsedEntry {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        var amount: Long? = null
        var date = today
        var category: ExpenseCategory? = null
        val noteWords = mutableListOf<String>()
        for (t in tokens) {
            val lower = t.lowercase().trimEnd('.', ',')
            val m = amountToken.find(lower)
            when {
                amount == null && m != null -> {
                    val value = m.groupValues[1].replace(",", "").toBigDecimalOrNull()
                    val k = lower.endsWith("k")
                    amount = value?.let { (if (k) it.multiply(1000.toBigDecimal()) else it)
                        .setScale(0, java.math.RoundingMode.HALF_UP).toLong() }?.takeIf { it > 0 }
                    if (amount == null) noteWords += t
                }
                lower == "today" -> date = today
                lower == "yesterday" -> date = today.minusDays(1)
                category == null && ExpenseCategory.entries.any { it.name.lowercase() == lower } ->
                    category = ExpenseCategory.entries.first { it.name.lowercase() == lower }
                else -> noteWords += t
            }
        }
        val note = noteWords.joinToString(" ")
        val guess = category ?: guessCategory(note)
        return ParsedEntry(amount, note, guess, date)
    }

    fun guessCategory(note: String): ExpenseCategory {
        val lower = note.lowercase()
        val tokens = lower.split(Regex("[^a-z0-9]+")).toSet()
        words.firstOrNull { (_, list) -> list.any { it in tokens } }?.let { return it.first }
        return TxnParser.category(lower)
    }
}
