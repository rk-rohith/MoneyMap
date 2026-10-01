package com.moneymap.data

import com.moneymap.core.Investment
import com.moneymap.core.InvestmentKind
import com.moneymap.core.InvestmentTxn
import com.moneymap.core.Loan
import com.moneymap.core.NetWorthPoint
import com.moneymap.core.Prepayment
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Loans, investments and net-worth history, each stored as one JSON setting. */
object WealthJson {
    const val LOANS_KEY = "loans"
    const val INVESTMENTS_KEY = "investments"
    const val NET_WORTH_KEY = "net_worth_history"

    /** Settings that ride along in backups under "extras". */
    val BACKUP_KEYS = listOf(LOANS_KEY, INVESTMENTS_KEY, NET_WORTH_KEY)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun arr(text: String?) = text?.let { runCatching { JSONArray(it) }.getOrNull() }?.objects().orEmpty()

    fun encodeLoans(list: List<Loan>): String = JSONArray().apply {
        list.forEach { l ->
            put(JSONObject().apply {
                put("id", l.id); put("name", l.name); put("principal", l.principal); put("annualRate", l.annualRate)
                put("emi", l.emi); put("firstEmi", l.firstEmi.toString())
                put("prepayments", JSONArray().apply {
                    l.prepayments.forEach { p -> put(JSONObject().apply { put("date", p.date.toString()); put("amount", p.amount) }) }
                })
            })
        }
    }.toString()

    fun decodeLoans(text: String?): List<Loan> = arr(text).mapNotNull { o ->
        runCatching {
            Loan(
                id = o.getString("id"), name = o.getString("name"), principal = o.getLong("principal"),
                annualRate = o.getDouble("annualRate"), emi = o.getLong("emi"), firstEmi = LocalDate.parse(o.getString("firstEmi")),
                prepayments = o.optJSONArray("prepayments")?.objects().orEmpty()
                    .map { Prepayment(LocalDate.parse(it.getString("date")), it.getLong("amount")) },
            )
        }.getOrNull()
    }

    fun encodeInvestments(list: List<Investment>): String = JSONArray().apply {
        list.forEach { i ->
            put(JSONObject().apply {
                put("id", i.id); put("name", i.name); put("kind", i.kind.name); put("monthlySip", i.monthlySip)
                put("sipDay", i.sipDay)
                put("currentValue", i.currentValue ?: JSONObject.NULL)
                put("nav", i.nav ?: JSONObject.NULL)
                put("valueUpdated", i.valueUpdated?.toString() ?: JSONObject.NULL)
                put("txns", JSONArray().apply {
                    i.txns.forEach { t ->
                        put(JSONObject().apply {
                            put("date", t.date.toString()); put("amount", t.amount); put("units", t.units ?: JSONObject.NULL)
                        })
                    }
                })
            })
        }
    }.toString()

    fun decodeInvestments(text: String?): List<Investment> = arr(text).mapNotNull { o ->
        runCatching {
            Investment(
                id = o.getString("id"), name = o.getString("name"), kind = InvestmentKind.parse(o.optString("kind")),
                monthlySip = o.optLong("monthlySip"), sipDay = o.optInt("sipDay", 1),
                currentValue = if (o.isNull("currentValue")) null else o.getLong("currentValue"),
                nav = if (o.isNull("nav")) null else o.getDouble("nav"),
                valueUpdated = if (o.isNull("valueUpdated")) null else LocalDate.parse(o.getString("valueUpdated")),
                txns = o.optJSONArray("txns")?.objects().orEmpty().map {
                    InvestmentTxn(LocalDate.parse(it.getString("date")), it.getLong("amount"),
                        if (it.isNull("units")) null else it.getDouble("units"))
                },
            )
        }.getOrNull()
    }

    fun encodeHistory(list: List<NetWorthPoint>): String = JSONArray().apply {
        list.forEach { p -> put(JSONObject().apply { put("month", p.month.toString()); put("total", p.total) }) }
    }.toString()

    fun decodeHistory(text: String?): List<NetWorthPoint> = arr(text).mapNotNull { o ->
        runCatching { NetWorthPoint(LocalDate.parse(o.getString("month")), o.getLong("total")) }.getOrNull()
    }.sortedBy { it.month }
}
