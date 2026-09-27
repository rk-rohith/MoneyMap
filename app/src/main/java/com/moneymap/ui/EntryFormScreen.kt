package com.moneymap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymap.core.Direction
import com.moneymap.core.LedgerEntry
import com.moneymap.core.ReturnPod
import com.moneymap.core.formatInr
import com.moneymap.core.parseAmount
import java.time.LocalDate

typealias SaveEntry = (
    existing: LedgerEntry?, person: String, direction: Direction, amount: Long, reason: String,
    date: LocalDate, dueDate: LocalDate?, pod: ReturnPod, notes: String,
) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(
    existing: LedgerEntry?,
    people: List<String>,
    onSave: SaveEntry,
    onClose: () -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf(existing?.direction ?: Direction.LENT) }
    var person by rememberSaveable { mutableStateOf(existing?.person ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.let { formatInr(it, withSymbol = false) } ?: "") }
    var reason by rememberSaveable { mutableStateOf(existing?.reason ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var pod by rememberSaveable { mutableStateOf(existing?.returnPod ?: ReturnPod.JUPITER_MAIN) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val parsed = parseAmount(amount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add" else "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                actions = {
                    TextButton(onClick = {
                        val a = parsed
                        if (a == null || person.isBlank()) showErrors = true
                        else onSave(existing, person.trim(), direction, a, reason.trim(), date, due, pod, notes.trim())
                    }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("What happened?", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = direction == Direction.LENT, onClick = { direction = Direction.LENT },
                    label = { Text("I lent (they owe me)") })
                FilterChip(selected = direction == Direction.BORROWED, onClick = { direction = Direction.BORROWED },
                    label = { Text("I borrowed (I owe)") })
            }
            PersonField(person, { person = it }, people, isError = showErrors && person.isBlank())
            AmountField(amount, { amount = it }, isError = showErrors && parsed == null)
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            DateField("Date", date, { if (it != null) date = it })
            DateField("Due", due, { due = it }, clearable = true)
            if (direction == Direction.LENT) {
                Text("When it comes back, move it to", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReturnPod.entries.forEach { p ->
                        FilterChip(selected = pod == p, onClick = { pod = p }, label = { Text(p.label) })
                    }
                }
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2,
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val a = parsed
                    if (a == null || person.isBlank()) showErrors = true
                    else onSave(existing, person.trim(), direction, a, reason.trim(), date, due, pod, notes.trim())
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
