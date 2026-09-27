package com.moneymap.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneymap.core.Plan
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
    expenses: List<Expense>,
    people: List<String>,
    onAdd: (Long, String) -> Unit,
    onPaidForSomeone: (String, Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cycle = Plan.cycleStartFor(today)
    val end = Plan.cycleEnd(cycle)
    val inCycle = expenses.filter { !it.date.isBefore(cycle) && !it.date.isAfter(end) }
    val spent = inCycle.sumOf { it.amount }
    val left = Plan.SPEND_BUDGET - spent
    val daysLeft = ChronoUnit.DAYS.between(today, end) + 1
    val perDay = if (left > 0 && daysLeft > 0) left / daysLeft else 0

    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
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
            Card(colors = CardDefaults.cardColors(
                containerColor = if (left < 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            )) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("LEFT TO SPEND · JUPITER MAIN", style = MaterialTheme.typography.labelMedium)
                    Text(formatInr(left), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("Spent ${formatInr(spent)} of ${formatInr(Plan.SPEND_BUDGET)} · ${cycle.short()} – ${end.short()}",
                        style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { (spent.toFloat() / Plan.SPEND_BUDGET).coerceIn(0f, 1f) },
                        color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                    Text(
                        if (left > 0) "$daysLeft days left · about ${formatInr(perDay)} a day"
                        else "Over budget by ${formatInr(-left)} · $daysLeft days left",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Log an expense", style = MaterialTheme.typography.titleMedium)
                    AmountField(amount, { amount = it }, isError = showErrors && parsed == null)
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, label = { Text("Note") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
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
                                if (forSomeone) onPaidForSomeone(person, a, note) else onAdd(a, note)
                                amount = ""; note = ""; person = ""; forSomeone = false; showErrors = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (forSomeone) "Add to People" else "Add expense") }
                }
            }
        }
        item { SectionTitle("This cycle (${inCycle.size})") }
        if (inCycle.isEmpty()) {
            item { Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(inCycle, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.note.ifBlank { "Expense" }, style = MaterialTheme.typography.bodyLarge)
                    Text(e.date.withDay(), style = MaterialTheme.typography.bodySmall)
                }
                Text(formatInr(e.amount), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onDelete(e.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
