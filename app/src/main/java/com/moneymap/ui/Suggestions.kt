package com.moneymap.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.SuggestedExpense
import com.moneymap.core.formatInr
import com.moneymap.core.withDay
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Expenses read from bank / UPI notifications, waiting to be added or dismissed. */
@Composable
fun SuggestionsCard(
    suggestions: List<SuggestedExpense>,
    onAccept: (SuggestedExpense, String, ExpenseCategory) -> Unit,
    onDismiss: (SuggestedExpense) -> Unit,
) {
    var confirm by remember { mutableStateOf<SuggestedExpense?>(null) }
    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("From your notifications (${suggestions.size})", style = MaterialTheme.typography.titleMedium)
            Text("Payments your bank or UPI app told you about. Add the ones that are spending.",
                style = MaterialTheme.typography.bodySmall)
            suggestions.take(10).forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${formatInr(s.amount)} · ${s.merchant.ifBlank { "Unknown payee" }}",
                            style = MaterialTheme.typography.bodyLarge)
                        Text("${s.at.toLocalDate().withDay()}, ${s.at.format(timeFormat)} · ${s.category.label}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onDismiss(s) }) { Text("Ignore") }
                    TextButton(onClick = { confirm = s }) { Text("Add") }
                }
            }
            if (suggestions.size > 10) {
                Text("+${suggestions.size - 10} more", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    confirm?.let { s ->
        var note by remember(s) { mutableStateOf(s.merchant) }
        var category by remember(s) { mutableStateOf(s.category) }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Add ${formatInr(s.amount)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpenseCategory.entries.forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onAccept(s, note, category); confirm = null }) { Text("Add expense") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
