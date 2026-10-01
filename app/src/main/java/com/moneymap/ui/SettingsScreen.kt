package com.moneymap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymap.core.DebtInstalment
import com.moneymap.core.ExtraIncome
import com.moneymap.core.Flow
import com.moneymap.core.OneOff
import com.moneymap.core.Plan
import com.moneymap.core.PlanEngine
import com.moneymap.core.PlanSettings
import com.moneymap.core.Profile
import com.moneymap.core.RecurringItem
import com.moneymap.core.ReminderPrefs
import com.moneymap.core.formatInr
import com.moneymap.core.long
import com.moneymap.core.monthYear
import com.moneymap.core.parseAmount
import com.moneymap.core.short
import com.moneymap.ui.theme.dynamicColorSupported
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BackupState(val folderLabel: String?, val lastBackupAt: Long, val passwordSet: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    today: LocalDate,
    plan: PlanEngine,
    backup: BackupState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSavePlan: (PlanSettings) -> Unit,
    onChooseFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onBackupOff: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onTestNotification: () -> Unit,
    onExportToDrive: () -> Unit = {},
    reminderPrefs: ReminderPrefs = ReminderPrefs(),
    onSaveReminders: (ReminderPrefs) -> Unit = {},
    lockEnabled: Boolean = false,
    onLockChange: (Boolean) -> Unit = {},
    dynamicColor: Boolean = false,
    onDynamicColorChange: (Boolean) -> Unit = {},
    onBackupPassword: (String?) -> Unit = {},
    profile: Profile = Plan.profile,
    onSaveProfile: (Profile) -> Unit = {},
    onSaveCardBills: (List<com.moneymap.core.CardBill>) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlanEditor(today, plan, onSavePlan)

            ProfileCard(profile, onSaveProfile)

            CardBillsCard(today, plan.settings.cardBills, onSaveCardBills)

            ReminderSettings(reminderPrefs, onSaveReminders)

            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("App lock", style = MaterialTheme.typography.titleMedium)
                        Text("Ask for fingerprint, face or screen lock when opening the app.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = lockEnabled, onCheckedChange = onLockChange)
                }
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatic backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (backup.folderLabel == null) "Off. Pick a folder and a full backup is saved there every week."
                        else "Weekly to: ${backup.folderLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (backup.lastBackupAt > 0) {
                        Text("Last backup: ${formatTime(backup.lastBackupAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Tip: pick a folder that your phone syncs or copies off the device.",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onChooseFolder) {
                            Text(if (backup.folderLabel == null) "Choose folder" else "Change folder")
                        }
                        if (backup.folderLabel != null) {
                            OutlinedButton(onClick = onBackupNow) { Text("Back up now") }
                        }
                    }
                    if (backup.folderLabel != null) {
                        TextButton(onClick = onBackupOff) { Text("Turn off") }
                    }
                }
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
                    Text("Share a JSON backup (with readable CSVs), or restore from a JSON backup.",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onExport) { Text("Export") }
                        OutlinedButton(onClick = onImport) { Text("Import…") }
                    }
                    OutlinedButton(onClick = onExportToDrive) { Text("Save backup to Google Drive") }
                    Text("Opens Drive's upload screen with the backup file. Set a backup password first to keep it " +
                        "encrypted in Drive.", style = MaterialTheme.typography.bodySmall)
                }
            }

            BackupPasswordCard(backup.passwordSet, onBackupPassword)

            NotificationAccessCard()

            if (dynamicColorSupported) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Wallpaper colours", style = MaterialTheme.typography.titleMedium)
                            Text("Use Material You colours from your wallpaper instead of Money map green.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }
                }
            }

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    Text("For reminders to arrive on time, set Battery to Unrestricted for Money map in Android settings.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onTestNotification) { Text("Send test notification") }
                }
            }
        }
    }
}

