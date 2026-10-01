package com.moneymap.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.formatInr
import com.moneymap.core.parseAmount
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import com.moneymap.core.long
import com.moneymap.data.Expense

/** An expense's details with its receipt photo: attach from the gallery or camera, view, remove. */
@Composable
fun ExpenseDialog(
    expense: Expense,
    hasReceipt: Boolean,
    loadReceipt: suspend (Long) -> Bitmap?,
    cameraUri: () -> Uri,
    onAttach: (Long, Uri) -> Unit,
    onRemoveReceipt: (Long) -> Unit,
    onDelete: (Expense) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (Expense) -> Unit = {},
) {
    var editing by remember(expense.id) { mutableStateOf(false) }
    if (editing) {
        EditExpenseDialog(expense, onSave = { onEdit(it); editing = false }, onCancel = { editing = false })
        return
    }
    var bitmap by remember(expense.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(expense.id, hasReceipt) { bitmap = if (hasReceipt) loadReceipt(expense.id) else null }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAttach(expense.id, uri)
    }
    var pendingCamera by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCamera
        if (ok && uri != null) onAttach(expense.id, uri)
        pendingCamera = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${formatInr(expense.amount)} · ${expense.category.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (expense.note.isNotBlank()) Text(expense.note, style = MaterialTheme.typography.bodyLarge)
                Text(expense.date.long(), style = MaterialTheme.typography.bodySmall)
                bitmap?.let {
                    Image(it.asImageBitmap(), contentDescription = "Receipt", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (hasReceipt) "Replace" else "Add photo") }
                    OutlinedButton(onClick = {
                        val uri = cameraUri()
                        pendingCamera = uri
                        // No camera app (some work profiles and emulators): just do nothing.
                        runCatching { camera.launch(uri) }.onFailure { pendingCamera = null }
                    }) { Text("Camera") }
                }
                if (hasReceipt) {
                    TextButton(onClick = { onRemoveReceipt(expense.id) }) { Text("Remove photo") }
                }
                OutlinedButton(onClick = { editing = true }) { Text("Edit amount, note, category or date") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { onDelete(expense); onDismiss() }) { Text("Delete expense") } },
    )
}

@Composable
private fun EditExpenseDialog(expense: Expense, onSave: (Expense) -> Unit, onCancel: () -> Unit) {
    var amount by remember { mutableStateOf(formatInr(expense.amount, withSymbol = false)) }
    var note by remember { mutableStateOf(expense.note) }
    var category by remember { mutableStateOf(expense.category) }
    var date by remember { mutableStateOf(expense.date) }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(amount, { amount = it }, isError = parsed == null)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                DateField("Date", date, { if (it != null) date = it })
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = {
                onSave(expense.copy(amount = parsed!!, note = note.trim(), category = category, date = date))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
