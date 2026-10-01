package com.moneymap.core

/** Case-insensitive search across text fields and amounts ("1500", "1,500" and "₹1,500" all match 1500). */
object Search {
    fun matches(query: String, fields: List<String?>, amounts: List<Long> = emptyList()): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        val terms = q.lowercase().split(Regex("\\s+"))
        return terms.all { term -> termMatches(term, fields, amounts) }
    }

    private fun termMatches(term: String, fields: List<String?>, amounts: List<Long>): Boolean {
        if (fields.any { it?.lowercase()?.contains(term) == true }) return true
        val symbol = Plan.profile.currencySymbol.lowercase()
        val digits = term.let { if (symbol.isNotBlank()) it.replace(symbol, "") else it }.replace("₹", "").replace(",", "")
        val n = digits.toLongOrNull() ?: return false
        return amounts.any { it == n || it.toString().startsWith(digits) }
    }
}

enum class SearchPeriod(val label: String) {
    ANY("Any time"), THIS_CYCLE("This cycle"), LAST_CYCLE("Last cycle"), LAST_90_DAYS("Last 90 days"), THIS_YEAR("This year");

    /** Inclusive date range for this period, or null for any time. */
    fun range(today: java.time.LocalDate): ClosedRange<java.time.LocalDate>? = when (this) {
        ANY -> null
        THIS_CYCLE -> Plan.cycleStartFor(today).let { it..Plan.cycleEnd(it) }
        LAST_CYCLE -> Plan.cycleStartFor(today).minusMonths(1).let { it..Plan.cycleEnd(it) }
        LAST_90_DAYS -> today.minusDays(89)..today
        THIS_YEAR -> today.withDayOfYear(1)..today.withDayOfYear(today.lengthOfYear())
    }
}

/** Narrows search results by date, amount and (for expenses) category. */
data class SearchFilter(
    val period: SearchPeriod = SearchPeriod.ANY,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val category: ExpenseCategory? = null,
) {
    val active: Boolean get() = period != SearchPeriod.ANY || minAmount != null || maxAmount != null || category != null

    fun accepts(date: java.time.LocalDate, amount: Long, today: java.time.LocalDate, category: ExpenseCategory? = null): Boolean {
        val r = period.range(today)
        if (r != null && date !in r) return false
        val a = kotlin.math.abs(amount)
        if (minAmount != null && a < minAmount) return false
        if (maxAmount != null && a > maxAmount) return false
        if (this.category != null && category != this.category) return false
        return true
    }
}
