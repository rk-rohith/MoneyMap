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
import com.moneymap.core.Profile
import com.moneymap.core.ReminderPrefs
import com.moneymap.core.ReturnPod
import com.moneymap.core.SuggestedExpense
import com.moneymap.core.formatInr
import com.moneymap.data.AppLock
import com.moneymap.data.AutoBackup
import com.moneymap.data.Backup
import com.moneymap.data.BackupPassword
import com.moneymap.data.Expense
import com.moneymap.data.Receipts
import com.moneymap.data.UiPrefs
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

/** Files to share; [targetPackage] picks one app (e.g. Google Drive) instead of the share sheet. */
data class ExportRequest(val uris: List<Uri>, val targetPackage: String?)

const val DRIVE_PACKAGE = "com.google.android.apps.docs"

/** Import of [uri] needs a password; [wrong] after a failed attempt. */
data class PasswordPrompt(val uri: Uri, val wrong: Boolean)

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
    /** Null while loading; [Profile.setupDone] false shows the first-run setup. */
    val profile: StateFlow<Profile?> =
        repo.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val categoryBudgets: StateFlow<Map<ExpenseCategory, Long>> =
        repo.categoryBudgets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun saveCategoryBudgets(budgets: Map<ExpenseCategory, Long>) = viewModelScope.launch {
        repo.saveCategoryBudgets(budgets)
        say("Category budgets saved")
    }

    /** Snackbar text when this expense pushes its category past 80% or 100% of its budget. */
    private suspend fun budgetWarning(amount: Long, category: ExpenseCategory, date: LocalDate): String? {
        val budget = repo.categoryBudgetsNow()[category] ?: return null
        val before = repo.spentInCategory(category, date)
        return when (com.moneymap.core.Reports.budgetAlert(before, amount, budget)) {
            100 -> " · ${category.label} is over its ${formatInr(budget)} budget"
            80 -> " · 80% of the ${category.label} budget used"
            else -> null
        }
    }

    /** Adds a repeating payment spotted in Reports to the plan from the current cycle onward. */
    fun addRegularItem(guess: com.moneymap.core.RecurringGuess) = viewModelScope.launch {
        val engine = repo.planNow()
        val cycle = com.moneymap.core.Plan.cycleStartFor(today.value)
        val cfg = engine.settings.baseConfigFor(cycle)
        val item = com.moneymap.core.RecurringItem(
            id = "r${System.currentTimeMillis()}", title = guess.title, amount = guess.typicalAmount, day = guess.day,
            flow = com.moneymap.core.Flow.HDFC, account = com.moneymap.core.Plan.HDFC,
        )
        repo.savePlan(engine.settings.withVersion(cycle, cfg.copy(items = cfg.items + item)))
        say("Added ${guess.title} to your plan from ${com.moneymap.core.Plan.cycleLabel(cycle)}")
    }

    private val _receiptIds = MutableStateFlow(Receipts.ids(app))
    /** Expenses that have a receipt photo. */
    val receiptIds: StateFlow<Set<Long>> = _receiptIds.asStateFlow()

    fun attachReceipt(expenseId: Long, uri: Uri) = viewModelScope.launch {
        runCatching { Receipts.save(getApplication(), expenseId, uri) }
            .onSuccess { say("Receipt saved") }
            .onFailure { say("Could not save the photo: ${it.message}") }
        _receiptIds.value = Receipts.ids(getApplication())
    }

    fun removeReceipt(expenseId: Long) {
        Receipts.remove(getApplication(), expenseId)
        _receiptIds.value = Receipts.ids(getApplication())
    }

    suspend fun loadReceipt(expenseId: Long) = Receipts.load(getApplication(), expenseId)

    fun receiptCameraUri(): Uri = Receipts.cameraUri(getApplication())

    val suggestions: StateFlow<List<SuggestedExpense>> =
        repo.suggestions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reminderPrefs: StateFlow<ReminderPrefs> =
        repo.reminderPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderPrefs())

    private val _quickAdd = MutableStateFlow(false)
    /** Quick "log expense" dialog, opened from the launcher shortcut, widget or weekly notification. */
    val quickAdd: StateFlow<Boolean> = _quickAdd.asStateFlow()

    private val _addEntry = MutableStateFlow(false)
    /** Open the add lent/borrowed form (launcher shortcut). */
    val addEntry: StateFlow<Boolean> = _addEntry.asStateFlow()

    private val _dynamicColor = MutableStateFlow(UiPrefs.dynamicColor(app))
    /** Material You colours from the wallpaper (Android 12+). */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        UiPrefs.setDynamicColor(getApplication(), enabled)
        _dynamicColor.value = enabled
    }

    private val _lockEnabled = MutableStateFlow(AppLock.isEnabled(app))
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    fun showQuickAdd() { _quickAdd.value = true }
    fun dismissQuickAdd() { _quickAdd.value = false }
    fun showAddEntry() { _addEntry.value = true }
    fun consumeAddEntry() { _addEntry.value = false }

    fun lockChanged(enabled: Boolean, applied: Boolean) {
        _lockEnabled.value = AppLock.isEnabled(getApplication())
        when {
            !applied -> say("Set a screen lock (PIN, pattern or fingerprint) on your phone first")
            enabled -> say("App lock on")
            else -> say("App lock off")
        }
    }

    fun saveReminderPrefs(prefs: ReminderPrefs) = viewModelScope.launch {
        repo.saveReminderPrefs(prefs)
        say("Reminders updated")
    }

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    private val _openRequest = MutableStateFlow<OpenRequest?>(null)
    val openRequest: StateFlow<OpenRequest?> = _openRequest.asStateFlow()

    private val _exportUris = MutableSharedFlow<ExportRequest>(extraBufferCapacity = 1)
    val exportUris: SharedFlow<ExportRequest> = _exportUris

    private val _backup = MutableStateFlow(readBackupState())
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    private fun readBackupState(): BackupState {
        val ctx = getApplication<Application>()
        return BackupState(AutoBackup.folder(ctx)?.let(AutoBackup::folderLabel), AutoBackup.lastBackupAt(ctx),
            passwordSet = BackupPassword.isSet(ctx))
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

    fun addExpense(amount: Long, note: String, category: ExpenseCategory, date: LocalDate? = null) = viewModelScope.launch {
        val day = date ?: today.value
        val warning = budgetWarning(amount, category, day)
        repo.addExpense(amount, note, day, category)
        say("Logged ${formatInr(amount)} · ${category.label}" + (warning ?: ""))
    }

    fun acceptSuggestion(s: SuggestedExpense, note: String, category: ExpenseCategory) = viewModelScope.launch {
        val warning = budgetWarning(s.amount, category, s.at.toLocalDate())
        repo.acceptSuggestion(s, note, category)
        say("Logged ${formatInr(s.amount)} · ${category.label}" + (warning ?: ""))
    }

    fun dismissSuggestion(s: SuggestedExpense) = viewModelScope.launch {
        repo.dismissSuggestion(s.id)
        say("Dismissed ${formatInr(s.amount)}", "Undo") { viewModelScope.launch { repo.addSuggestion(s) } }
    }

    fun clearSuggestions() = viewModelScope.launch { repo.clearSuggestions() }

    /** Pays a shared bill: my share is spending, everyone else's share is money they owe me. */
    fun splitBill(total: Long, note: String, category: ExpenseCategory, others: List<String>) = viewModelScope.launch {
        val (mine, shares) = com.moneymap.core.Split.equal(total, others)
        val warning = budgetWarning(mine, category, today.value)
        repo.addExpense(mine, note, today.value, category)
        val label = note.trim().ifBlank { category.label }
        shares.forEach { repo.paidForSomeone(it.person, it.amount, "Split: $label", today.value) }
        say("Your share ${formatInr(mine)} logged · ${shares.size} ${if (shares.size == 1) "person owes" else "people owe"} " +
            "${formatInr(shares.firstOrNull()?.amount ?: 0)} each" + (warning ?: ""))
    }

    fun paidForSomeone(person: String, amount: Long, note: String) = viewModelScope.launch {
        repo.paidForSomeone(person.trim(), amount, note, today.value)
        say("Added to People: ${person.trim()} owes you ${formatInr(amount)}")
    }

    fun editExpense(updated: Expense, original: Expense) = viewModelScope.launch {
        repo.editExpense(updated)
        say("Expense updated", "Undo") { viewModelScope.launch { repo.editExpense(original) } }
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

    /** Shares the export; [toDrive] sends it straight to the Google Drive app's upload screen. */
    fun export(toDrive: Boolean = false) = viewModelScope.launch {
        runCatching { Backup.export(getApplication(), repo) }
            .onSuccess { uris ->
                // Drive gets only the backup file (the first one), not the readable CSVs.
                _exportUris.tryEmit(if (toDrive) ExportRequest(uris.take(1), DRIVE_PACKAGE) else ExportRequest(uris, null))
            }
            .onFailure { say("Export failed: ${it.message}") }
    }

    /** A password-protected backup waiting for its password, or null. */
    private val _passwordPrompt = MutableStateFlow<PasswordPrompt?>(null)
    val passwordPrompt: StateFlow<PasswordPrompt?> = _passwordPrompt.asStateFlow()

    fun importBackup(uri: Uri, password: String? = null) = viewModelScope.launch {
        _passwordPrompt.value = null
        runCatching { Backup.importFrom(getApplication(), repo, uri, password) }
            .onSuccess { say("Imported ${it.entries.size} entries and ${it.expenses.size} expenses") }
            .onFailure {
                if (it is Backup.PasswordNeededException) _passwordPrompt.value = PasswordPrompt(uri, it.wrongPassword)
                else say("Import failed: ${it.message}")
            }
    }

    fun dismissPasswordPrompt() { _passwordPrompt.value = null }

    fun completeSetup(profile: Profile, salary: Long, spendBudget: Long) = viewModelScope.launch {
        repo.completeSetup(profile, salary, spendBudget)
        say("You're set. Add bills and savings in Settings & plan.")
    }

    fun saveProfile(profile: Profile) = viewModelScope.launch {
        repo.saveProfile(profile)
        say("Profile saved")
    }

    fun setBackupPassword(password: String?) {
        runCatching { BackupPassword.set(getApplication(), password) }
            .onSuccess { say(if (password.isNullOrBlank()) "Backup password removed" else "Backups will be password-protected") }
            .onFailure { say("Could not save the password: ${it.message}") }
        _backup.value = readBackupState()
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
