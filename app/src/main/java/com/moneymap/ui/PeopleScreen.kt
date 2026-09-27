package com.moneymap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneymap.core.Direction
import com.moneymap.core.EntryStatus
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.Ledger
import com.moneymap.core.LedgerFilter
import com.moneymap.core.formatInr
import com.moneymap.core.long
import com.moneymap.core.parseAmount
import com.moneymap.ui.theme.MoneyColors
import java.time.LocalDate

@Composable
fun PeopleScreen(
    entries: List<EntryWithTxns>,
    today: LocalDate,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRecord: (Long, Long, LocalDate, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(LedgerFilter.ALL) }
    var recordFor by remember { mutableStateOf<EntryWithTxns?>(null) }
    var deleteFor by remember { mutableStateOf<EntryWithTxns?>(null) }
    val context = LocalContext.current
    val summary = Ledger.summary(entries)
    val groups = Ledger.byPerson(entries, filter)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryHeader(summary.othersOweMe, summary.iOwe, summary.net) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (groups.isEmpty()) {
                item { Text("Nothing here.", style = MaterialTheme.typography.bodyMedium) }
            }
            groups.forEach { group ->
                item(key = "person-${Ledger.personKey(group.person)}") {
                    PersonHeader(group.person, group.net, onShare = Ledger.reminderMessage(entries, group.person)?.let { msg ->
                        { shareText(context, msg) }
                    })
                }
                items(group.entries, key = { it.entry.id }) { e ->
                    EntryCard(
                        item = e,
                        today = today,
                        onClick = { onOpen(e.entry.id) },
                        onRecord = { recordFor = e },
                        onEdit = { onEdit(e.entry.id) },
                        onDelete = { deleteFor = e },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    recordFor?.let { e ->
        RecordDialog(e, today, onDismiss = { recordFor = null }) { amount, date, note ->
            onRecord(e.entry.id, amount, date, note)
            recordFor = null
        }
    }
    deleteFor?.let { e ->
        ConfirmDialog(
            title = "Delete entry?",
            text = "${e.entry.person} · ${e.entry.reason} · ${formatInr(e.entry.amount)} and all its transactions will be removed.",
            confirmLabel = "Delete",
            onConfirm = { onDelete(e.entry.id) },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun SummaryHeader(othersOweMe: Long, iOwe: Long, net: Long) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            SummaryCell("Others owe me", othersOweMe, MoneyColors.positive, Modifier.weight(1f))
            SummaryCell("I owe others", iOwe, MoneyColors.negative, Modifier.weight(1f))
            SummaryCell("Net", net, if (net >= 0) MoneyColors.positive else MoneyColors.negative, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(formatInr(amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun PersonHeader(person: String, net: Long, onShare: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(person, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(
            when {
                net > 0 -> "owes you ${formatInr(net)}"
                net < 0 -> "you owe ${formatInr(-net)}"
                else -> "all settled"
            },
            color = when {
                net > 0 -> MoneyColors.positive
                net < 0 -> MoneyColors.negative
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.titleSmall,
        )
        if (onShare != null) {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Send $person a reminder") }
        }
    }
}

/** "Move ₹X to <pod> today" when money came back today. */
fun moveNote(e: EntryWithTxns, today: LocalDate): String? {
    if (e.entry.direction != Direction.LENT) return null
    val todays = e.settlements.filter { it.date == today }.sumOf { it.amount }
    return if (todays > 0) "Move ${formatInr(todays)} to ${e.entry.returnPod.label} today" else null
}

@Composable
fun StatusBadge(status: EntryStatus) {
    val (bg, fg) = when (status) {
        EntryStatus.OPEN -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        EntryStatus.PARTLY_SETTLED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        EntryStatus.SETTLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(status.label, color = fg, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun EntryCard(
    item: EntryWithTxns,
    today: LocalDate,
    onClick: () -> Unit,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val e = item.entry
    val settled = item.status == EntryStatus.SETTLED
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (settled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.reason.ifBlank { "No reason" }, style = MaterialTheme.typography.titleSmall)
                    Text(
                        (if (e.direction == Direction.LENT) "They owe me" else "I owe") + " · ${e.date.long()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusBadge(item.status)
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Outstanding", style = MaterialTheme.typography.labelSmall)
                    Text(formatInr(item.outstanding), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (settled) MaterialTheme.colorScheme.onSurfaceVariant
                        else if (e.direction == Direction.LENT) MoneyColors.positive else MoneyColors.negative)
                }
                Text("of ${formatInr(e.amount)}", style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            e.dueDate?.let { due ->
                val overdue = item.isOverdue(today)
                Text(
                    "Due ${due.long()}" + if (overdue) " · overdue" else "",
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (e.direction == Direction.LENT && !settled) {
                Text("Returns go to ${e.returnPod.label}", style = MaterialTheme.typography.bodySmall)
            }
            moveNote(item, today)?.let {
                Text(it, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!settled) {
                    FilledTonalButton(onClick = onRecord) {
                        Text(if (e.direction == Direction.LENT) "Record return" else "Record repayment")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    }
}

/** Amount defaults to the full outstanding; edit it for a partial return/repayment. */
@Composable
fun RecordDialog(
    item: EntryWithTxns,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (Long, LocalDate, String) -> Unit,
) {
    val lent = item.entry.direction == Direction.LENT
    var amount by rememberSaveable { mutableStateOf(formatInr(item.outstanding, withSymbol = false)) }
    var date by remember { mutableStateOf(today) }
    var note by rememberSaveable { mutableStateOf("") }
    val parsed = parseAmount(amount)
    val tooMuch = parsed != null && parsed > item.outstanding
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lent) "Record return from ${item.entry.person}" else "Record repayment to ${item.entry.person}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding: ${formatInr(item.outstanding)}")
                AmountField(amount, { amount = it }, isError = parsed == null || tooMuch)
                if (tooMuch) {
                    Text("More than outstanding — only ${formatInr(item.outstanding)} will be recorded",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                DateField("Date", date, { if (it != null) date = it })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (lent) {
                    Text("Then move it to ${item.entry.returnPod.label}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let { onConfirm(it, date, note) } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