/** Credit card statements due: added automatically from statement notifications, or by hand. */
@Composable
private fun CardBillsCard(today: LocalDate, bills: List<com.moneymap.core.CardBill>, onSave: (List<com.moneymap.core.CardBill>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val upcoming = bills.filter { !it.dueDate.isBefore(today.minusDays(7)) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Credit card bills", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
            Text("Statements from card notifications are added here and show on Month with reminders (needs " +
                "\"Expenses from notifications\" on). They're reminders only: the spends were already logged.",
                style = MaterialTheme.typography.bodySmall)
            upcoming.forEach { b ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${b.card} · ${formatInr(b.amount)}", style = MaterialTheme.typography.bodyLarge)
                        Text("Due ${b.dueDate.long()}" + (b.minimumDue?.let { " · min ${formatInr(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onSave(bills - b) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                }
            }
        }
    }
    if (adding) {
        var card by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var due by remember { mutableStateOf(today.plusDays(14)) }
        val parsed = parseAmount(amount)
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add card bill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(card, { card = it }, label = { Text("Card (e.g. HDFC Regalia)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    AmountField(amount, { amount = it }, label = "Total due")
                    DateField("Due", due, { if (it != null) due = it })
                }
            },
            confirmButton = {
                TextButton(enabled = parsed != null && card.isNotBlank(), onClick = {
                    val bill = com.moneymap.core.CardBill("${card.trim().lowercase().replace(" ", "")}-$due", card.trim(), parsed!!, null, due)
                    onSave(com.moneymap.core.CardBillParser.merge(bills, bill, today))
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, onSave: (Profile) -> Unit) {
    var salaryAccount by remember(profile) { mutableStateOf(profile.salaryAccount) }
    var spendAccount by remember(profile) { mutableStateOf(profile.spendAccount) }
    var goalPod by remember(profile) { mutableStateOf(profile.goalPod) }
    var lender by remember(profile) { mutableStateOf(profile.lenderName) }
    var weekendEarly by remember(profile) { mutableStateOf(profile.weekendSalaryEarly) }
    var currency by remember(profile) { mutableStateOf(profile.currencySymbol) }
    var indian by remember(profile) { mutableStateOf(profile.indianGrouping) }
    var loanText by remember(profile) { mutableStateOf(if (profile.loanTotal > 0) formatInr(profile.loanTotal, withSymbol = false) else "") }
    val loan = if (loanText.isBlank()) 0L else parseAmount(loanText)
    val edited = profile.copy(
        salaryAccount = salaryAccount.trim(), spendAccount = spendAccount.trim(), goalPod = goalPod.trim(),
        lenderName = lender.trim(), loanTotal = loan ?: profile.loanTotal, weekendSalaryEarly = weekendEarly,
        currencySymbol = currency.trim(), indianGrouping = indian,
    )
    val valid = loan != null && listOf(salaryAccount, spendAccount, goalPod, lender).all { it.isNotBlank() }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Accounts & loan", style = MaterialTheme.typography.titleMedium)
            Text("Salary day: ${profile.salaryDay} (set when the app was first set up; changing it would move every " +
                "past cycle).", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Salary comes the Friday before when salary day is a weekend",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = weekendEarly, onCheckedChange = { weekendEarly = it })
            }
            OutlinedTextField(salaryAccount, { salaryAccount = it }, label = { Text("Salary account (bills paid from)") },
                singleLine = true, isError = salaryAccount.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(spendAccount, { spendAccount = it }, label = { Text("Spending account (holds the pods)") },
                singleLine = true, isError = spendAccount.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(goalPod, { goalPod = it }, label = { Text("Extra savings pod for returns") },
                singleLine = true, isError = goalPod.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lender, { lender = it }, label = { Text("Lender for loan instalments") },
                singleLine = true, isError = lender.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(loanText, { v -> loanText = v.filter { it.isDigit() || it == ',' } },
                label = { Text("Loan total (${Plan.CURRENCY}, blank for none)") }, singleLine = true, isError = loan == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(currency, { currency = it.take(4) }, label = { Text("Currency symbol") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Indian grouping (" + formatInr(150_000, symbol = currency.trim(), indian = true) + " instead of " +
                    formatInr(150_000, symbol = currency.trim(), indian = false) + ")",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = indian, onCheckedChange = { indian = it })
            }
            Button(enabled = valid && edited != profile, onClick = { onSave(edited) }) { Text("Save") }
        }
    }
}

/** Lets the notification listener read bank / UPI alerts to suggest expenses. */
@Composable
private fun NotificationAccessCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        enabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        onPauseOrDispose { }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Expenses from notifications", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "On. Payments from bank and UPI notifications show up on Spend for you to add or ignore."
                else "Off. Allow notification access and Money map suggests expenses from bank, card and UPI " +
                    "notifications. They stay on this phone and nothing is added without your tap.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text(if (enabled) "Turn off in Android settings" else "Allow notification access") }
        }
    }
}

@Composable
private fun BackupPasswordCard(passwordSet: Boolean, onSave: (String?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup password", style = MaterialTheme.typography.titleMedium)
            Text(
                if (passwordSet) "On. Exports and weekly backups are encrypted (.mmbackup) and CSVs are left out. " +
                    "Remember it: you need it to restore on a new phone."
                else "Off. Backups are readable JSON. Set a password to encrypt them.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { editing = true }) { Text(if (passwordSet) "Change" else "Set password") }
                if (passwordSet) OutlinedButton(onClick = { onSave(null) }) { Text("Remove") }
            }
        }
    }
    if (editing) {
        var first by remember { mutableStateOf("") }
        var second by remember { mutableStateOf("") }
        val valid = first.length >= 6 && first == second
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Backup password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasswordField(first, { first = it }, "Password (at least 6 characters)")
                    PasswordField(second, { second = it }, "Repeat password", isError = second.isNotEmpty() && second != first)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { onSave(first); editing = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))

@Composable
private fun PlanEditor(today: LocalDate, plan: PlanEngine, onSave: (PlanSettings) -> Unit) {
    val settings = plan.settings
    var offset by rememberSaveable { mutableLongStateOf(0L) }
    val from = Plan.cycleStartFor(today).plusMonths(offset)
    var draft by remember(from, settings) { mutableStateOf(settings.baseConfigFor(from)) }
    var debt by remember(settings) { mutableStateOf(settings.debt.sortedBy { it.date }) }
    var oneOffs by remember(settings) { mutableStateOf(settings.oneOffs.sortedBy { it.date }) }
    var extra by remember(settings) { mutableStateOf(settings.extraIncome.sortedBy { it.date }) }
    var overrides by remember(settings) { mutableStateOf(settings.salaryOverrides) }
    var addExtra by remember { mutableStateOf(false) }
    var overrideText by remember(from, settings) {
        mutableStateOf(settings.salaryOverrides[from]?.let { formatInr(it, withSymbol = false) } ?: "")
    }
    var editOneOff by remember { mutableStateOf<OneOff?>(null) }
    var addOneOff by remember { mutableStateOf(false) }
    var showYear by rememberSaveable { mutableStateOf(false) }
    var salaryText by remember(from, settings) { mutableStateOf(formatInr(settings.baseConfigFor(from).salary, withSymbol = false)) }
    var budgetText by remember(from, settings) { mutableStateOf(formatInr(settings.baseConfigFor(from).spendBudget, withSymbol = false)) }
    var editItem by remember { mutableStateOf<RecurringItem?>(null) }
    var addItem by remember { mutableStateOf(false) }
    var addDebt by remember { mutableStateOf(false) }
    var removeVersion by remember { mutableStateOf<LocalDate?>(null) }

    val salary = parseAmount(salaryText)
    val budget = parseAmount(budgetText)
    val working = draft.copy(salary = salary ?: draft.salary, spendBudget = budget ?: draft.spendBudget)
    val overrideValue = if (overrideText.isBlank()) null else parseAmount(overrideText)
    val overrideValid = overrideText.isBlank() || overrideValue != null
    val workingOverrides = if (overrideValue == null) overrides - from else overrides + (from to overrideValue)
    val draftSettings = settings.withVersion(from, working)
        .copy(debt = debt, oneOffs = oneOffs, extraIncome = extra, salaryOverrides = workingOverrides)
    val previewEngine = PlanEngine(draftSettings)
    val before = plan.budget(from)
    val after = previewEngine.budget(from)
    val changed = working != settings.baseConfigFor(from) || debt != settings.debt.sortedBy { it.date } ||
        oneOffs != settings.oneOffs.sortedBy { it.date } || extra != settings.extraIncome.sortedBy { it.date } ||
        workingOverrides != settings.salaryOverrides

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your plan", style = MaterialTheme.typography.titleMedium)
            Text("Changes apply from the chosen cycle onward. Earlier cycles keep their numbers.",
                style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier cycle")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("From ${Plan.cycleLabel(from)}", style = MaterialTheme.typography.titleSmall)
                    Text("${from.short()} onward", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { offset++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later cycle")
                }
            }
            OutlinedTextField(salaryText, { v -> salaryText = v.filter { it.isDigit() || it == ',' } },
                label = { Text("Salary (${Plan.CURRENCY}, on day ${Plan.SALARY_DAY})") }, singleLine = true, isError = salary == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(budgetText, { v -> budgetText = v.filter { it.isDigit() || it == ',' } },
                label = { Text("Monthly spending budget (${Plan.CURRENCY}, ${Plan.SPEND_MAIN})") }, singleLine = true, isError = budget == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(overrideText, { v -> overrideText = v.filter { it.isDigit() || it == ',' } },
                label = { Text("Salary for ${from.monthYear()} cycle only (${Plan.CURRENCY})") },
                supportingText = { Text("Leave blank to use the plan. For a bonus month, a raise not yet in the plan, or unpaid leave.") },
                singleLine = true, isError = !overrideValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Regular items", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { addItem = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
            working.items.sortedWith(compareBy({ it.flow.ordinal }, { (it.day - Plan.SALARY_DAY + 31) % 31 })).forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { editItem = item }.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(itemSubtitle(item), style = MaterialTheme.typography.bodySmall)
                    }
                    Text((if (item.flow == Flow.INCOME) "+" else "") + formatInr(item.amount),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Loan repayments", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { addDebt = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
            debt.forEach { d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.date.long(), Modifier.weight(1f))
                    Text(formatInr(d.amount))
                    IconButton(onClick = { debt = debt - d }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                }
            }
            Text("Total ${formatInr(debt.sumOf { it.amount })}" +
                if (Plan.LOAN_TOTAL > 0) " of ${formatInr(Plan.LOAN_TOTAL)}" else "",
                style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Planned one-offs", style = MaterialTheme.typography.titleSmall)
                    Text("Yearly premiums, services, festivals… saved in their own pod",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { addOneOff = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
            oneOffs.forEach { o ->
                Row(Modifier.fillMaxWidth().clickable { editOneOff = o }.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(o.title, style = MaterialTheme.typography.bodyLarge)
                        val n = o.fundingCycles().size
                        Text("${o.date.long()} · " + if (n > 1) "save over $n cycles" else "set aside in ${o.dueCycle.monthYear()}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatInr(o.amount), style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Extra income", style = MaterialTheme.typography.titleSmall)
                    Text("Bonuses, refunds, freelance pay… added to that cycle's emergency pod",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { addExtra = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
            extra.forEach { x ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(x.title, style = MaterialTheme.typography.bodyLarge)
                        Text(x.date.long(), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("+" + formatInr(x.amount))
                    IconButton(onClick = { extra = extra - x }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                }
            }

            HorizontalDivider()
            Text("Emergency fund in ${from.monthYear()} cycle: ${formatInr(after.emergencyPod)}" +
                if (after.emergencyPod != before.emergencyPod) " (now ${formatInr(before.emergencyPod)})" else "",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (after.emergencyPod < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (after.emergencyPod < 0) {
                Text("Spending more than comes in this cycle.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = { showYear = !showYear }) {
                Text(if (showYear) "Hide next 12 months" else if (changed) "Compare next 12 months" else "Show next 12 months")
            }
            if (showYear) YearTable(from, plan, previewEngine, changed)
            Button(
                enabled = changed && salary != null && budget != null && overrideValid,
                onClick = { onSave(draftSettings) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save plan from ${from.monthYear()}") }

            if (settings.versions.size > 1) {
                HorizontalDivider()
                Text("Plan changes", style = MaterialTheme.typography.titleSmall)
                settings.sortedVersions.forEachIndexed { i, v ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (i == 0) "Starting plan" else "From ${Plan.cycleLabel(v.from)}") +
                                " · salary ${formatInr(v.config.salary)}",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        )
                        if (i > 0) {
                            IconButton(onClick = { removeVersion = v.from }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove change")
                            }
                        }
                    }
                }
            }
        }
    }

    if (addItem || editItem != null) {
        ItemDialog(
            existing = editItem,
            onDismiss = { addItem = false; editItem = null },
            onDelete = { id -> draft = working.copy(items = working.items.filter { it.id != id }); editItem = null },
            onSave = { item ->
                val others = working.items.filter { it.id != item.id }
                draft = working.copy(items = others + item)
                addItem = false; editItem = null
            },
        )
    }
    if (addOneOff || editOneOff != null) {
        OneOffDialog(
            existing = editOneOff,
            today = today,
            onDismiss = { addOneOff = false; editOneOff = null },
            onDelete = { id -> oneOffs = oneOffs.filter { it.id != id }; editOneOff = null },
            onSave = { o ->
                oneOffs = (oneOffs.filter { it.id != o.id } + o).sortedBy { it.date }
                addOneOff = false; editOneOff = null
            },
        )
    }
    if (addExtra) {
        ExtraIncomeDialog(onDismiss = { addExtra = false }) { x -> extra = (extra + x).sortedBy { it.date }; addExtra = false }
    }
    if (addDebt) {
        DebtDialog(onDismiss = { addDebt = false }) { d -> debt = (debt + d).sortedBy { it.date }; addDebt = false }
    }
    removeVersion?.let { v ->
        ConfirmDialog("Remove this change?", "The plan from ${Plan.cycleLabel(v)} will go back to the one before it.",
            "Remove", onConfirm = { onSave(settings.withoutVersion(v)) }, onDismiss = { removeVersion = null })
    }
}

private fun itemSubtitle(i: RecurringItem): String = buildString {
    append(if (i.day == Plan.SALARY_DAY) "Salary day" else "Day ${i.day}")
    append(" · ")
    append(when (i.flow) {
        Flow.INCOME -> "Income into ${i.account}"
        Flow.HDFC -> if (i.autopay) "${Plan.HDFC} autopay" else "Paid from ${Plan.HDFC}"
        Flow.POD -> "From ${i.pod.ifBlank { "pod" }}" + if (i.autopay) " · autopay" else ""
    })
    i.startDate?.let { append(" · from ${it.short()} ${it.year}") }
    i.endDate?.let { append(" · until ${it.short()} ${it.year}") }
}

@Composable
private fun ItemDialog(
    existing: RecurringItem?,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onSave: (RecurringItem) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.let { formatInr(it, withSymbol = false) } ?: "") }
    var day by rememberSaveable { mutableStateOf(existing?.day?.toString() ?: "") }
    var flow by rememberSaveable { mutableStateOf(existing?.flow ?: Flow.HDFC) }
    var autopay by rememberSaveable { mutableStateOf(existing?.autopay ?: true) }
    var account by rememberSaveable { mutableStateOf(existing?.account ?: "") }
    var pod by rememberSaveable { mutableStateOf(existing?.pod ?: "") }
    var start by remember { mutableStateOf(existing?.startDate) }
    var end by remember { mutableStateOf(existing?.endDate) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parsed = parseAmount(amount)
    val dayNum = day.toIntOrNull()?.takeIf { it in 1..31 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add regular item" else "Edit ${existing.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true,
                    isError = showErrors && title.isBlank(), modifier = Modifier.fillMaxWidth())
                AmountField(amount, { amount = it }, isError = showErrors && parsed == null)
                OutlinedTextField(day, { v -> day = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Day of month (25 = salary day)") }, singleLine = true,
                    isError = showErrors && dayNum == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Type", style = MaterialTheme.typography.titleSmall)
                Column {
                    Flow.entries.forEach { f ->
                        FilterChip(selected = flow == f, onClick = { flow = f }, label = { Text(f.label) })
                    }
                }
                if (flow != Flow.INCOME) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Autopay", Modifier.weight(1f))
                        Switch(checked = autopay, onCheckedChange = { autopay = it })
                    }
                }
                if (flow == Flow.POD) {
                    OutlinedTextField(pod, { pod = it }, label = { Text("Pod name, e.g. SIP pod") }, singleLine = true,
                        isError = showErrors && pod.isBlank(), modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(account, { account = it },
                    label = { Text(if (flow == Flow.INCOME) "Arrives in (e.g. ${Plan.HDFC})" else "Account (e.g. ${Plan.HDFC}, ${Plan.JUPITER})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("Starts", start, { start = it }, clearable = true)
                DateField("Ends", end, { end = it }, clearable = true)
                Text("Leave start/end empty for something that goes on every month.",
                    style = MaterialTheme.typography.bodySmall)
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Remove this item", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = parsed
                val d = dayNum
                if (title.isBlank() || a == null || d == null || (flow == Flow.POD && pod.isBlank())) {
                    showErrors = true
                } else {
                    onSave(
                        RecurringItem(
                            id = existing?.id ?: "item-${System.currentTimeMillis()}",
                            title = title.trim(),
                            amount = a,
                            day = d,
                            flow = flow,
                            account = account.trim().ifBlank { if (flow == Flow.POD) Plan.JUPITER else Plan.HDFC },
                            autopay = flow != Flow.INCOME && autopay,
                            pod = if (flow == Flow.POD) pod.trim() else "",
                            startDate = start,
                            endDate = end,
                        )
                    )
                }
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog("Remove ${existing.title}?", "It stops from the chosen cycle once you save the plan.", "Remove",
            onConfirm = { onDelete(existing.id) }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun ExtraIncomeDialog(onDismiss: () -> Unit, onAdd: (ExtraIncome) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add extra income") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("What (e.g. Bonus)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                AmountField(amount, { amount = it })
                DateField("Arrives", date, { if (it != null) date = it })
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && title.isNotBlank(), onClick = {
                parsed?.let { onAdd(ExtraIncome("x${System.currentTimeMillis()}", title.trim(), it, date)) }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DebtDialog(onDismiss: () -> Unit, onAdd: (DebtInstalment) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().let { Plan.cycleStartFor(it).plusMonths(1) }) }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add repayment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(amount, { amount = it })
                DateField("Date", date, { if (it != null) date = it })
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let { onAdd(DebtInstalment(date, it)) } }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


/** Emergency fund for the next 12 cycles, now vs with the unsaved changes. */
@Composable
private fun YearTable(from: LocalDate, current: PlanEngine, draft: PlanEngine, changed: Boolean) {
    val rows = (0L until 12L).map { i ->
        val c = from.plusMonths(i)
        Triple(c, current.budget(c).emergencyPod, draft.budget(c).emergencyPod)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text("Cycle", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(if (changed) "Now" else "Emergency", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            if (changed) {
                Text("After", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Change", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
        }
        rows.forEach { (c, before, after) ->
            val diff = after - before
            Row {
                Text(c.monthYear(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(formatInr(before), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = if (before < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (changed) {
                    Text(formatInr(after), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = if (after < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(if (diff == 0L) "–" else (if (diff > 0) "+" else "") + formatInr(diff), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        color = when {
                            diff > 0 -> MaterialTheme.colorScheme.primary
                            diff < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
            }
        }
        val totalBefore = rows.sumOf { it.second }
        val totalAfter = rows.sumOf { it.third }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(
            if (changed) "12-month emergency fund: ${formatInr(totalBefore)} → ${formatInr(totalAfter)}"
            else "12-month emergency fund: ${formatInr(totalBefore)}",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OneOffDialog(
    existing: OneOff?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onSave: (OneOff) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.let { formatInr(it, withSymbol = false) } ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: today.plusMonths(2)) }
    var spread by rememberSaveable { mutableStateOf(existing?.spreadCycles ?: 1) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val parsed = parseAmount(amount)
    val preview = OneOff(existing?.id ?: "new", title.ifBlank { "One-off" }, parsed ?: 0, date, spread,
        existing?.fundFrom ?: Plan.cycleStartFor(today))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Plan a one-off" else "Edit ${existing.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("What for") }, singleLine = true,
                    isError = showErrors && title.isBlank(), modifier = Modifier.fillMaxWidth())
                AmountField(amount, { amount = it }, isError = showErrors && parsed == null)
                DateField("Due", date, { if (it != null) date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Save over", Modifier.weight(1f))
                    TextButton(onClick = { spread = (spread - 1).coerceAtLeast(1) }) { Text("−") }
                    Text("$spread cycle${if (spread == 1) "" else "s"}")
                    TextButton(onClick = { spread = (spread + 1).coerceAtMost(12) }) { Text("+") }
                }
                val cycles = preview.fundingCycles()
                if (parsed != null) {
                    Text(
                        if (cycles.size == 1) "Set aside ${formatInr(parsed)} on salary day of the ${cycles.first().monthYear()} cycle"
                        else "Set aside about ${formatInr(parsed / cycles.size)} each cycle, " +
                            "${cycles.first().monthYear()} to ${cycles.last().monthYear()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (cycles.size < spread) {
                        Text("Only ${cycles.size} cycle${if (cycles.size == 1) "" else "s"} left before it's due.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (existing != null) {
                    TextButton(onClick = { onDelete(existing.id) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = parsed
                if (title.isBlank() || a == null) showErrors = true
                else onSave(preview.copy(id = existing?.id ?: "oo-${System.currentTimeMillis()}", title = title.trim(), amount = a))
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
