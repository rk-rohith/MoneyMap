package com.moneymap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneymap.core.Goal
import com.moneymap.core.Plan
import com.moneymap.core.PlanEngine
import com.moneymap.core.PodBalance
import com.moneymap.core.PodMove
import com.moneymap.core.Pods
import com.moneymap.core.formatInr
import com.moneymap.core.long
import com.moneymap.core.monthYear
import com.moneymap.core.parseAmount
import com.moneymap.core.short
import com.moneymap.ui.theme.MoneyColors
import java.time.LocalDate

@Composable
fun SaveScreen(
    today: LocalDate,
    plan: PlanEngine,
    podMoves: List<PodMove>,
    goals: List<Goal>,
    onPodMove: (pod: String, amount: Long, note: String) -> Unit,
    onDeletePodMove: (Long) -> Unit,
    onSaveGoal: (Goal) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onApplyAutopilot: (List<Pair<String, Long>>) -> Unit = {},
) {
    val planPods = Pods.planPods(plan, Plan.cycleStartFor(today))
    val balances = Pods.balances(podMoves, planPods + goals.map { it.pod })
    var goalDialog by remember { mutableStateOf<Goal?>(null) }
    var newGoal by remember { mutableStateOf(false) }
    var deleteGoal by remember { mutableStateOf<Goal?>(null) }
    var moveDialog by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Goals", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { newGoal = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add goal", Modifier.padding(start = 4.dp))
                }
            }
        }
        if (goals.isEmpty()) {
            item {
                Text("Set a target on a pod, e.g. a trip or an emergency fund, and see how much to save each cycle.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(goals, key = { "goal-${it.id}" }) { g ->
            GoalCard(g, Pods.balanceOf(podMoves, g.pod), today, onEdit = { goalDialog = g }, onDelete = { deleteGoal = g })
        }
        if (goals.isNotEmpty()) {
            item { AutopilotCard(today, plan, podMoves, goals, onApplyAutopilot) }
        }
        item {
            Text("Pods", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Ticking the salary-day routine fills the pods with that cycle's split. Ticking a debt instalment or a " +
                    "pod → ${Plan.SPEND_MAIN} move takes the money out. Returns from people go into their return pod.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(balances, key = { "pod-${it.pod}" }) { p ->
            PodCard(p,
                onAdd = { moveDialog = p.pod to false },
                onWithdraw = { moveDialog = p.pod to true },
                onDeleteMove = onDeletePodMove)
        }
    }

    if (newGoal || goalDialog != null) {
        GoalDialog(
            existing = goalDialog,
            pods = balances.map { it.pod },
            onDismiss = { newGoal = false; goalDialog = null },
            onSave = { onSaveGoal(it); newGoal = false; goalDialog = null },
        )
    }
    deleteGoal?.let { g ->
        ConfirmDialog("Delete goal?", "${g.name} will be removed. The ${g.pod} balance stays.", "Delete",
            onConfirm = { onDeleteGoal(g.id) }, onDismiss = { deleteGoal = null })
    }
    moveDialog?.let { (pod, withdraw) ->
        PodMoveDialog(pod, withdraw, onDismiss = { moveDialog = null }) { amount, note ->
            onPodMove(pod, if (withdraw) -amount else amount, note)
            moveDialog = null
        }
    }
}

@Composable
private fun GoalCard(goal: Goal, saved: Long, today: LocalDate, onEdit: () -> Unit, onDelete: () -> Unit) {
    val p = Pods.goalProgress(goal, saved, today)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium)
                    Text(goal.pod + (goal.targetDate?.let { " · by ${it.long()}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit goal") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete goal") }
            }
            Text("${formatInr(saved)} of ${formatInr(goal.target)}", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { p.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(
                when {
                    p.reached -> "Goal reached"
                    p.perCycle != null && goal.targetDate != null && goal.targetDate.isBefore(today) ->
                        "Target date passed · ${formatInr(p.remaining)} still to go"
                    p.perCycle != null ->
                        "Save ${formatInr(p.perCycle)} per cycle for ${p.cyclesLeft} cycle${if (p.cyclesLeft == 1L) "" else "s"}"
                    else -> "${formatInr(p.remaining)} to go"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PodCard(p: PodBalance, onAdd: () -> Unit, onWithdraw: () -> Unit, onDeleteMove: (Long) -> Unit) {
    var open by rememberSaveable(p.pod) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.pod, style = MaterialTheme.typography.titleMedium)
                    Text(formatInr(p.balance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = if (p.balance < 0) MoneyColors.negative else MaterialTheme.colorScheme.onSurface)
                }
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Hide history" else "Show history")
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAdd) { Text("Add money") }
                OutlinedButton(onClick = onWithdraw) { Text("Withdraw") }
            }
            if (open) {
                if (p.moves.isEmpty()) {
                    Text("No movements yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                p.moves.take(20).forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.note.ifBlank { if (m.amount >= 0) "Added" else "Withdrawn" },
                                style = MaterialTheme.typography.bodyMedium)
                            Text(m.date.short() + if (m.linkKey != null) " · automatic" else "",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text((if (m.amount >= 0) "+" else "−") + formatInr(kotlin.math.abs(m.amount)),
                            color = if (m.amount >= 0) MoneyColors.positive else MoneyColors.negative)
                        if (m.linkKey == null) {
                            IconButton(onClick = { onDeleteMove(m.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete movement")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodMoveDialog(pod: String, withdraw: Boolean, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val parsed = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (withdraw) "Withdraw from $pod" else "Add money to $pod") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(amount, { amount = it })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (!withdraw) {
                    Text("Use this for money already in the pod or extra savings.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let { onConfirm(it, note) } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalDialog(existing: Goal?, pods: List<String>, onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var target by rememberSaveable { mutableStateOf(existing?.target?.let { formatInr(it, withSymbol = false) } ?: "") }
    var date by remember { mutableStateOf(existing?.targetDate) }
    var pod by rememberSaveable { mutableStateOf(existing?.pod ?: "") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val parsed = parseAmount(target)
    val podName = pod.trim().ifBlank { name.trim().takeIf { it.isNotEmpty() }?.let { "$it pod" } ?: "" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Goal") }, singleLine = true,
                    isError = showErrors && name.isBlank(), modifier = Modifier.fillMaxWidth())
                AmountField(target, { target = it }, label = "Target", isError = showErrors && parsed == null)
                DateField("By", date, { date = it }, clearable = true)
                Text("Saved in", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pods.forEach { p -> FilterChip(selected = pod == p, onClick = { pod = p }, label = { Text(p) }) }
                }
                OutlinedTextField(pod, { pod = it }, label = { Text("Pod (pick above or type a new one)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (date != null) {
                    Text("Target cycle: ${Plan.cycleStartFor(date!!).monthYear()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = parsed
                if (name.isBlank() || t == null || podName.isBlank()) showErrors = true
                else onSave(
                    (existing ?: Goal(name = "", target = 0, pod = "")).copy(
                        name = name.trim(), target = t, targetDate = date, pod = podName,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Suggests how to split this cycle's spare money (the emergency pod's share) across goals, and moves it on tap. */
@Composable
private fun AutopilotCard(
    today: LocalDate,
    plan: PlanEngine,
    podMoves: List<PodMove>,
    goals: List<Goal>,
    onApply: (List<Pair<String, Long>>) -> Unit,
) {
    val cycle = Plan.cycleStartFor(today)
    val spare = plan.budget(cycle).emergencyPod
    val progress = goals.map { Pods.goalProgress(it, Pods.balanceOf(podMoves, it.pod), today) }
    val autopilot = com.moneymap.core.GoalAutopilot.plan(progress, spare)
    val applied = podMoves.firstOrNull {
        it.note == AUTOPILOT_NOTE && !it.date.isBefore(cycle) && !it.date.isAfter(Plan.cycleEnd(cycle))
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Goal autopilot", style = MaterialTheme.typography.titleMedium)
            Text(
                if (spare <= 0) "Nothing spare this cycle: the plan leaves ${formatInr(spare)} for the ${Plan.EMERGENCY_POD.lowercase()}."
                else "This cycle ${formatInr(spare)} goes to the ${Plan.EMERGENCY_POD.lowercase()}. Suggested split:",
                style = MaterialTheme.typography.bodySmall,
            )
            autopilot.allocations.forEach { a ->
                Row {
                    Text(a.goal.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(formatInr(a.suggested) + (a.needed?.let { " of ${formatInr(it)} needed" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (autopilot.shortBy > 0) {
                Text("Short by ${formatInr(autopilot.shortBy)} to keep every dated goal on time.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (autopilot.total > 0) {
                Text("Keeps ${formatInr(autopilot.keep)} in the ${Plan.EMERGENCY_POD.lowercase()}.", style = MaterialTheme.typography.bodySmall)
                if (applied != null) {
                    Text("Already moved on ${applied.date.long()} this cycle.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                } else {
                    FilledTonalButton(onClick = {
                        onApply(autopilot.allocations.filter { it.suggested > 0 }.map { it.goal.pod to it.suggested })
                    }) { Text("Move ${formatInr(autopilot.total)} to goal pods") }
                }
            }
        }
    }
}

const val AUTOPILOT_NOTE = "Goal autopilot"
