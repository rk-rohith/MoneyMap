package com.moneymap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymap.core.Investment
import com.moneymap.core.InvestmentKind
import com.moneymap.core.InvestmentTxn
import com.moneymap.core.Investments
import com.moneymap.core.Loan
import com.moneymap.core.LoanMath
import com.moneymap.core.NetWorth
import com.moneymap.core.NetWorthCalc
import com.moneymap.core.NetWorthPoint
import com.moneymap.core.Prepayment
import com.moneymap.core.formatInr
import com.moneymap.core.long
import com.moneymap.core.monthYear
import com.moneymap.core.parseAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/** Net worth with its trend, investments (SIPs, FDs…) and the loan planner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthScreen(
    today: LocalDate,
    netWorth: NetWorth,
    history: List<NetWorthPoint>,
    investments: List<Investment>,
    loans: List<Loan>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSaveInvestment: (Investment) -> Unit,
    onDeleteInvestment: (String) -> Unit,
    onSaveLoan: (Loan) -> Unit,
    onDeleteLoan: (String) -> Unit,
    onOpened: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { onOpened() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Net worth") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Overview", "Investments", "Loans").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> NetWorthTab(netWorth, history)
                1 -> InvestmentsTab(today, investments, onSaveInvestment, onDeleteInvestment)
                else -> LoansTab(today, loans, onSaveLoan, onDeleteLoan)
            }
        }
    }
}

@Composable
private fun NetWorthTab(nw: NetWorth, history: List<NetWorthPoint>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("NET WORTH", style = MaterialTheme.typography.labelMedium)
                    Text(formatInr(nw.total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    NetWorthCalc.changeOver(history, 1)?.let {
                        Text((if (it >= 0) "▲ " else "▼ ") + formatInr(kotlin.math.abs(it)) + " since last month",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    NetWorthCalc.changeOver(history, 12)?.let {
                        Text((if (it >= 0) "▲ " else "▼ ") + formatInr(kotlin.math.abs(it)) + " over 12 months",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What makes it up", style = MaterialTheme.typography.titleMedium)
                    WorthRow("Savings pods", nw.pods)
                    WorthRow("Others owe you", nw.owedToMe)
                    WorthRow("Investments", nw.investments)
                    HorizontalDivider()
                    WorthRow("You owe people", -nw.iOwe)
                    WorthRow("Loan balances", -nw.loans)
                    HorizontalDivider()
                    WorthRow("Net worth", nw.total, bold = true)
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Month by month", style = MaterialTheme.typography.titleMedium)
                    if (history.size < 2) {
                        Text("The trend fills in as months go by: this month's figure is saved each time something " +
                            "changes.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        TrendChart(history.takeLast(12))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorthRow(label: String, amount: Long, bold: Boolean = false) {
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else null)
        Text(formatInr(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null,
            color = if (amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TrendChart(points: List<NetWorthPoint>) {
    val line = MaterialTheme.colorScheme.primary
    val zero = MaterialTheme.colorScheme.outline
    val min = minOf(points.minOf { it.total }, 0L)
    val max = maxOf(points.maxOf { it.total }, 0L).let { if (it == min) min + 1 else it }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        fun y(v: Long) = size.height - size.height * (v - min) / (max - min).toFloat()
        val step = if (points.size > 1) size.width / (points.size - 1) else 0f
        drawLine(zero, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 2f)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = step * i
            if (i == 0) path.moveTo(x, y(p.total)) else path.lineTo(x, y(p.total))
            drawCircle(line, radius = 6f, center = Offset(x, y(p.total)))
        }
        drawPath(path, line, style = Stroke(width = 5f))
    }
    Row(Modifier.fillMaxWidth()) {
        points.forEachIndexed { i, p ->
            if (i == 0 || i == points.lastIndex || points.size <= 6) {
                Text(p.month.format(shortMonth), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f))
            } else {
                Text("", Modifier.weight(1f))
            }
        }
    }
}

// ---------- Investments ----------

@Composable
private fun InvestmentsTab(
    today: LocalDate,
    list: List<Investment>,
    onSave: (Investment) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<Investment?>(null) }
    var adding by remember { mutableStateOf(false) }
    val sum = Investments.summary(list)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("INVESTMENTS", style = MaterialTheme.typography.labelMedium)
                    Text(formatInr(sum.value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Invested ${formatInr(sum.invested)} · " + gainText(sum.gain, sum.gainPercent),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add investment") }
        }
        if (list.isEmpty()) {
            item {
                Text("Add your SIPs, FDs, stocks, gold or PPF. For a mutual fund, enter the units from each instalment and " +
                    "the latest NAV to see what it's worth; for anything else, update the value now and then.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        items(list, key = { it.id }) { inv ->
            OutlinedCard(Modifier.fillMaxWidth().clickable { editing = inv }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.name, style = MaterialTheme.typography.titleMedium)
                            Text(inv.kind.label + if (inv.monthlySip > 0) " · ${formatInr(inv.monthlySip)}/month on day ${inv.sipDay}" else "",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatInr(inv.value), style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Invested ${formatInr(inv.invested)} · " + gainText(inv.gain, inv.gainPercent),
                        style = MaterialTheme.typography.bodySmall)
                    if (Investments.sipDue(inv, today)) {
                        TextButton(onClick = { onSave(Investments.addSip(inv, today)) }) {
                            Text("Record this month's ${formatInr(inv.monthlySip)} SIP")
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        InvestmentDialog(
            existing = editing, today = today,
            onDismiss = { adding = false; editing = null },
            onSave = { onSave(it); adding = false; editing = null },
            onDelete = { onDelete(it); editing = null },
        )
    }
}

private fun gainText(gain: Long, pct: Double?): String =
    (if (gain >= 0) "gain " else "loss ") + formatInr(kotlin.math.abs(gain)) +
        (pct?.let { " (${if (it >= 0) "+" else ""}${"%.1f".format(Locale.ENGLISH, it)}%)" } ?: "")

@Composable
private fun InvestmentDialog(
    existing: Investment?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Investment) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: InvestmentKind.SIP) }
    var sipText by remember { mutableStateOf(existing?.monthlySip?.takeIf { it > 0 }?.let { formatInr(it, withSymbol = false) } ?: "") }
    var sipDayText by remember { mutableStateOf((existing?.sipDay ?: 5).toString()) }
    var addText by remember { mutableStateOf("") }
    var unitsText by remember { mutableStateOf("") }
    var addDate by remember { mutableStateOf(today) }
    var valueText by remember { mutableStateOf(existing?.currentValue?.let { formatInr(it, withSymbol = false) } ?: "") }
    var navText by remember { mutableStateOf(existing?.nav?.toString() ?: "") }

    val sip = if (sipText.isBlank()) 0L else parseAmount(sipText)
    val sipDay = sipDayText.toIntOrNull()?.takeIf { it in 1..28 }
    val add = if (addText.isBlank()) null else parseAmount(addText)
    val units = unitsText.toDoubleOrNull()
    val value = if (valueText.isBlank()) null else parseAmount(valueText)
    val nav = navText.toDoubleOrNull()
    val valid = name.isNotBlank() && sip != null && sipDay != null && (addText.isBlank() || add != null) &&
        (valueText.isBlank() || value != null) && (navText.isBlank() || nav != null) && (unitsText.isBlank() || units != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add investment" else existing.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Nifty 50 index fund)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InvestmentKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sipText, { v -> sipText = v.filter { it.isDigit() || it == ',' } },
                        label = { Text("Monthly SIP") }, singleLine = true, isError = sip == null, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(sipDayText, { v -> sipDayText = v.filter { it.isDigit() }.take(2) },
                        label = { Text("SIP day") }, singleLine = true, isError = sipDay == null, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                HorizontalDivider()
                Text("Add money put in" + (existing?.let { " (so far ${formatInr(it.invested)})" } ?: ""),
                    style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(addText, { v -> addText = v.filter { it.isDigit() || it == ',' } },
                        label = { Text("Amount") }, singleLine = true, isError = addText.isNotBlank() && add == null,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(unitsText, { v -> unitsText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("Units") }, singleLine = true, isError = unitsText.isNotBlank() && units == null,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                DateField("On", addDate, { if (it != null) addDate = it })
                HorizontalDivider()
                Text("Current value", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(navText, { v -> navText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("NAV / price") }, singleLine = true, isError = navText.isNotBlank() && nav == null,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(valueText, { v -> valueText = v.filter { it.isDigit() || it == ',' } },
                        label = { Text("or value") }, singleLine = true, isError = valueText.isNotBlank() && value == null,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text("With units and a NAV the value is worked out; otherwise the value you type is used.",
                    style = MaterialTheme.typography.bodySmall)
                if (existing != null) TextButton(onClick = { onDelete(existing.id) }) { Text("Remove investment") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val base = existing ?: Investment(id = "i${System.currentTimeMillis()}", name = name)
                val txns = base.txns + listOfNotNull(add?.let { InvestmentTxn(addDate, it, units) })
                val valueChanged = value != base.currentValue || nav != base.nav
                onSave(base.copy(
                    name = name.trim(), kind = kind, monthlySip = sip ?: 0, sipDay = sipDay ?: 1, txns = txns,
                    currentValue = value, nav = nav, valueUpdated = if (valueChanged) today else base.valueUpdated,
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- Loans ----------

@Composable
private fun LoansTab(today: LocalDate, loans: List<Loan>, onSave: (Loan) -> Unit, onDelete: (String) -> Unit) {
    var editing by remember { mutableStateOf<Loan?>(null) }
    var adding by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Loan?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add loan") }
        }
        if (loans.isEmpty()) {
            item {
                Text("Add a loan with its amount, interest rate and EMI (car, home, personal) to see the full repayment " +
                    "schedule, when it ends, and what a prepayment would save. Interest-free money from family belongs in People.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        items(loans, key = { it.id }) { loan ->
            val s = LoanMath.schedule(loan)
            val left = LoanMath.outstandingOn(loan, today)
            val interestLeft = s.rows.filter { it.date.isAfter(today) }.sumOf { it.interest }
            OutlinedCard(Modifier.fillMaxWidth().clickable { detail = loan }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(loan.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(formatInr(left), style = MaterialTheme.typography.titleMedium)
                    }
                    Text("EMI ${formatInr(loan.emi)} · ${loan.annualRate}% · " +
                        if (s.endsWithinLimit) "ends ${s.payoffDate?.monthYear()}" else "EMI doesn't cover the interest",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.endsWithinLimit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                    Text("Interest still to pay: ${formatInr(interestLeft)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    detail?.let { loan ->
        LoanDetailDialog(loan, today,
            onDismiss = { detail = null },
            onEdit = { editing = loan; detail = null },
            onRecordPrepayment = { p -> onSave(loan.copy(prepayments = loan.prepayments + p)); detail = null })
    }
    if (adding || editing != null) {
        LoanDialog(editing, today,
            onDismiss = { adding = false; editing = null },
            onSave = { onSave(it); adding = false; editing = null },
            onDelete = { onDelete(it); editing = null })
    }
}

@Composable
private fun LoanDetailDialog(
    loan: Loan,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRecordPrepayment: (Prepayment) -> Unit,
) {
    val s = LoanMath.schedule(loan)
    var prepayText by remember { mutableStateOf("") }
    var prepayDate by remember { mutableStateOf(today) }
    val prepay = parseAmount(prepayText)
    val effect = prepay?.let { LoanMath.prepaymentEffect(loan, it, prepayDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loan.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Borrowed ${formatInr(loan.principal)} at ${loan.annualRate}% · EMI ${formatInr(loan.emi)}",
                    style = MaterialTheme.typography.bodyMedium)
                Text(if (s.endsWithinLimit) "Ends ${s.payoffDate?.long()} after ${s.months} EMIs · total interest ${formatInr(s.totalInterest)}"
                else "At this EMI the loan never ends: the EMI is less than the monthly interest.",
                    style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("What if I prepay?", style = MaterialTheme.typography.titleSmall)
                AmountField(prepayText, { prepayText = it }, label = "Extra payment")
                DateField("On", prepayDate, { if (it != null) prepayDate = it })
                if (effect != null) {
                    Text("Saves ${formatInr(effect.interestSaved)} interest and ${effect.monthsSaved} EMIs · " +
                        "ends ${effect.newPayoff?.monthYear() ?: "-"}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { onRecordPrepayment(Prepayment(prepayDate, prepay)) }) { Text("I made this prepayment") }
                }
                HorizontalDivider()
                Text("Schedule (next 12)", style = MaterialTheme.typography.titleSmall)
                Row {
                    listOf("Date", "EMI", "Interest", "Left").forEach {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                s.rows.filter { !it.date.isBefore(today) }.take(12).forEach { r ->
                    Row {
                        Text(r.date.format(DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        Text(formatInr(r.payment + r.extra, withSymbol = false), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(formatInr(r.interest, withSymbol = false), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(formatInr(r.balance, withSymbol = false), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (loan.prepayments.isNotEmpty()) {
                    Text("Prepayments: " + loan.prepayments.joinToString { "${formatInr(it.amount)} on ${it.date.long()}" },
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onEdit) { Text("Edit loan") } },
    )
}

@Composable
private fun LoanDialog(
    existing: Loan?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Loan) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var principalText by remember { mutableStateOf(existing?.principal?.let { formatInr(it, withSymbol = false) } ?: "") }
    var rateText by remember { mutableStateOf(existing?.annualRate?.toString() ?: "") }
    var emiText by remember { mutableStateOf(existing?.emi?.let { formatInr(it, withSymbol = false) } ?: "") }
    var monthsText by remember { mutableStateOf("") }
    var first by remember { mutableStateOf(existing?.firstEmi ?: today.plusMonths(1)) }
    val principal = parseAmount(principalText)
    val rate = rateText.toDoubleOrNull()?.takeIf { it in 0.0..60.0 }
    val emi = parseAmount(emiText)
    val months = monthsText.toIntOrNull()?.takeIf { it in 1..600 }
    val valid = name.isNotBlank() && principal != null && rate != null && emi != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add loan" else "Edit loan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Car loan)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                AmountField(principalText, { principalText = it }, label = "Amount borrowed",
                    isError = principalText.isNotEmpty() && principal == null)
                OutlinedTextField(rateText, { v -> rateText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Interest rate (% a year)") }, singleLine = true,
                    isError = rateText.isNotEmpty() && rate == null, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                AmountField(emiText, { emiText = it }, label = "EMI", isError = emiText.isNotEmpty() && emi == null)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(monthsText, { v -> monthsText = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Months") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    TextButton(enabled = principal != null && rate != null && months != null, onClick = {
                        emiText = formatInr(LoanMath.emiFor(principal!!, rate!!, months!!), withSymbol = false)
                    }) { Text("Work out EMI") }
                }
                DateField("First EMI", first, { if (it != null) first = it })
                if (existing != null) TextButton(onClick = { onDelete(existing.id) }) { Text("Remove loan") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(Loan(existing?.id ?: "l${System.currentTimeMillis()}", name.trim(), principal!!, rate!!, emi!!, first,
                    existing?.prepayments.orEmpty()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
