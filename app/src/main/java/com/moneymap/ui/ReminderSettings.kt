package com.moneymap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymap.core.ReminderPrefs
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Reminder times and on/off switches. Edits are kept locally until Save. */
@Composable
fun ReminderSettings(saved: ReminderPrefs, onSave: (ReminderPrefs) -> Unit) {
    var p by remember(saved) { mutableStateOf(saved) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Reminders", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Payment reminders", "Bills, autopays, salary day and review", p.paymentsEnabled) {
                p = p.copy(paymentsEnabled = it)
            }
            if (p.paymentsEnabled) {
                TimeRow("Evening before", p.eveningBeforeEnabled, p.eveningBefore,
                    { p = p.copy(eveningBeforeEnabled = it) }, { p = p.copy(eveningBefore = it) })
                TimeRow("On the day", p.morningEnabled, p.morning,
                    { p = p.copy(morningEnabled = it) }, { p = p.copy(morning = it) })
                TimeRow("If not done yet", p.eveningEnabled, p.evening,
                    { p = p.copy(eveningEnabled = it) }, { p = p.copy(evening = it) })
            }
            TimeRow("Money owed (due dates)", p.ledgerEnabled, p.ledger,
                { p = p.copy(ledgerEnabled = it) }, { p = p.copy(ledger = it) })
            TimeRow("Sunday spending summary", p.weeklyEnabled, p.weekly,
                { p = p.copy(weeklyEnabled = it) }, { p = p.copy(weekly = it) })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = p != saved, onClick = { onSave(p) }) { Text("Save reminders") }
                if (p != ReminderPrefs()) {
                    TextButton(onClick = { p = ReminderPrefs() }) { Text("Defaults") }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeRow(
    title: String,
    enabled: Boolean,
    time: LocalTime,
    onEnabled: (Boolean) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { picking = true }, enabled = enabled, modifier = Modifier.padding(end = 8.dp)) {
            Text(time.format(timeFormat))
        }
        Switch(checked = enabled, onCheckedChange = onEnabled)
    }
    if (picking) {
        TimeDialog(time, onDismiss = { picking = false }) { onTime(it); picking = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
