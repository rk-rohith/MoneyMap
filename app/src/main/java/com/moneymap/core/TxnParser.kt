package com.moneymap.core

import java.time.LocalDateTime

/** An expense guessed from a bank or UPI notification, waiting for the person to add or dismiss it. */
data class SuggestedExpense(
    val id: String,
    val amount: Long,
    val merchant: String,
    val category: ExpenseCategory,
    val at: LocalDateTime,
    val source: String,
)

/**
 * Reads bank / UPI / card notifications such as "Rs.250.00 debited from A/c XX1234 to ZOMATO" and turns debits into
 * [SuggestedExpense]s. Credits, OTPs, balance alerts and promotions are ignored. Pure logic so it can be unit tested.
 */
object TxnParser {
    private val amountRegex = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val debitWords = Regex(
        """\b(debited|spent|paid|sent|withdrawn|purchase|debit|charged|transferred to|txn of|payment of)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditWords = Regex("""\b(credited|received|refund(ed)?|cashback|deposited|reversed)\b""", RegexOption.IGNORE_CASE)
    private val ignoreWords = Regex(
        """\b(otp|one time password|verification code|due date|min(imum)? due|statement|balance is|avl bal is|""" +
            """emi of|offer|cashback up to|win|request(ed)? (money|rs|₹)|collect request|will be debited|is due)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantRegexes = listOf(
        Regex("""\b(?:to|at|towards)\s+(?:vpa\s+)?([A-Za-z0-9@&._*' -]{2,40}?)(?=\s+(?:on|via|ref|upi|from|using|avl|a/c|for)\b|[.,;](?:\s|$)|$)""",
            RegexOption.IGNORE_CASE),
        Regex("""\binfo[:\s]+([A-Za-z0-9@&._*' -]{2,40}?)(?=[.,;](?:\s|$)|$)""", RegexOption.IGNORE_CASE),
    )

    private val categoryHints: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.FOOD to listOf("zomato", "swiggy", "restaurant", "cafe", "hotel", "dominos", "pizza", "kfc", "mcdonald", "eatsure"),
        ExpenseCategory.GROCERIES to listOf("bigbasket", "blinkit", "zepto", "dmart", "grocer", "supermarket", "instamart", "jiomart", "more retail"),
        ExpenseCategory.TRANSPORT to listOf("uber", "ola", "rapido", "petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "metro", "irctc", "fastag", "parking"),
        ExpenseCategory.SHOPPING to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon", "ikea"),
        ExpenseCategory.BILLS to listOf("airtel", "jio", "vodafone", "vi ", "bsnl", "electricity", "bescom", "tata power", "recharge", "broadband", "gas", "water"),
        ExpenseCategory.HEALTH to listOf("pharmacy", "apollo", "medplus", "1mg", "pharmeasy", "hospital", "clinic", "diagnostic"),
        ExpenseCategory.FUN to listOf("netflix", "spotify", "bookmyshow", "pvr", "inox", "hotstar", "prime video", "steam", "playstation"),
    )

    fun parse(title: String?, text: String, at: LocalDateTime, source: String): SuggestedExpense? {
        val full = listOfNotNull(title, text).joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (full.isEmpty() || ignoreWords.containsMatchIn(full)) return null
        val debit = debitWords.find(full) ?: return null
        // "credited to your a/c" alone is income; "debited ... credited to merchant" is still a debit.
        val credit = creditWords.find(full)
        if (credit != null && credit.range.first < debit.range.first && !full.contains("debited", ignoreCase = true)) return null
        val amount = amountRegex.find(full)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull() ?: return null
        val rupees = amount.setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        if (rupees <= 0) return null
        val merchant = merchant(full)
        return SuggestedExpense(
            id = "${source}:${at.withSecond(0).withNano(0)}:$rupees:${merchant.lowercase()}",
            amount = rupees,
            merchant = merchant,
            category = category(merchant + " " + full),
            at = at,
            source = source,
        )
    }

    fun merchant(text: String): String {
        for (r in merchantRegexes) {
            val m = r.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', '-', '*') ?: continue
            if (m.isBlank() || m.matches(Regex("(?i)(your|a/c|ac|account|xx\\S*|\\d+).*"))) continue
            return cleanMerchant(m)
        }
        return ""
    }

    private fun cleanMerchant(raw: String): String {
        // UPI ids: "zomato.order@hdfcbank" -> "zomato order"
        val name = if ('@' in raw) raw.substringBefore('@').replace('.', ' ').replace('_', ' ') else raw
        val words = name.trim().split(' ').filter { it.isNotBlank() }
        // SHOUTED NAMES from card alerts read better in title case.
        val shouting = words.any { w -> w.any { it.isLetter() } } && words.all { it == it.uppercase() }
        return words.joinToString(" ") { w ->
            if (shouting && w.length > 1) w.lowercase().replaceFirstChar { it.uppercase() } else w
        }.take(40)
    }

    fun category(text: String): ExpenseCategory {
        val t = text.lowercase()
        return categoryHints.firstOrNull { (_, words) -> words.any { it in t } }?.first ?: ExpenseCategory.OTHER
    }

    /**
     * Adds [new] unless it repeats one already pending: the bank and the UPI app often both notify about the same
     * payment within a couple of minutes. Keeps at most [max] newest suggestions.
     */
    fun merge(pending: List<SuggestedExpense>, new: SuggestedExpense, max: Int = 50): List<SuggestedExpense> {
        val dup = pending.any {
            it.amount == new.amount && kotlin.math.abs(java.time.Duration.between(it.at, new.at).toMinutes()) <= 3
        }
        if (dup) return pending
        return (pending + new).sortedByDescending { it.at }.take(max)
    }
}
