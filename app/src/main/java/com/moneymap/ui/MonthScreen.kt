package com.moneymap.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.ItemKind
import com.moneymap.core.Plan
import com.moneymap.core.PlanItem
import com.moneymap.core.SeedData
import com.moneymap.core.formatInr
import com.moneymap.core.short
import com.moneymap.core.withDay
import com.moneymap.ui.theme.MoneyColors
import java.time.LocalDate

@Composable
fun MonthScreen(
    today: LocalDate,
    entries: List<EntryWithTxns>,
    done: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onOpenEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCycle = Plan.cycleStartFor(today)
    var offset by rememberSaveable { mutableLongStateOf(0L) }
    var splitOpen by rememberSaveable { mutableStateOf(false) }
    val cycle = currentCycle.plusMonths(offset)
    val cycleItems = remember(cycle) { Plan.items(cycle) }
    val budget = remember(cycle) { Plan.budget(cycle) }
    val loan = entries.firstOrNull { it.entry.seedKey == SeedData.LENDER_KEY }

    val overdue = remember(today, done) {
        Plan.itemsBetween(maxOf(Plan.TRACK_START, today.minusDays(62)), today.minusDays(1))
            .filter { it.notifies && it.id !in done }
    }
    val nextUp = remember(today, done) {
        Plan.itemsBetween(maxOf(today, Plan.TRACK_START), today.plusDays(62)).firstOrNull { it.notifies && it.id !in done }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (overdue.isNotEmpty()) {
            item { OverdueCard(overdue, today) { onToggle(it, true) } }
        }
        if (nextUp != null && offset == 0L) {
            item { NextUpCard(nextUp, today) { onToggle(nextUp.id, true) } }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous cycle")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Plan.cycleLabel(cycle), style = MaterialTheme.typography.titleMedium)
                    Text("${cycle.short()} – ${Plan.cycleEnd(cycle).short()}",
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { offset++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next cycle")
                }
            }
            if (offset != 0L) {
                TextButton(onClick = { offset = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Back to this cycle") }
            }
        }
        item {
            SalarySplitCard(budget, expanded = splitOpen, onToggle = { splitOpen = !splitOpen })
        }
        items(cycleItems, key = { it.id }) { item ->
            PaymentCard(
                item = item,
                today = today,
                done = item.id in done,
                loan = if (item.kind == ItemKind.SALARY_DAY || item.kind == ItemKind.DEBT) loan else null,
                onToggle = { onToggle(item.id, it) },
                onOpenLoan = { loan?.let { onOpenEntry(it.entry.id) } },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OverdueCard(items: List<PlanItem>, today: LocalDate, onDone: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Overdue (${items.size})", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title + if (item.amount > 0) " · ${formatInr(item.amount)}" else "",
                            color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                        Text("Due ${item.date.withDay()} · ${relativeDay(item.date, today)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    TextButton(onClick = { onDone(item.id) }) { Text("Mark done") }
                }
            }
        }
    }
}

@Composable
private fun NextUpCard(item: PlanItem, today: LocalDate, onDone: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text("NEXT UP · ${relativeDay(item.date, today).uppercase()}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(item.title, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
            if (item.amount > 0) {
                Text(formatInr(item.amount), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(listOf(item.date.withDay(), item.account).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (item.detail.isNotBlank()) {
                Text(item.detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Button(onClick = onDone, modifier = Modifier.padding(top = 12.dp)) { Text("Mark done") }
        }
    }
}

@Composable
private fun SalarySplitCard(b: com.moneymap.core.CycleBudget, expanded: Boolean, onToggle: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Salary split", style = MaterialTheme.typography.titleMedium)
                    Text("Emergency fund this cycle: ${formatInr(b.emergencyPod)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand")
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    SplitRow("Salary (HDFC)", b.salary)
                    if (b.rentReceived > 0) SplitRow("Rent received", b.rentReceived)
                    SplitRow("Total in", b.income, bold = true)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SplitRow("House utility (HDFC)", -b.utility)
                    SplitRow("Keep in HDFC for bills", -b.hdfcHold)
                    Text(
                        buildString {
                            append("Term ${formatInr(b.termInsurance)} · Car EMI ${formatInr(b.carEmi)} · SIP ${formatInr(b.hdfcSip)}")
                            if (b.rentPaid > 0) append(" · Rent ${formatInr(b.rentPaid)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    SplitRow("Send to Jupiter", b.transferToJupiter, bold = true)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    if (b.debtPod > 0) SplitRow("Debt pod", b.debtPod)
                    SplitRow("SIP pod", b.sipPod)
                    SplitRow("Jupiter main (spending)", b.jupiterMain)
                    SplitRow("Emergency pod", b.emergencyPod, bold = true)
                }
            }
        }
    }
}

@Composable
private fun SplitRow(label: String, amount: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(
            formatInr(amount),
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (amount < 0) MoneyColors.negative else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PaymentCard(
    item: PlanItem,
    today: LocalDate,
    done: Boolean,
    loan: EntryWithTxns?,
    onToggle: (Boolean) -> Unit,
    onOpenLoan: () -> Unit,
) {
    val overdue = !done && item.notifies && item.tracked && item.date.isBefore(today)
    val isToday = item.date == today
    val container = when {
        overdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isToday && !done -> MaterialTheme.colorScheme.tertiaryContainer
        item.kind == ItemKind.SALARY_DAY -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = done, onCheckedChange = onToggle)
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.amount > 0) {
                        Text(
                            (if (item.kind == ItemKind.INCOME) "+" else "") + formatInr(item.amount),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (item.kind == ItemKind.INCOME) MoneyColors.positive else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                val dateLine = buildString {
                    append(item.date.withDay())
                    if (item.account.isNotBlank()) append(" · ${item.account}")
                    if (!item.tracked) append(" · before tracking")
                    else if (overdue) append(" · overdue")
                    else if (!done) append(" · ${relativeDay(item.date, today)}")
                }
                Text(dateLine, style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.detail.isNotBlank() && item.kind != ItemKind.SALARY_DAY) {
                    Text(item.detail, style = MaterialTheme.typography.bodySmall)
                }
                if (item.steps.isNotEmpty()) {
                    Column(Modifier.padding(top = 6.dp)) {
                        item.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (loan != null) {
                    LoanProgress(loan, onOpenLoan)
                }
            }
        }
    }
}

@Composable
private fun LoanProgress(loan: EntryWithTxns, onOpen: () -> Unit) {
    Column(Modifier.padding(top = 8.dp).clickable(onClick = onOpen)) {
        Text(
            "₹2L loan: ${formatInr(loan.settled)} repaid · ${formatInr(loan.outstanding)} left",
            style = MaterialTheme.typography.labelMedium,
        )
        LinearProgressIndicator(
            progress = { loan.progress },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        AssistChip(onClick = onOpen, label = { Text("Open loan history") }, modifier = Modifier.padding(top = 4.dp))
    }
}
