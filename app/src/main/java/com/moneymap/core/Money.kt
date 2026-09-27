package com.moneymap.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Formats a rupee amount using the Indian grouping system: ₹1,68,000 / ₹12,34,567.
 * Amounts are whole rupees.
 */
fun formatInr(amount: Long, withSymbol: Boolean = true): String {
    val digits = abs(amount).toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val sb = StringBuilder()
        rest.reversed().chunked(2).forEachIndexed { i, chunk ->
            if (i > 0) sb.append(',')
            sb.append(chunk)
        }
        sb.reverse().toString() + "," + last3
    }
    val sign = if (amount < 0) "-" else ""
    return sign + (if (withSymbol) "₹" else "") + grouped
}

/** Parses user input such as "1,68,000", "₹ 2500" or "3008". Returns null if not a positive whole amount. */
fun parseAmount(input: String): Long? {
    val cleaned = input.replace("₹", "").replace(",", "").trim()
    if (cleaned.isEmpty()) return null
    val value = cleaned.toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    return value.setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val dayDate = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

fun LocalDate.short(): String = format(shortDate)
fun LocalDate.long(): String = format(longDate)
fun LocalDate.withDay(): String = format(dayDate)
fun LocalDate.monthYear(): String = format(monthYear)
