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
        val digits = term.replace("₹", "").replace(",", "")
        val n = digits.toLongOrNull() ?: return false
        return amounts.any { it == n || it.toString().startsWith(digits) }
    }
}
