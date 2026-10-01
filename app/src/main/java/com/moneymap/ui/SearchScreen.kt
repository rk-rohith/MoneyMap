package com.moneymap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.moneymap.core.Direction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.PodMove
import com.moneymap.core.Search
import com.moneymap.core.SearchFilter
import com.moneymap.core.SearchPeriod
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.parseAmount
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.moneymap.core.formatInr
import com.moneymap.core.long
import com.moneymap.data.Expense
import com.moneymap.ui.theme.MoneyColors
import java.time.LocalDate
import kotlin.math.abs

enum class SearchScope(val label: String) { ALL("All"), EXPENSES("Expenses"), PEOPLE("People"), PODS("Pods") }

private sealed class Hit(val date: LocalDate) {
    class ExpenseHit(val e: Expense) : Hit(e.date)
    class EntryHit(val e: EntryWithTxns) : Hit(e.entry.date)
    class PodHit(val m: PodMove) : Hit(m.date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    entries: List<EntryWithTxns>,
    expenses: List<Expense>,
    podMoves: List<PodMove>,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
    initialQuery: String = "",
    today: LocalDate = LocalDate.now(),
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var scope by rememberSaveable { mutableStateOf(SearchScope.ALL) }
    var period by rememberSaveable { mutableStateOf(SearchPeriod.ANY) }
    var minText by rememberSaveable { mutableStateOf("") }
    var maxText by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<ExpenseCategory?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val filter = SearchFilter(period, parseAmount(minText), parseAmount(maxText), category)
    // With a filter set, an empty query lists everything the filter lets through.
    fun matches(fields: List<String?>, amounts: List<Long>) =
        if (query.isBlank()) filter.active else Search.matches(query, fields, amounts)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val hits = remember(query, scope, filter, entries, expenses, podMoves, today) {
        if (query.isBlank() && !filter.active) emptyList() else buildList<Hit> {
            if (scope == SearchScope.ALL || scope == SearchScope.EXPENSES) {
                expenses.filter {
                    filter.accepts(it.date, it.amount, today, it.category) &&
                        matches(listOf(it.note, it.category.label), listOf(it.amount))
                }.forEach { add(Hit.ExpenseHit(it)) }
            }
            // A category only applies to expenses.
            if ((scope == SearchScope.ALL || scope == SearchScope.PEOPLE) && filter.category == null) {
                entries.filter { e ->
                    filter.accepts(e.entry.date, e.entry.amount, today) &&
                        matches(listOf(e.entry.person, e.entry.reason, e.entry.notes, e.status.label) + e.txns.map { it.note },
                            listOf(e.entry.amount, e.outstanding) + e.txns.map { it.amount })
                }.forEach { add(Hit.EntryHit(it)) }
            }
            if ((scope == SearchScope.ALL || scope == SearchScope.PODS) && filter.category == null) {
                podMoves.filter {
                    filter.accepts(it.date, it.amount, today) && matches(listOf(it.pod, it.note), listOf(abs(it.amount)))
                }.forEach { add(Hit.PodHit(it)) }
            }
        }.sortedByDescending { it.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Name, note, category or amount") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchScope.entries.forEach { s ->
                        FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) })
                    }
                    FilterChip(selected = showFilters || filter.active, onClick = { showFilters = !showFilters },
                        label = { Text(if (filter.active) "Filters on" else "Filters") })
                }
            }
            if (showFilters) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchPeriod.entries.forEach { p ->
                                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(minText, { v -> minText = v.filter { it.isDigit() || it == ',' } },
                                label = { Text("Min ${com.moneymap.core.Plan.CURRENCY}") }, singleLine = true, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(maxText, { v -> maxText = v.filter { it.isDigit() || it == ',' } },
                                label = { Text("Max ${com.moneymap.core.Plan.CURRENCY}") }, singleLine = true, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        if (scope == SearchScope.ALL || scope == SearchScope.EXPENSES) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExpenseCategory.entries.forEach { c ->
                                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c },
                                        label = { Text(c.label) })
                                }
                            }
                        }
                        if (filter.active) {
                            androidx.compose.material3.TextButton(onClick = {
                                period = SearchPeriod.ANY; minText = ""; maxText = ""; category = null
                            }) { Text("Clear filters") }
                            val total = hits.filterIsInstance<Hit.ExpenseHit>().sumOf { it.e.amount }
                            if (total > 0) Text("Expenses in results: ${formatInr(total)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (query.isNotBlank() || filter.active) {
                item {
                    Text(if (hits.isEmpty()) "No matches" else "${hits.size} match${if (hits.size == 1) "" else "es"}",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            items(hits) { hit ->
                when (hit) {
                    is Hit.ExpenseHit -> ResultRow(
                        title = hit.e.note.ifBlank { hit.e.category.label },
                        subtitle = "Expense · ${hit.e.category.label} · ${hit.e.date.long()}",
                        amount = "−" + formatInr(hit.e.amount),
                    )
                    is Hit.EntryHit -> {
                        val e = hit.e
                        val lent = e.entry.direction == Direction.LENT
                        ResultRow(
                            title = "${e.entry.person} · ${e.entry.reason}",
                            subtitle = (if (lent) "They owe me" else "I owe") + " · ${e.status.label} · ${e.entry.date.long()}",
                            amount = formatInr(e.outstanding) + " of " + formatInr(e.entry.amount),
                            onClick = { onOpenEntry(e.entry.id) },
                        )
                    }
                    is Hit.PodHit -> ResultRow(
                        title = hit.m.note.ifBlank { if (hit.m.amount >= 0) "Added" else "Withdrawn" },
                        subtitle = "${hit.m.pod} · ${hit.m.date.long()}",
                        amount = (if (hit.m.amount >= 0) "+" else "−") + formatInr(abs(hit.m.amount)),
                        positive = hit.m.amount >= 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    amount: String,
    positive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text(amount, style = MaterialTheme.typography.titleSmall,
            color = if (positive) MoneyColors.positive else MaterialTheme.colorScheme.onSurface)
    }
}
