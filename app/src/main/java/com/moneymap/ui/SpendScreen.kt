package com.moneymap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.Plan
import com.moneymap.core.PlanEngine
import com.moneymap.core.Spending
import com.moneymap.core.SuggestedExpense
import com.moneymap.core.formatInr
import com.moneymap.core.parseAmount
import com.moneymap.core.short
import com.moneymap.core.withDay
import com.moneymap.data.Expense
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SpendScreen(
    today: LocalDate,
    plan: PlanEngine,
    expenses: List<Expense>,
    people: List<String>,
    onAdd: (Long, String, ExpenseCategory) -> Unit,
    onPaidForSomeone: (String, Long, String) -> Unit,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<SuggestedExpense> = emptyList(),
    onAcceptSuggestion: (SuggestedExpense, String, ExpenseCategory) -> Unit = { _, _, _ -> },
    onDismissSuggestion: (SuggestedExpense) -> Unit = {},
) {
    var offset by rememberSaveable { mutableLongStateOf(0L) }
    val current = offset == 0L
    val cycle = Plan.cycleStartFor(today).plusMonths(offset)
    val end = Plan.cycleEnd(cycle)
    val budget = plan.spendBudget(cycle)
    val inCycle = expenses.filter { !it.date.isBefore(cycle) && !it.date.isAfter(end) }
    val spent = inCycle.sumOf { it.amount }
    val left = budget - spent
    val daysLeft = (ChronoUnit.DAYS.between(today, end) + 1).coerceAtLeast(1)
    val perDay = if (left > 0) left / daysLeft else 0
    val breakdown = Spending.breakdown(inCycle.map { it.category to it.amount })

    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ExpenseCategory.FOOD) }
    var forSomeone by rememberSaveable { mutableStateOf(false) }
    var person by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val parsed = parseAmount(amount)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous cycle")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Plan.cycleLabel(cycle), style = MaterialTheme.typography.titleMedium)
                    Text("${cycle.short()} – ${end.short()}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { offset++ }, enabled = offset < 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next cycle")
                }
            }
            if (!current) {
                TextButton(onClick = { offset = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Back to this cycle") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = if (left < 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            )) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(if (current) "LEFT TO SPEND · ${Plan.SPEND_MAIN.uppercase()}" else "LEFT AT END OF CYCLE",
                        style = MaterialTheme.typography.labelMedium)
                    Text(formatInr(left), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("Spent ${formatInr(spent)} of ${formatInr(budget)}", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { if (budget <= 0) 1f else (spent.toFloat() / budget).coerceIn(0f, 1f) },
                        color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                    if (current) {
                        Text(
                            if (left > 0) "$daysLeft days left · about ${formatInr(perDay)} a day"
                            else "Over budget by ${formatInr(-left)} · $daysLeft days left",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
        if (current && suggestions.isNotEmpty()) {
            item { SuggestionsCard(suggestions, onAcceptSuggestion, onDismissSuggestion) }
        }
        if (breakdown.isNotEmpty()) {
            item {
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Where it went", style = MaterialTheme.typography.titleMedium)
                        breakdown.forEach { c ->
                            Column {
                                Row {
                                    Text(c.category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("${formatInr(c.amount)} · ${(c.share * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                Box(
                                    Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(c.share).height(8.dp).clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (current) {
            item {
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Log an expense", style = MaterialTheme.typography.titleMedium)
                        AmountField(amount, { amount = it }, isError = showErrors && parsed == null)
                        OutlinedTextField(
                            value = note, onValueChange = { note = it }, label = { Text("Note") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        if (!forSomeone) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExpenseCategory.entries.forEach { c ->
                                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Paid for someone")
                                Text("Adds it to People as money they owe you, not as spending",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = forSomeone, onCheckedChange = { forSomeone = it })
                        }
                        if (forSomeone) {
                            PersonField(person, { person = it }, people, isError = showErrors && person.isBlank())
                        }
                        Button(
                            onClick = {
                                val a = parsed
                                if (a == null || (forSomeone && person.isBlank())) {
                                    showErrors = true
                                } else {
                                    if (forSomeone) onPaidForSomeone(person, a, note) else onAdd(a, note, category)
                                    amount = ""; note = ""; person = ""; forSomeone = false; showErrors = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (forSomeone) "Add to People" else "Add expense") }
                    }
                }
            }
        }
        item { SectionTitle("Expenses (${inCycle.size})") }
        if (inCycle.isEmpty()) {
            item { Text("Nothing logged.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(inCycle, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.note.ifBlank { e.category.label }, style = MaterialTheme.typography.bodyLarge)
                    Text("${e.date.withDay()} · ${e.category.label}", style = MaterialTheme.typography.bodySmall)
                }
                Text(formatInr(e.amount), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onDelete(e) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
