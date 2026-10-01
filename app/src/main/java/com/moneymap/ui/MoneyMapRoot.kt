package com.moneymap.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Month", Icons.Filled.DateRange),
    Tab("Spend", Icons.Filled.ShoppingCart),
    Tab("People", Icons.Filled.Person),
    Tab("Save", Icons.Filled.Star),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyMapRoot(vm: MainViewModel, onLockChanged: (Boolean) -> Boolean = { true }) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val done by vm.doneIds.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val podMoves by vm.podMoves.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var reportsOpen by rememberSaveable { mutableStateOf(false) }
    var wealthOpen by rememberSaveable { mutableStateOf(false) }
    val loans by vm.loans.collectAsStateWithLifecycle()
    val investments by vm.investments.collectAsStateWithLifecycle()
    val netWorthHistory by vm.netWorthHistory.collectAsStateWithLifecycle()
    val categoryBudgets by vm.categoryBudgets.collectAsStateWithLifecycle()
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val quickAdd by vm.quickAdd.collectAsStateWithLifecycle()
    val addEntryRequest by vm.addEntry.collectAsStateWithLifecycle()
    val reminderPrefs by vm.reminderPrefs.collectAsStateWithLifecycle()
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val passwordPrompt by vm.passwordPrompt.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val receiptIds by vm.receiptIds.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordOnOpen by rememberSaveable { mutableStateOf(false) }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var formEditId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }
    LaunchedEffect(Unit) {
        vm.exportUris.collect { request ->
            val uris = request.uris
            val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = ClipData.newRawUri("Money map export", uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                putExtra(Intent.EXTRA_SUBJECT, "Money map backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val direct = request.targetPackage?.let { pkg ->
                runCatching { context.startActivity(Intent(send).setPackage(pkg)) }.isSuccess
            } ?: false
            if (!direct) {
                val title = if (request.targetPackage != null) "Drive isn't installed: save the backup to…" else "Share backup"
                context.startActivity(Intent.createChooser(send, title))
            }
        }
    }
    LaunchedEffect(openRequest) {
        val req = openRequest ?: return@LaunchedEffect
        req.tab?.let { tab = it }
        if (req.entryId != null) {
            tab = 2
            formOpen = false
            settingsOpen = false
            searchOpen = false
            detailId = req.entryId
            recordOnOpen = req.record
        }
        vm.consumeOpenRequest()
    }

    val openForm: (Long?) -> Unit = { id -> formEditId = id; formOpen = true }

    passwordPrompt?.let { prompt ->
        BackupPasswordDialog(
            wrong = prompt.wrong,
            onSubmit = { vm.importBackup(prompt.uri, it) },
            onDismiss = { vm.dismissPasswordPrompt() },
        )
    }

    // Wait for the profile, then run first-time setup before anything else.
    val currentProfile = profile ?: return
    if (!currentProfile.setupDone) {
        SetupScreen(
            start = currentProfile,
            snackbar = snackbar,
            onDone = { p, salary, budget -> vm.completeSetup(p, salary, budget) },
            onRestore = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
        return
    }

    LaunchedEffect(addEntryRequest) {
        if (addEntryRequest) {
            settingsOpen = false
            detailId = null
            tab = 2
            openForm(null)
            vm.consumeAddEntry()
        }
    }
    if (quickAdd) {
        val cycle = com.moneymap.core.Plan.cycleStartFor(today)
        val end = com.moneymap.core.Plan.cycleEnd(cycle)
        val spent = expenses.filter { !it.date.isBefore(cycle) && !it.date.isAfter(end) }.sumOf { it.amount }
        QuickAddDialog(
            leftToSpend = plan.spendBudget(cycle) - spent,
            onDismiss = { vm.dismissQuickAdd() },
            onAdd = { amount, note, category ->
                vm.addExpense(amount, note, category)
                vm.dismissQuickAdd()
            },
            onAddDated = { amount, note, category, date ->
                vm.addExpense(amount, note, category, date)
                vm.dismissQuickAdd()
            },
        )
    }

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(
            today = today,
            plan = plan,
            backup = backup,
            snackbar = snackbar,
            onBack = { settingsOpen = false },
            onSavePlan = { vm.savePlan(it) },
            onChooseFolder = { folderLauncher.launch(null) },
            onBackupNow = { vm.backupNow() },
            onBackupOff = { vm.turnOffBackup() },
            onExport = { vm.export() },
            onExportToDrive = { vm.export(toDrive = true) },
            onImport = { confirmImport = true },
            onTestNotification = { vm.sendTestNotification() },
            reminderPrefs = reminderPrefs,
            onSaveReminders = { vm.saveReminderPrefs(it) },
            lockEnabled = lockEnabled,
            onLockChange = { enabled -> vm.lockChanged(enabled, onLockChanged(enabled)) },
            dynamicColor = dynamicColor,
            onDynamicColorChange = { vm.setDynamicColor(it) },
            onBackupPassword = { vm.setBackupPassword(it) },
            profile = currentProfile,
            onSaveProfile = { vm.saveProfile(it) },
        )
        ImportConfirm(confirmImport, onDismiss = { confirmImport = false }) {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        return
    }

    if (wealthOpen) {
        BackHandler { wealthOpen = false }
        val cycle = com.moneymap.core.Plan.cycleStartFor(today)
        val balances = com.moneymap.core.Pods.balances(podMoves,
            com.moneymap.core.Pods.planPods(plan, cycle) + goals.map { it.pod })
        val netWorth = com.moneymap.core.NetWorthCalc.compute(balances, com.moneymap.core.Ledger.summary(entries),
            investments, loans, today)
        WealthScreen(
            today = today, netWorth = netWorth, history = netWorthHistory, investments = investments, loans = loans,
            snackbar = snackbar,
            onBack = { wealthOpen = false },
            onSaveInvestment = { vm.saveInvestment(it) },
            onDeleteInvestment = { vm.deleteInvestment(it) },
            onSaveLoan = { vm.saveLoan(it) },
            onDeleteLoan = { vm.deleteLoan(it) },
            onOpened = { vm.refreshNetWorth() },
        )
        return
    }

    if (reportsOpen) {
        BackHandler { reportsOpen = false }
        ReportsScreen(
            today = today, plan = plan, expenses = expenses, budgets = categoryBudgets, snackbar = snackbar,
            onBack = { reportsOpen = false },
            onSaveBudgets = { vm.saveCategoryBudgets(it) },
            onAddRegular = { vm.addRegularItem(it) },
        )
        return
    }

    if (searchOpen && !formOpen && detailId == null) {
        BackHandler { searchOpen = false }
        SearchScreen(
            entries = entries, expenses = expenses, podMoves = podMoves,
            today = today,
            onOpenEntry = { detailId = it },
            onBack = { searchOpen = false },
        )
        return
    }

    if (formOpen) {
        BackHandler { formOpen = false }
        EntryFormScreen(
            existing = formEditId?.let { id -> entries.firstOrNull { it.entry.id == id }?.entry },
            people = com.moneymap.core.Ledger.people(entries),
            onSave = { existing, person, direction, amount, reason, date, due, pod, notes ->
                vm.saveEntry(existing, person, direction, amount, reason, date, due, pod, notes)
                formOpen = false
            },
            onClose = { formOpen = false },
        )
        return
    }

    val detail = detailId?.let { id -> entries.firstOrNull { it.entry.id == id } }
    if (detailId != null && detail != null) {
        BackHandler { detailId = null }
        EntryDetailScreen(
            data = detail,
            today = today,
            snackbar = snackbar,
            startRecording = recordOnOpen,
            onRecordingShown = { recordOnOpen = false },
            onBack = { detailId = null },
            onEdit = { openForm(detail.entry.id) },
            onDelete = { vm.deleteEntry(detail.entry.id); detailId = null },
            onRecord = { amount, date, note -> vm.recordSettlement(detail.entry.id, amount, date, note) },
            onUndo = { vm.undoLast(detail.entry.id) },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[tab].label) },
                actions = {
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Settings & plan") }, onClick = {
                            menuOpen = false; settingsOpen = true
                        })
                        DropdownMenuItem(text = { Text("Net worth, investments & loans") }, onClick = {
                            menuOpen = false; wealthOpen = true
                        })
                        DropdownMenuItem(text = { Text("Reports & budgets") }, onClick = {
                            menuOpen = false; reportsOpen = true
                        })
                        DropdownMenuItem(text = { Text("Export backup") }, onClick = {
                            menuOpen = false; vm.export()
                        })
                        DropdownMenuItem(text = { Text("Send test notification") }, onClick = {
                            menuOpen = false; vm.sendTestNotification()
                        })
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (tab) {
            0 -> MonthScreen(
                today = today, plan = plan, entries = entries, done = done, modifier = mod,
                onToggle = { id, checked -> vm.setDone(id, checked) },
                onOpenEntry = { detailId = it },
            )
            1 -> SpendScreen(
                today = today, plan = plan, expenses = expenses, people = com.moneymap.core.Ledger.people(entries), modifier = mod,
                onAdd = { amount, note, category -> vm.addExpense(amount, note, category) },
                onPaidForSomeone = { person, amount, note -> vm.paidForSomeone(person, amount, note) },
                onDelete = { vm.deleteExpense(it) },
                suggestions = suggestions,
                onAcceptSuggestion = { s, note, category -> vm.acceptSuggestion(s, note, category) },
                onDismissSuggestion = { vm.dismissSuggestion(it) },
                receiptIds = receiptIds,
                loadReceipt = { vm.loadReceipt(it) },
                cameraUri = { vm.receiptCameraUri() },
                onAttachReceipt = { id, uri -> vm.attachReceipt(id, uri) },
                onRemoveReceipt = { vm.removeReceipt(it) },
                onSplit = { total, note, category, others -> vm.splitBill(total, note, category, others) },
                onEditExpense = { updated, original -> vm.editExpense(updated, original) },
            )
            3 -> SaveScreen(
                today = today, plan = plan, podMoves = podMoves, goals = goals, modifier = mod,
                onPodMove = { pod, amount, note -> vm.addPodMove(pod, amount, note) },
                onDeletePodMove = { vm.deletePodMove(it) },
                onSaveGoal = { vm.saveGoal(it) },
                onDeleteGoal = { vm.deleteGoal(it) },
            )
            else -> PeopleScreen(
                entries = entries, today = today, modifier = mod,
                onOpen = { detailId = it },
                onAdd = { openForm(null) },
                onEdit = { openForm(it) },
                onDelete = { vm.deleteEntry(it) },
                onRecord = { id, amount, date, note -> vm.recordSettlement(id, amount, date, note) },
            )
        }
    }

}

@Composable
private fun ImportConfirm(show: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (!show) return
    ConfirmDialog(
        title = "Import backup?",
        text = "This replaces everything in the app (people, expenses, pods, goals, plan and ticks) with the backup file.",
        confirmLabel = "Choose file",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
