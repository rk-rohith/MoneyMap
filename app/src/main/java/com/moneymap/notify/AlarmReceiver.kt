package com.moneymap.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.moneymap.MainActivity
import com.moneymap.MoneyMapApp
import com.moneymap.R
import com.moneymap.core.Direction
import com.moneymap.core.EntryStatus
import com.moneymap.core.ItemKind
import com.moneymap.core.Ledger
import com.moneymap.core.Plan
import com.moneymap.core.PlanItem
import com.moneymap.core.Reminder
import com.moneymap.core.ReminderPlanner
import com.moneymap.core.ReminderType
import com.moneymap.core.Slot
import com.moneymap.data.AutoBackup
import com.moneymap.data.MoneyRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MoneyMapApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                handle(app, app.repository, intent)
                runCatching { MoneyWidget.updateAll(app, app.repository) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, repo: MoneyRepository, intent: Intent) {
        val reminder = intent.toReminder()
        when (intent.action) {
            ACTION_FIRE -> if (reminder != null) fire(context, repo, reminder)
            ACTION_MARK_DONE -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                repo.setDone(itemId, true)
                Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            }
            ACTION_SNOOZE -> {
                if (reminder == null) return
                Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                val snoozed = reminder.copy(slot = if (reminder.type == ReminderType.PAYMENT) Slot.SNOOZE else reminder.slot)
                AlarmScheduler.schedule(context, snoozed, keyOverride = "snooze:${reminder.key}",
                    atMillis = System.currentTimeMillis() + SNOOZE_MILLIS)
            }
        }
    }

    private suspend fun fire(context: Context, repo: MoneyRepository, r: Reminder) {
        val today = LocalDate.now()
        when (r.type) {
            ReminderType.PAYMENT -> {
                val item = r.itemId?.let { repo.planNow().itemById(it) } ?: return
                if (repo.isDone(item.id)) return
                val summary = if (item.kind == ItemKind.REVIEW) Ledger.summary(repo.entriesNow()) else null
                val text = ReminderPlanner.paymentText(item, r.slot, summary)
                showPayment(context, item, r, text.title, text.text)
            }
            ReminderType.LEDGER_DUE -> {
                val person = r.person ?: return
                val direction = r.direction ?: return
                val due = r.dueDate ?: return
                val entries = repo.entriesNow()
                val outstanding = Ledger.outstandingFor(entries, person, direction)
                if (outstanding <= 0) return
                val entryId = entries.firstOrNull {
                    Ledger.personKey(it.entry.person) == Ledger.personKey(person) &&
                        it.entry.direction == direction && it.status != EntryStatus.SETTLED
                }?.entry?.id ?: return
                val text = ReminderPlanner.ledgerText(person, direction, outstanding, due, today)
                val notifId = "due:${Ledger.personKey(person)}:$direction".hashCode()
                val open = Notifications.openAppIntent(context, notifId) {
                    putExtra(MainActivity.EXTRA_OPEN_ENTRY, entryId)
                }
                val record = Notifications.openAppIntent(context, notifId + 1) {
                    putExtra(MainActivity.EXTRA_OPEN_ENTRY, entryId)
                    putExtra(MainActivity.EXTRA_RECORD, true)
                }
                val label = if (direction == Direction.LENT) "Record received" else "Record repayment"
                Notifications.show(context, notifId, Notifications.CHANNEL_PEOPLE, text.title, text.text, open,
                    listOf(
                        NotificationCompat.Action(R.drawable.ic_notification, label, record),
                        NotificationCompat.Action(R.drawable.ic_notification, "Snooze 1 hr",
                            actionIntent(context, ACTION_SNOOZE, r, notifId)),
                    ))
            }
            ReminderType.WEEKLY -> {
                AutoBackup.runIfDue(context, repo)
                val budget = repo.planNow().spendBudget(Plan.cycleStartFor(today))
                val text = ReminderPlanner.weeklyText(repo.spentInCycle(today), today, budget)
                val open = Notifications.openAppIntent(context, Notifications.ID_WEEKLY) {
                    putExtra(MainActivity.EXTRA_TAB, 1)
                }
                val quickAdd = Notifications.openAppIntent(context, Notifications.ID_WEEKLY + 1) {
                    putExtra(MainActivity.EXTRA_QUICK_ADD, true)
                }
                Notifications.show(context, Notifications.ID_WEEKLY, Notifications.CHANNEL_WEEKLY, text.title, text.text, open,
                    listOf(NotificationCompat.Action(R.drawable.ic_notification, "Log expense", quickAdd)))
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.moneymap.action.FIRE"
        const val ACTION_MARK_DONE = "com.moneymap.action.MARK_DONE"
        const val ACTION_SNOOZE = "com.moneymap.action.SNOOZE"

        private const val EXTRA_KEY = "key"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_SLOT = "slot"
        private const val EXTRA_AT = "at"
        const val EXTRA_ITEM_ID = "itemId"
        private const val EXTRA_PERSON = "person"
        private const val EXTRA_DIRECTION = "direction"
        private const val EXTRA_DUE = "due"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        private const val SNOOZE_MILLIS = 60 * 60 * 1000L

        private fun Intent.putReminder(r: Reminder): Intent = apply {
            putExtra(EXTRA_KEY, r.key)
            putExtra(EXTRA_TYPE, r.type.name)
            putExtra(EXTRA_SLOT, r.slot.name)
            putExtra(EXTRA_AT, r.at.toString())
            r.itemId?.let { putExtra(EXTRA_ITEM_ID, it) }
            r.person?.let { putExtra(EXTRA_PERSON, it) }
            r.direction?.let { putExtra(EXTRA_DIRECTION, it.name) }
            r.dueDate?.let { putExtra(EXTRA_DUE, it.toString()) }
        }

        fun Intent.toReminder(): Reminder? = runCatching {
            Reminder(
                key = getStringExtra(EXTRA_KEY) ?: return null,
                at = LocalDateTime.parse(getStringExtra(EXTRA_AT)),
                type = ReminderType.valueOf(getStringExtra(EXTRA_TYPE) ?: return null),
                slot = Slot.valueOf(getStringExtra(EXTRA_SLOT) ?: return null),
                itemId = getStringExtra(EXTRA_ITEM_ID),
                person = getStringExtra(EXTRA_PERSON),
                direction = getStringExtra(EXTRA_DIRECTION)?.let(Direction::valueOf),
                dueDate = getStringExtra(EXTRA_DUE)?.let(LocalDate::parse),
            )
        }.getOrNull()

        fun fireIntent(context: Context, r: Reminder, key: String): Intent =
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                data = AlarmScheduler.keyUri(key)
            }.putReminder(r)

        private fun actionIntent(context: Context, action: String, r: Reminder, notifId: Int): android.app.PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                this.action = action
                data = AlarmScheduler.keyUri("$action:${r.key}")
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
            }.putReminder(r)
            return android.app.PendingIntent.getBroadcast(context, "$action:${r.key}".hashCode(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        }

        fun showPayment(context: Context, item: PlanItem, r: Reminder, title: String, text: String) {
            val notifId = item.id.hashCode()
            val open = Notifications.openAppIntent(context, notifId) { putExtra(MainActivity.EXTRA_TAB, 0) }
            Notifications.show(context, notifId, Notifications.CHANNEL_PAYMENTS, title, text, open,
                listOf(
                    NotificationCompat.Action(R.drawable.ic_notification, "Mark done",
                        actionIntent(context, ACTION_MARK_DONE, r, notifId)),
                    NotificationCompat.Action(R.drawable.ic_notification, "Snooze 1 hr",
                        actionIntent(context, ACTION_SNOOZE, r, notifId)),
                ))
        }

        /** Posts a real payment notification (with working actions) for the next unpaid item. */
        suspend fun sendTest(context: Context, repo: MoneyRepository) {
            val today = LocalDate.now()
            val done = repo.doneNow()
            val item = repo.planNow().itemsBetween(maxOf(today, Plan.TRACK_START), today.plusDays(62))
                .firstOrNull { it.notifies && it.id !in done } ?: return
            val r = Reminder("test:${item.id}", LocalDateTime.now(), ReminderType.PAYMENT, Slot.MORNING, itemId = item.id)
            val text = ReminderPlanner.paymentText(item, Slot.MORNING,
                if (item.kind == ItemKind.REVIEW) Ledger.summary(repo.entriesNow()) else null)
            showPayment(context, item, r, "Test · ${text.title}", text.text)
        }
    }
}
