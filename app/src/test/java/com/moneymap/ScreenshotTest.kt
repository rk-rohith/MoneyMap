package com.moneymap

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.moneymap.core.DefaultPlan
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.Goal
import com.moneymap.core.ItemKind
import com.moneymap.core.LedgerTxn
import com.moneymap.core.OneOff
import com.moneymap.core.PlanEngine
import com.moneymap.core.Pods
import com.moneymap.core.SeedData
import com.moneymap.core.TxnType
import com.moneymap.core.openingType
import com.moneymap.data.Expense
import com.moneymap.ui.BackupState
import com.moneymap.ui.EntryDetailScreen
import com.moneymap.ui.MonthScreen
import com.moneymap.ui.PeopleScreen
import com.moneymap.ui.SaveScreen
import com.moneymap.ui.SearchScreen
import com.moneymap.ui.SettingsScreen
import com.moneymap.ui.SpendScreen
import com.moneymap.ui.theme.MoneyMapTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders each screen with sample data to PNGs in app/build/screenshots so changes can be
 * previewed without installing. Run: ./gradlew testDebugUnitTest -Pscreenshots
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h1500dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 20)
    private val plan = DefaultPlan.engine

    /** Seed data with ids, plus one partial return, as the app would show it. */
    private val entries: List<EntryWithTxns> = run {
        var txnId = 1L
        SeedData.entries.mapIndexed { i, seed ->
            val id = i + 1L
            val e = seed.entry.copy(id = id, createdAt = 1, updatedAt = 1)
            val txns = mutableListOf(LedgerTxn(txnId++, id, e.direction.openingType(), e.amount, e.date, e.reason))
            seed.settlements.forEach { txns += it.copy(id = txnId++, entryId = id) }
            if (e.seedKey == "friend-loan-emi") {
                txns += LedgerTxn(txnId++, id, TxnType.RECEIVED, 4_282, LocalDate.of(2026, 10, 12), "Part payment")
            }
            EntryWithTxns(e, txns)
        }
    }

    private val expenses = listOf(
        Expense(1, 1_850, "Dinner", today.minusDays(1), 0, ExpenseCategory.FOOD),
        Expense(2, 2_400, "Groceries", today.minusDays(3), 0, ExpenseCategory.GROCERIES),
        Expense(3, 1_500, "Petrol", today.minusDays(5), 0, ExpenseCategory.TRANSPORT),
        Expense(4, 499, "Recharge", today.minusDays(9), 0, ExpenseCategory.BILLS),
        Expense(5, 1_450, "Movie", today.minusDays(12), 0, ExpenseCategory.FUN),
    )

    private val done = setOf("2026-10-01:move-sip-jupiter", "2026-10-01:sip-jupiter", "2026-10-01:sip-hdfc",
        "2026-10-03:term", "2026-10-07:rent-out", "2026-10-17:car-emi")

    private val podMoves = run {
        val sep = LocalDate.of(2026, 9, 25)
        val salaryDay = plan.items(sep).first { it.kind == ItemKind.SALARY_DAY }
        val move = plan.items(sep).first { it.kind == ItemKind.TRANSFER }
        (Pods.movesForTick(salaryDay, plan.budget(sep)) + Pods.movesForTick(move, plan.budget(sep)))
            .mapIndexed { i, m -> m.copy(id = i + 1L) } +
            Pods.moveForReturn(com.moneymap.core.ReturnPod.SRI_LANKA, 4_282, LocalDate.of(2026, 10, 12), "Friend", 99)!!
                .copy(id = 50)
    }

    private val goals = listOf(
        Goal(1, "Sri Lanka trip", 60_000, LocalDate.of(2026, 12, 10), "Sri Lanka pod", 1),
        Goal(2, "Emergency fund", 3_00_000, LocalDate.of(2027, 9, 24), "Emergency pod", 2),
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { MoneyMapTheme { content() } }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun month() = shot("1-month") {
        MonthScreen(today = today, plan = plan, entries = entries, done = done, onToggle = { _, _ -> }, onOpenEntry = {})
    }

    @Test
    fun monthDark() {
        RuntimeEnvironment.setQualifiers("+night")
        shot("1-month-dark") {
            MonthScreen(today = today, plan = plan, entries = entries, done = done, onToggle = { _, _ -> }, onOpenEntry = {})
        }
    }

    @Test
    fun spend() = shot("2-spend") {
        SpendScreen(today = today, plan = plan, expenses = expenses, people = listOf("Mom", "Friend"),
            onAdd = { _, _, _ -> }, onPaidForSomeone = { _, _, _ -> }, onDelete = {})
    }

    @Test
    fun people() = shot("3-people") {
        PeopleScreen(entries = entries, today = today, onOpen = {}, onAdd = {}, onEdit = {}, onDelete = {},
            onRecord = { _, _, _, _ -> })
    }

    @Test
    fun entryDetail() = shot("4-entry-detail") {
        EntryDetailScreen(data = entries.first { it.entry.seedKey == "friend-loan-emi" }, today = today,
            snackbar = SnackbarHostState(), startRecording = false, onRecordingShown = {}, onBack = {}, onEdit = {},
            onDelete = {}, onRecord = { _, _, _ -> }, onUndo = {})
    }

    @Test
    fun save() = shot("5-save") {
        SaveScreen(today = today, plan = plan, podMoves = podMoves, goals = goals, onPodMove = { _, _, _ -> },
            onDeletePodMove = {}, onSaveGoal = {}, onDeleteGoal = {})
    }

    @Test
    fun search() = shot("7-search") {
        SearchScreen(entries = entries, expenses = expenses, podMoves = podMoves, onOpenEntry = {}, onBack = {},
            initialQuery = "emi")
    }

    @Test
    fun settings() = shot("6-settings") {
        val withOneOff = PlanEngine(DefaultPlan.settings.copy(oneOffs = listOf(
            OneOff("o1", "Car insurance", 18_000, LocalDate.of(2027, 3, 10), 3, LocalDate.of(2026, 10, 20)),
        )))
        SettingsScreen(today = today, plan = withOneOff, backup = BackupState("Documents/MoneyMap", 0),
            snackbar = SnackbarHostState(), onBack = {}, onSavePlan = {}, onChooseFolder = {}, onBackupNow = {},
            onBackupOff = {}, onExport = {}, onImport = {}, onTestNotification = {})
    }
}
