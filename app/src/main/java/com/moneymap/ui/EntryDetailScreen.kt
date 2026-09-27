package com.moneymap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneymap.core.Direction
import com.moneymap.core.EntryStatus
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.Ledger
import com.moneymap.core.LedgerTxn
import com.moneymap.core.TxnType
import com.moneymap.core.formatInr
import com.moneymap.core.long
import com.moneymap.ui.theme.MoneyColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    data: EntryWithTxns,
    today: LocalDate,
    snackbar: SnackbarHostState,
    startRecording: Boolean,
    onRecordingShown: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRecord: (Long, LocalDate, String) -> Unit,
    onUndo: () -> Unit,
) {
    val e = data.entry
    val context = LocalContext.current
    val lent = e.direction == Direction.LENT
    var recording by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    LaunchedEffect(startRecording) {
        if (startRecording) {
            if (data.status != EntryStatus.SETTLED) recording = true
            onRecordingShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e.person) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Ledger.reminderMessage(listOf(data), e.person)?.let { msg ->
                        IconButton(onClick = { shareText(context, msg) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Send a reminder")
                        }
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.reason.ifBlank { "No reason" }, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            StatusBadge(data.status)
                        }
                        Text(if (lent) "${e.person} owes me" else "I owe ${e.person}", style = MaterialTheme.typography.bodyMedium)
                        Text(formatInr(data.outstanding), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                            color = if (lent) MoneyColors.positive else MoneyColors.negative)
                        Text("${formatInr(data.settled)} ${if (lent) "returned" else "repaid"} of ${formatInr(e.amount)}")
                        LinearProgressIndicator(progress = { data.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        Text("Date: ${e.date.long()}", style = MaterialTheme.typography.bodySmall)
                        e.dueDate?.let {
                            val overdue = data.isOverdue(today)
                            Text("Due: ${it.long()}" + if (overdue) " · overdue" else "", style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                        if (lent) Text("Returns go to: ${e.returnPod.label}", style = MaterialTheme.typography.bodySmall)
                        if (e.notes.isNotBlank()) Text(e.notes, style = MaterialTheme.typography.bodySmall)
                        moveNote(data, today)?.let {
                            Text(it, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (data.status != EntryStatus.SETTLED) {
                        Button(onClick = { recording = true }) { Text(if (lent) "Record return" else "Record repayment") }
                    }
                    if (data.settlements.isNotEmpty()) {
                        OutlinedButton(onClick = { confirmUndo = true }) { Text("Undo last") }
                    }
                }
            }
            item { SectionTitle("History") }
            val timeline = data.timeline
            itemsIndexed(timeline, key = { _, t -> t.id }) { i, t ->
                TimelineRow(t, first = i == 0, last = i == timeline.lastIndex)
            }
        }
    }

    if (recording) {
        RecordDialog(data, today, onDismiss = { recording = false }) { amount, date, note ->
            onRecord(amount, date, note)
            recording = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete entry?", "This removes ${e.person} · ${e.reason} and its whole history.", "Delete",
            onConfirm = onDelete, onDismiss = { confirmDelete = false })
    }
    if (confirmUndo) {
        val last = data.settlements.lastOrNull()
        ConfirmDialog("Undo last transaction?",
            last?.let { "Remove ${txnLabel(it.type)} of ${formatInr(it.amount)} on ${it.date.long()}." } ?: "",
            "Undo", onConfirm = onUndo, onDismiss = { confirmUndo = false })
    }
}

private fun txnLabel(type: TxnType) = when (type) {
    TxnType.GIVEN -> "Given"
    TxnType.RECEIVED -> "Received"
    TxnType.BORROWED -> "Borrowed"
    TxnType.REPAID -> "Repaid"
}

@Composable
private fun TimelineRow(t: LedgerTxn, first: Boolean, last: Boolean) {
    val opening = t.type == TxnType.GIVEN || t.type == TxnType.BORROWED
    val dot = if (opening) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Box(Modifier.width(24.dp).fillMaxHeight().heightIn(min = 56.dp), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(2.dp).height(8.dp).background(if (first) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.outlineVariant))
                Box(Modifier.size(12.dp).background(dot, CircleShape))
                Box(Modifier.width(2.dp).height(40.dp).background(if (last) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Row {
                Text(txnLabel(t.type), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text((if (opening) "" else "−") + formatInr(t.amount), style = MaterialTheme.typography.titleSmall,
                    color = if (opening) MaterialTheme.colorScheme.onSurface else MoneyColors.positive)
            }
            Text(t.date.long(), style = MaterialTheme.typography.bodySmall)
            if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}
