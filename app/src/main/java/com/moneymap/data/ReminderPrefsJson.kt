package com.moneymap.data

import com.moneymap.core.ReminderPrefs
import org.json.JSONObject
import java.time.LocalTime

object ReminderPrefsJson {
    const val SETTING_KEY = "reminders"

    fun encode(p: ReminderPrefs): String = JSONObject().apply {
        put("paymentsEnabled", p.paymentsEnabled)
        put("eveningBeforeEnabled", p.eveningBeforeEnabled); put("eveningBefore", p.eveningBefore.toString())
        put("morningEnabled", p.morningEnabled); put("morning", p.morning.toString())
        put("eveningEnabled", p.eveningEnabled); put("evening", p.evening.toString())
        put("ledgerEnabled", p.ledgerEnabled); put("ledger", p.ledger.toString())
        put("weeklyEnabled", p.weeklyEnabled); put("weekly", p.weekly.toString())
    }.toString()

    /** Missing or unreadable values fall back to the defaults. */
    fun decode(text: String?): ReminderPrefs {
        val d = ReminderPrefs()
        val o = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return d
        fun time(k: String, def: LocalTime) = runCatching { LocalTime.parse(o.getString(k)) }.getOrDefault(def)
        return ReminderPrefs(
            paymentsEnabled = o.optBoolean("paymentsEnabled", d.paymentsEnabled),
            eveningBeforeEnabled = o.optBoolean("eveningBeforeEnabled", d.eveningBeforeEnabled),
            eveningBefore = time("eveningBefore", d.eveningBefore),
            morningEnabled = o.optBoolean("morningEnabled", d.morningEnabled),
            morning = time("morning", d.morning),
            eveningEnabled = o.optBoolean("eveningEnabled", d.eveningEnabled),
            evening = time("evening", d.evening),
            ledgerEnabled = o.optBoolean("ledgerEnabled", d.ledgerEnabled),
            ledger = time("ledger", d.ledger),
            weeklyEnabled = o.optBoolean("weeklyEnabled", d.weeklyEnabled),
            weekly = time("weekly", d.weekly),
        )
    }
}
