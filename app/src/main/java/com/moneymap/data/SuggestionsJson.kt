package com.moneymap.data

import com.moneymap.core.ExpenseCategory
import com.moneymap.core.SuggestedExpense
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Pending expense suggestions from notifications, stored as one small JSON setting. */
object SuggestionsJson {
    const val SETTING_KEY = "suggestions"

    fun encode(list: List<SuggestedExpense>): String = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id); put("amount", s.amount); put("merchant", s.merchant)
                put("category", s.category.name); put("at", s.at.toString()); put("source", s.source)
            })
        }
    }.toString()

    fun decode(text: String?): List<SuggestedExpense> {
        val arr = text?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                SuggestedExpense(
                    id = o.getString("id"), amount = o.getLong("amount"), merchant = o.optString("merchant"),
                    category = ExpenseCategory.parse(o.optString("category")), at = LocalDateTime.parse(o.getString("at")),
                    source = o.optString("source"),
                )
            }.getOrNull()
        }
    }
}
