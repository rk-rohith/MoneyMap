package com.moneymap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneymap.core.CycleReport
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.Plan
import com.moneymap.core.PlanEngine
import com.moneymap.core.RecurringGuess
import com.moneymap.core.Reports
import com.moneymap.core.SpendRecord
import com.moneymap.core.formatInr
import com.moneymap.core.parseAmount
import com.moneymap.data.Expense
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    today: java.time.LocalDate,
    plan: PlanEngine,
    expenses: List<Expense>,
    budgets: Map<ExpenseCategory, Long>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSaveBudgets: (Map<ExpenseCategory, Long>) -> Unit,
    onAddRegular: (RecurringGuess) -> Unit,
) {
    val records = expenses.map { SpendRecord(it.date, it.amount, it.category, it.note) }
    val reports = Reports.cycles(records, plan, today)
    val trends = Reports.trends(reports)
    val cycle = Plan.cycleStartFor(today)
    val status = Reports.budgetStatus(records, cycle, budgets)
    val regularTitles = plan.config(cycle).items.map { it.title }
    val recurring = Reports.recurring(records, today, regularTitles)
    var editBudgets by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Spending per cycle", style = MaterialTheme.typography.titleMedium)
                        Text("Bars are what you spent; the dashed line is the budget.", style = MaterialTheme.typography.bodySmall)
                        SpendChart(reports)
                        val avg = reports.dropLast(1).filter { it.spent > 0 }.map { it.spent }.average()
                        if (!avg.isNaN()) {
                            Text("Earlier cycles averaged ${formatInr(avg.roundToInt().toLong())}",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (trends.isNotEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("This cycle by category", style = MaterialTheme.typography.titleMedium)
                            trends.forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(formatInr(t.thisCycle), style = MaterialTheme.typography.bodyMedium)
                                    val change = t.change
                                    Text(
                                        when {
                                            change == null -> "new"
                                            change >= 0.05f -> "▲ ${(change * 100).roundToInt()}%"
                                            change <= -0.05f -> "▼ ${(-change * 100).roundToInt()}%"
                                            else -> "≈ avg"
                                        },
                                        Modifier.padding(start = 12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if ((change ?: 0f) >= 0.05f) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Category budgets", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { editBudgets = true }) { Text(if (budgets.isEmpty()) "Set" else "Edit") }
                        }
                        if (status.isEmpty()) {
                            Text("Give categories like food or shopping their own monthly limit. You get a heads-up at 80% " +
                                "and when one goes over.", style = MaterialTheme.typography.bodySmall)
                        }
                        status.forEach { s ->
                            Column {
                                Row {
                                    Text(s.category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("${formatInr(s.spent)} of ${formatInr(s.budget)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                LinearProgressIndicator(
                                    progress = { s.ratio.coerceIn(0f, 1f) },
                                    color = when {
                                        s.ratio > 1f -> MaterialTheme.colorScheme.error
                                        s.ratio >= 0.8f -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (recurring.isNotEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Looks like it repeats", style = MaterialTheme.typography.titleMedium)
                            Text("Logged at a steady amount for 3+ months. Add one to your plan to get a reminder and " +
                                "have it set aside on salary day; then stop logging it as spending.",
                                style = MaterialTheme.typography.bodySmall)
                            recurring.forEach { g ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${g.title} · ${formatInr(g.typicalAmount)}", style = MaterialTheme.typography.bodyLarge)
                                        Text("${g.months} months · around day ${g.day} · ${g.category.label}",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { onAddRegular(g) }) { Text("Add to plan") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editBudgets) {
        CategoryBudgetDialog(budgets, onDismiss = { editBudgets = false }) { onSaveBudgets(it); editBudgets = false }
    }
}

@Composable
private fun SpendChart(reports: List<CycleReport>) {
    val maxValue = (reports.maxOfOrNull { maxOf(it.spent, it.budget) } ?: 0L).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val slot = size.width / reports.size
        val barWidth = slot * 0.55f
        reports.forEachIndexed { i, r ->
            val x = slot * i + (slot - barWidth) / 2
            val h = size.height * r.spent / maxValue
            drawRect(if (r.over) over else bar, topLeft = Offset(x, size.height - h), size = Size(barWidth, h))
            val y = size.height - size.height * r.budget / maxValue
            drawLine(line, Offset(slot * i + 2f, y), Offset(slot * (i + 1) - 2f, y), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        reports.forEach { r ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.cycleStart.format(shortMonth), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Text(if (r.spent >= 1000) "${r.spent / 1000}k" else r.spent.toString(),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CategoryBudgetDialog(
    budgets: Map<ExpenseCategory, Long>,
    onDismiss: () -> Unit,
    onSave: (Map<ExpenseCategory, Long>) -> Unit,
) {
    var texts by remember {
        mutableStateOf(ExpenseCategory.entries.associateWith { c -> budgets[c]?.let { formatInr(it, withSymbol = false) } ?: "" })
    }
    val parsed = texts.mapValues { (_, t) -> if (t.isBlank()) 0L else parseAmount(t) }
    val valid = parsed.values.all { it != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly category budgets") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("Leave blank for no limit.", style = MaterialTheme.typography.bodySmall) }
                ExpenseCategory.entries.forEach { c ->
                    item {
                        OutlinedTextField(
                            texts.getValue(c), { v -> texts = texts + (c to v.filter { it.isDigit() || it == ',' }) },
                            label = { Text(c.label) }, singleLine = true, prefix = { Text(Plan.CURRENCY) },
                            isError = parsed[c] == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(parsed.mapValues { it.value ?: 0L }.filterValues { it > 0 }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
