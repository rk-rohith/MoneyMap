package com.moneymap.core

import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** A credit card statement: pay [amount] by [dueDate]. Shown on Month as a bill with reminders. */
data class CardBill(
    val id: String,
    val card: String,
    val amount: Long,
    val minimumDue: Long? = null,
    val dueDate: LocalDate,
)

/**
 * Reads credit card statement notifications, e.g. "HDFC Bank Credit Card XX1234 statement: Total due Rs.12,345.00,
 * Min due Rs.620, due by 15-Oct-2026". Only statements are read; spends and payments are left to [TxnParser].
 */
object CardBillParser {
    private val statementWords = Regex("""\b(statement|total (amount|amt\.?) due|total due|amount due|bill (is )?generated|bill of)\b""",
        RegexOption.IGNORE_CASE)
    private val paidWords = Regex("""\b(payment (of .{0,20})?(received|credited|successful)|thank you for (your )?payment|has been paid)\b""",
        RegexOption.IGNORE_CASE)
    private const val MONEY = """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val totalRegex = Regex("""(?:total\s*(?:amount|amt\.?)?\s*due|amount\s*due|total\s*due|bill\s*(?:amount|of))\D{0,12}?$MONEY""",
        RegexOption.IGNORE_CASE)
    private val minRegex = Regex("""min(?:imum)?\.?\s*(?:amount|amt\.?)?\s*due\D{0,12}?$MONEY""", RegexOption.IGNORE_CASE)
    private val anyMoney = Regex(MONEY, RegexOption.IGNORE_CASE)
    private val dueRegex = Regex(
        """(?:due\s*(?:date|by|on)?|pay\s*by|payable\s*by)\s*(?:is|:|-|of)?\s*([0-9]{1,2}[-/ .][A-Za-z]{3,9}[-/ .,]*(?:[0-9]{2,4})?|[0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{2,4}|[A-Za-z]{3,9}\s+[0-9]{1,2},?\s*(?:[0-9]{4})?)""",
        RegexOption.IGNORE_CASE)
    private val last4 = Regex("""(?:xx+|\*+|ending(?: in)?|card no\.?)\s*([0-9]{4})""", RegexOption.IGNORE_CASE)
    private val bankRegex = Regex("""\b(hdfc|icici|sbi|axis|kotak|amex|american express|hsbc|citi|idfc|yes bank|indusind|rbl|au|onecard|bob|federal|scapia|jupiter)\b""",
        RegexOption.IGNORE_CASE)

    fun parse(title: String?, text: String, received: LocalDate): CardBill? {
        val full = listOfNotNull(title, text).joinToString(" ").replace(Regex("\\s+"), " ")
        if (!statementWords.containsMatchIn(full) || paidWords.containsMatchIn(full)) return null
        if (!full.contains("card", ignoreCase = true)) return null
        val amount = (totalRegex.find(full) ?: anyMoney.find(full))?.money() ?: return null
        val due = dueRegex.find(full)?.groupValues?.get(1)?.let { parseDate(it.trim().trimEnd(',', '.'), received) } ?: return null
        val minimum = minRegex.find(full)?.money()
        val digits = last4.find(full)?.groupValues?.get(1)
        val bank = bankRegex.find(full)?.value?.let { b -> if (b.length <= 4) b.uppercase() else b.replaceFirstChar { it.uppercase() } }
        val card = listOfNotNull(bank ?: "Credit", "card", digits?.let { "••$it" }).joinToString(" ")
        val key = (digits ?: bank ?: "card").lowercase().replace(" ", "")
        return CardBill(id = "$key-$due", card = card, amount = amount, minimumDue = minimum?.takeIf { it < amount }, dueDate = due)
    }

    private fun MatchResult.money(): Long? =
        groupValues[1].replace(",", "").toBigDecimalOrNull()?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()?.takeIf { it > 0 }

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)

    private val withYear = listOf("d-MMM-yyyy", "d-MMM-yy", "d MMM yyyy", "d MMM yy", "d/M/yyyy", "d/M/yy", "d-M-yyyy", "d-M-yy",
        "d.M.yyyy", "d.M.yy", "MMM d, yyyy", "MMM d yyyy", "d-MMMM-yyyy", "d MMMM yyyy", "MMMM d, yyyy").map(::formatter)
    private val noYear = listOf("d-MMM", "d MMM", "MMM d", "d MMMM", "MMMM d").map(::formatter)

    /** Dates with and without a year; a date without one is the next such day on or after [received]. */
    fun parseDate(raw: String, received: LocalDate): LocalDate? {
        val s = raw.replace(Regex("\\s+"), " ").replace(Regex("(\\d)(st|nd|rd|th)\\b"), "$1")
        for (f in withYear) runCatching { return LocalDate.parse(s, f) }
        for (f in noYear) {
            val md = runCatching { MonthDay.parse(s, f) }.getOrNull() ?: continue
            val thisYear = md.atYear(received.year)
            return if (thisYear.isBefore(received)) md.atYear(received.year + 1) else thisYear
        }
        return null
    }

    /**
     * Adds [bill], replacing one for the same card and due date (statements are sometimes re-sent with a corrected
     * total). Bills due more than 60 days ago are dropped.
     */
    fun merge(bills: List<CardBill>, bill: CardBill, today: LocalDate): List<CardBill> =
        (bills.filter { it.id != bill.id } + bill).filter { !it.dueDate.isBefore(today.minusDays(60)) }.sortedBy { it.dueDate }
}
