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
import androidx.compose.material3.SnackbarHostState
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyMapRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val done by vm.doneIds.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val openRequest by vm.openRequest.collectAsStateWithLifecycle()

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
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.exportUris.collect { uris ->
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = ClipData.newRawUri("Money map export", uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                putExtra(Intent.EXTRA_SUBJECT, "Money map backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share backup"))
        }
    }
    LaunchedEffect(openRequest) {
        val req = openRequest ?: return@LaunchedEffect
        req.tab?.let { tab = it }
        if (req.entryId != null) {
            tab = 2
            formOpen = false
            detailId = req.entryId
            recordOnOpen = req.record
        }
        vm.consumeOpenRequest()
    }

    val openForm: (Long?) -> Unit = { id -> formEditId = id; formOpen = true }

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
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Export backup (JSON + CSV)") }, onClick = {
                            menuOpen = false; vm.export()
                        })
                        DropdownMenuItem(text = { Text("Import backup…") }, onClick = {
                            menuOpen = false; confirmImport = true
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
                today = today, entries = entries, done = done, modifier = mod,
                onToggle = { id, checked -> vm.setDone(id, checked) },
                onOpenEntry = { detailId = it },
            )
            1 -> SpendScreen(
                today = today, expenses = expenses, people = com.moneymap.core.Ledger.people(entries), modifier = mod,
                onAdd = { amount, note -> vm.addExpense(amount, note) },
                onPaidForSomeone = { person, amount, note -> vm.paidForSomeone(person, amount, note) },
                onDelete = { vm.deleteExpense(it) },
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

    if (confirmImport) {
        ConfirmDialog(
            title = "Import backup?",
            text = "This replaces everything in the app (people, transactions, expenses and ticks) with the backup file.",
            confirmLabel = "Choose file",
            onConfirm = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onDismiss = { confirmImport = false },
        )
    }
}
