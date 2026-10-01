package com.moneymap.data

import com.moneymap.core.ExpenseCategory
import org.json.JSONObject

/** Monthly budgets per spending category, e.g. {"FOOD": 6000}. Categories without a budget are left out. */
object CategoryBudgetsJson {
    const val SETTING_KEY = "category_budgets"

    fun encode(budgets: Map<ExpenseCategory, Long>): String = JSONObject().apply {
        budgets.filterValues { it > 0 }.forEach { (c, v) -> put(c.name, v) }
    }.toString()

    fun decode(text: String?): Map<ExpenseCategory, Long> {
        val o = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
        return ExpenseCategory.entries.mapNotNull { c -> o.optLong(c.name, 0).takeIf { it > 0 }?.let { c to it } }.toMap()
    }
}
