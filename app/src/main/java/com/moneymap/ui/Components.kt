package com.moneymap.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moneymap.core.long
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Amount (₹)",
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == ',' }) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        prefix = { Text("₹") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Text field with tappable suggestions from existing names. */
@Composable
fun PersonField(
    value: String,
    onValueChange: (String) -> Unit,
    people: List<String>,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Person") },
            singleLine = true,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
        )
        val suggestions = people.filter {
            it.contains(value.trim(), ignoreCase = true) && !it.equals(value.trim(), ignoreCase = true)
        }.take(8)
        if (suggestions.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { name ->
                    SuggestionChip(onClick = { onValueChange(name) }, label = { Text(name) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
            Text("$label: ${date?.long() ?: "none"}")
        }
        if (clearable && date != null) {
            TextButton(onClick = { onDateChange(null) }) { Text("Clear") }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Asks for the password of an encrypted backup being imported. */
@Composable
fun BackupPasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (wrong) "That password didn't work. Try again." else "This backup is password-protected.")
                PasswordField(password, { password = it }, "Password", isError = wrong)
            }
        },
        confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password) }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days > 1 -> "In $days days"
        else -> "${-days} days ago"
    }
}
