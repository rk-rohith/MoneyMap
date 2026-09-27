package com.moneymap.data

import com.moneymap.core.DebtInstalment
import com.moneymap.core.Flow
import com.moneymap.core.OneOff
import com.moneymap.core.PlanConfig
import com.moneymap.core.PlanSettings
import com.moneymap.core.PlanVersion
import com.moneymap.core.RecurringItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** JSON form of [PlanSettings], used for storage and backups. */
object PlanJson {
    const val SETTING_KEY = "plan"

    fun toJson(s: PlanSettings): JSONObject = JSONObject().apply {
        put("versions", JSONArray().apply {
            s.sortedVersions.forEach { v ->
                put(JSONObject().apply {
                    put("from", v.from.toString())
                    put("salary", v.config.salary)
                    put("spendBudget", v.config.spendBudget)
                    put("items", JSONArray().apply { v.config.items.forEach { put(itemJson(it)) } })
                })
            }
        })
        put("debt", JSONArray().apply {
            s.debt.sortedBy { it.date }.forEach { d ->
                put(JSONObject().apply { put("date", d.date.toString()); put("amount", d.amount) })
            }
        })
        put("oneOffs", JSONArray().apply {
            s.oneOffs.sortedBy { it.date }.forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id); put("title", o.title); put("amount", o.amount); put("date", o.date.toString())
                    put("spreadCycles", o.spreadCycles); put("fundFrom", o.fundFrom.toString())
                })
            }
        })
    }

    private fun itemJson(i: RecurringItem) = JSONObject().apply {
        put("id", i.id); put("title", i.title); put("amount", i.amount); put("day", i.day)
        put("flow", i.flow.name); put("account", i.account); put("autopay", i.autopay); put("pod", i.pod)
        put("startDate", i.startDate?.toString() ?: JSONObject.NULL)
        put("endDate", i.endDate?.toString() ?: JSONObject.NULL)
    }

    fun fromJson(o: JSONObject): PlanSettings {
        val versions = o.getJSONArray("versions").objects().map { v ->
            PlanVersion(
                from = LocalDate.parse(v.getString("from")),
                config = PlanConfig(
                    salary = v.getLong("salary"),
                    spendBudget = v.getLong("spendBudget"),
                    items = v.getJSONArray("items").objects().map { i ->
                        RecurringItem(
                            id = i.getString("id"),
                            title = i.getString("title"),
                            amount = i.getLong("amount"),
                            day = i.getInt("day"),
                            flow = Flow.valueOf(i.getString("flow")),
                            account = i.optString("account"),
                            autopay = i.optBoolean("autopay"),
                            pod = i.optString("pod"),
                            startDate = i.dateOrNull("startDate"),
                            endDate = i.dateOrNull("endDate"),
                        )
                    },
                ),
            )
        }
        val debt = o.optJSONArray("debt")?.objects().orEmpty().map {
            DebtInstalment(LocalDate.parse(it.getString("date")), it.getLong("amount"))
        }
        val oneOffs = o.optJSONArray("oneOffs")?.objects().orEmpty().map {
            val date = LocalDate.parse(it.getString("date"))
            OneOff(
                id = it.getString("id"),
                title = it.getString("title"),
                amount = it.getLong("amount"),
                date = date,
                spreadCycles = it.optInt("spreadCycles", 1),
                fundFrom = it.dateOrNull("fundFrom") ?: date,
            )
        }
        return PlanSettings(versions, debt, oneOffs)
    }

    fun encode(s: PlanSettings): String = toJson(s).toString()
    fun decode(text: String): PlanSettings = fromJson(JSONObject(text))

    private fun JSONObject.dateOrNull(k: String): LocalDate? =
        if (!has(k) || isNull(k)) null else LocalDate.parse(getString(k))

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
