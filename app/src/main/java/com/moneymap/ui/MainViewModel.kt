package com.moneymap.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneymap.MoneyMapApp
import com.moneymap.core.DefaultPlan
import com.moneymap.core.Direction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.Goal
import com.moneymap.core.LedgerEntry
import com.moneymap.core.PlanEngine
import com.moneymap.core.PlanSettings
import com.moneymap.core.PodMove
import com.moneymap.core.ReturnPod
import com.moneymap.core.formatInr
import com.moneymap.data.AutoBackup
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

/** A snackbar message, optionally with an action such as Undo. */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MoneyMapApp).repository

    val entries: StateFlow<List<EntryWithTxns>> =
        repo.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val doneIds: StateFlow<Set<String>> =
        repo.doneIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val expenses: StateFlow<List<Expense>> =
        repo.expenses.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val podMoves: StateFlow<List<PodMove>> =
        repo.podMoves.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goals: StateFlow<List<Goal>> =
        repo.goals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val plan: StateFlow<PlanEngine> =
        repo.plan.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultPlan.engine)

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = _openRequest.asStateFlow()

    private val _exportUris = MutableSharedFlow<List<Uri>>(extraBufferCapacity = 1)
    val exportUris: SharedFlow<List<Uri>> = _exportUris

    private val _backup = MutableStateFlow(readBackupState())
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    private fun readBackupState(): BackupState {
        val ctx = getApplication<Application>()
        return BackupState(AutoBackup.folder(ctx)?.let(AutoBackup::folderLabel), AutoBackup.lastBackupAt(ctx))
    }

    fun refreshToday() {
        _today.value = LocalDate.now()
        _backup.value = readBackupState()
    }

    fun openFromIntent(entryId: Long?, record: Boolean, tab: Int?) {
        if (entryId != null || tab != null) _openRequest.value = OpenRequest(entryId, record, tab)
    }

    fun consumeOpenRequest() {
        _openRequest.value = null
    }

    private fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, actionLabel, action))
    }

    /** Ticks/unticks a Month item and offers Undo. */
    fun setDone(itemId: String, done: Boolean) = viewModelScope.launch {
        repo.setDone(itemId, done)
        val title = plan.value.itemById(itemId)?.title ?: "Item"
        say(if (done) "$title marked done" else "$title unticked", "Undo") { applyDone(itemId, !done) }
    }

    private fun applyDone(itemId: String, done: Boolean) = viewModelScope.launch { repo.setDone(itemId, done) }

    fun savePlan(settings: PlanSettings) = viewModelScope.launch {
        repo.savePlan(settings)
        say("Plan saved · reminders updated")
    }

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

    fun addExpense(amount: Long, note: String, category: ExpenseCategory) = viewModelScope.launch {
        repo.addExpense(amount, note, today.value, category)
        say("Logged ${formatInr(amount)} · ${category.label}")
    }

    fun paidForSomeone(person: String, amount: Long, note: String) = viewModelScope.launch {
        repo.paidForSomeone(person.trim(), amount, note, today.value)
        say("Added to People: ${person.trim()} owes you ${formatInr(amount)}")
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        repo.deleteExpense(expense.id)
        say("Deleted ${formatInr(expense.amount)}", "Undo") {
            viewModelScope.launch { repo.restoreExpense(expense) }
        }
    }

    fun addPodMove(pod: String, amount: Long, note: String) = viewModelScope.launch {
        repo.addPodMove(pod, amount, note, today.value)
        say(if (amount >= 0) "Added ${formatInr(amount)} to $pod" else "Took ${formatInr(-amount)} from $pod")
    }

    fun deletePodMove(id: Long) = viewModelScope.launch { repo.deletePodMove(id) }

    fun saveGoal(goal: Goal) = viewModelScope.launch {
        repo.saveGoal(goal)
        say("Goal saved")
    }

    fun deleteGoal(id: Long) = viewModelScope.launch { repo.deleteGoal(id) }

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

    fun setBackupFolder(uri: Uri) = viewModelScope.launch {
        runCatching {
            AutoBackup.setFolder(getApplication(), uri)
            AutoBackup.runNow(getApplication(), repo)
        }.onSuccess { say("Backed up: $it") }
            .onFailure { say("Backup failed: ${it.message}") }
        _backup.value = readBackupState()
    }

    fun backupNow() = viewModelScope.launch {
        runCatching { AutoBackup.runNow(getApplication(), repo) }
            .onSuccess { say("Backed up: $it") }
            .onFailure { say("Backup failed: ${it.message}") }
        _backup.value = readBackupState()
    }

    fun turnOffBackup() {
        AutoBackup.turnOff(getApplication())
        _backup.value = readBackupState()
        say("Automatic backup turned off")
    }

    fun sendTestNotification() = viewModelScope.launch {
        AlarmReceiver.sendTest(getApplication(), repo)
    }
}
