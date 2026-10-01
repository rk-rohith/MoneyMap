package com.moneymap.data

import com.moneymap.core.Profile
import org.json.JSONObject
import java.time.LocalDate

object ProfileJson {
    const val SETTING_KEY = "profile"

    fun encode(p: Profile): String = JSONObject().apply {
        put("salaryDay", p.salaryDay)
        put("salaryAccount", p.salaryAccount)
        put("spendAccount", p.spendAccount)
        put("trackStart", p.trackStart.toString())
        put("lenderName", p.lenderName)
        put("loanTotal", p.loanTotal)
        put("goalPod", p.goalPod)
        put("setupDone", p.setupDone)
        put("weekendSalaryEarly", p.weekendSalaryEarly)
        put("currencySymbol", p.currencySymbol)
        put("indianGrouping", p.indianGrouping)
    }.toString()

    /** Missing values fall back to the original setup's defaults; unreadable text gives null. */
    fun decode(text: String?): Profile? {
        val o = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val d = Profile()
        return runCatching {
            Profile(
                salaryDay = o.optInt("salaryDay", d.salaryDay).coerceIn(1, 28),
                salaryAccount = o.optString("salaryAccount", d.salaryAccount).ifBlank { d.salaryAccount },
                spendAccount = o.optString("spendAccount", d.spendAccount).ifBlank { d.spendAccount },
                trackStart = runCatching { LocalDate.parse(o.getString("trackStart")) }.getOrDefault(d.trackStart),
                lenderName = o.optString("lenderName", d.lenderName).ifBlank { d.lenderName },
                loanTotal = o.optLong("loanTotal", d.loanTotal).coerceAtLeast(0),
                goalPod = o.optString("goalPod", d.goalPod).ifBlank { d.goalPod },
                setupDone = o.optBoolean("setupDone", true),
                weekendSalaryEarly = o.optBoolean("weekendSalaryEarly", false),
                currencySymbol = o.optString("currencySymbol", d.currencySymbol).take(4),
                indianGrouping = o.optBoolean("indianGrouping", d.indianGrouping),
            )
        }.getOrNull()
    }
}
