package com.moneymap.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneymap.MoneyMapApp
import com.moneymap.core.Direction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.LedgerEntry
import com.moneymap.core.ReturnPod
import com.moneymap.core.formatInr
import com.moneymap.data.Backup
import com.moneymap.data.Expense
import com.moneymap.notify.AlarmReceiver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Something the UI should open, e.g. from a notification tap. */
data class OpenRequest(val entryId: Long?, val record: Boolean, val tab: Int?)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MoneyMapApp).repository

    val entries: StateFlow<List<EntryWithTxns>> =
        repo.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val doneIds: StateFlow<Set<String>> =
        repo.doneIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val expenses: StateFlow<List<Expense>> =
        repo.expenses.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = _openRequest.asStateFlow()

    private val _exportUris = MutableSharedFlow<List<Uri>>(extraBufferCapacity = 1)
    val exportUris: SharedFlow<List<Uri>> = _exportUris

    fun refreshToday() {
        _today.value = LocalDate.now()
    }

    fun openFromIntent(entryId: Long?, record: Boolean, tab: Int?) {
        if (entryId != null || tab != null) _openRequest.value = OpenRequest(entryId, record, tab)
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    private fun say(message: String) {
        _messages.tryEmit(message)
    }

    fun setDone(itemId: String, done: Boolean) = viewModelScope.launch { repo.setDone(itemId, done) }

    fun saveEntry(
        existing: LedgerEntry?,
        person: String, direction: Direction, amount: Long, reason: String, date: LocalDate,
        dueDate: LocalDate?, pod: ReturnPod, notes: String,
    ) = viewModelScope.launch {
        if (existing == null) {
            repo.addEntry(person, direction, amount, reason, date, dueDate, pod, notes)
            say(if (direction == Direction.LENT) "$person owes you ${formatInr(amount)}" else "You owe $person ${formatInr(amount)}")
        } else {
            repo.editEntry(existing.copy(person = person, direction = direction, amount = amount, reason = reason,
                date = date, dueDate = dueDate, returnPod = pod, notes = notes))
            say("Saved")
        }
    }

    fun deleteEntry(id: Long) = viewModelScope.launch {
        repo.deleteEntry(id)
        say("Entry deleted")
    }

    fun recordSettlement(entryId: Long, amount: Long, date: LocalDate, note: String) = viewModelScope.launch {
        val s = repo.recordSettlement(entryId, amount, date, note) ?: return@launch
        val msg = s.moveHint ?: "Repaid ${formatInr(s.amount)} to ${s.person}"
        say(if (s.outstandingAfter > 0) "$msg · ${formatInr(s.outstandingAfter)} still open" else "$msg · settled")
    }

    fun undoLast(entryId: Long) = viewModelScope.launch {
        if (repo.undoLast(entryId)) say("Last transaction undone")
    }

    fun addExpense(amount: Long, note: String) = viewModelScope.launch {
        repo.addExpense(amount, note, today.value)
        say("Logged ${formatInr(amount)}")
    }

    fun paidForSomeone(person: String, amount: Long, note: String) = viewModelScope.launch {
        repo.paidForSomeone(person.trim(), amount, note, today.value)
        say("Added to People: ${person.trim()} owes you ${formatInr(amount)}")
    }

    fun deleteExpense(id: Long) = viewModelScope.launch { repo.deleteExpense(id) }

    fun export() = viewModelScope.launch {
        runCatching { Backup.export(getApplication(), repo) }
            .onSuccess { _exportUris.tryEmit(it) }
            .onFailure { say("Export failed: ${it.message}") }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching { Backup.importFrom(getApplication(), repo, uri) }
            .onSuccess { say("Imported ${it.entries.size} entries and ${it.expenses.size} expenses") }
            .onFailure { say("Import failed: ${it.message}") }
    }

    fun sendTestNotification() = viewModelScope.launch {
        AlarmReceiver.sendTest(getApplication(), repo)
    }
}
