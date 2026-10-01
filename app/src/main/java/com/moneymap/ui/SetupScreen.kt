package com.moneymap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymap.core.Profile
import com.moneymap.core.parseAmount

/** First run: the few facts the plan needs. Everything else can be added later in Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    start: Profile,
    snackbar: SnackbarHostState,
    onDone: (profile: Profile, salary: Long, spendBudget: Long) -> Unit,
    onRestore: () -> Unit,
) {
    var salaryAccount by rememberSaveable { mutableStateOf(start.salaryAccount) }
    var spendAccount by rememberSaveable { mutableStateOf(start.spendAccount) }
    var dayText by rememberSaveable { mutableStateOf(start.salaryDay.toString()) }
    var salaryText by rememberSaveable { mutableStateOf("") }
    var budgetText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(start.currencySymbol) }
    var indian by rememberSaveable { mutableStateOf(start.indianGrouping) }

    val day = dayText.toIntOrNull()?.takeIf { it in 1..28 }
    val salary = parseAmount(salaryText)
    val budget = parseAmount(budgetText)
    val valid = day != null && salary != null && budget != null && budget <= salary &&
        salaryAccount.isNotBlank() && spendAccount.isNotBlank()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Welcome to Money map") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("A few details to build your monthly plan. Everything stays on this phone, and you can change it later " +
                "in Settings.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(salaryAccount, { salaryAccount = it }, label = { Text("Account your salary arrives in") },
                supportingText = { Text("Bills and autopays are paid from here, e.g. your bank's name") },
                singleLine = true, isError = salaryAccount.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(spendAccount, { spendAccount = it }, label = { Text("Account you spend and save from") },
                supportingText = { Text("Holds your spending money and savings pods. Can be the same bank.") },
                singleLine = true, isError = spendAccount.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dayText, { v -> dayText = v.filter { it.isDigit() }.take(2) }, label = { Text("Salary day (1–28)") },
                supportingText = { Text("Each cycle runs from this day to the day before it next month.") },
                singleLine = true, isError = day == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(currency, { currency = it.take(4) }, label = { Text("Currency symbol") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Indian grouping (1,00,000)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = indian, onCheckedChange = { indian = it })
            }
            AmountField(salaryText, { salaryText = it }, label = "Monthly salary", isError = salaryText.isNotEmpty() && salary == null)
            AmountField(budgetText, { budgetText = it }, label = "Monthly spending budget",
                isError = budgetText.isNotEmpty() && (budget == null || (salary != null && budget > salary)))
            Text("Whatever is left after bills, savings and spending goes to the emergency pod. Add bills, SIPs, loans " +
                "and one-off expenses in Settings & plan.", style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = valid,
                onClick = {
                    onDone(
                        start.copy(salaryDay = day!!, salaryAccount = salaryAccount.trim(), spendAccount = spendAccount.trim(),
                            currencySymbol = currency.trim(), indianGrouping = indian),
                        salary!!, budget!!,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start") }
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Restore from a backup instead") }
        }
    }
}
