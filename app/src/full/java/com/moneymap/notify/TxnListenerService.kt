package com.moneymap.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneymap.MoneyMapApp
import com.moneymap.core.TxnParser
import com.moneymap.core.CardBillParser
import com.moneymap.core.formatInr
import com.moneymap.core.long
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads bank, card and UPI notifications (only after the person turns on notification access for Money map)
 * and keeps debits as suggested expenses on the Spend screen. Nothing leaves the phone and nothing is logged
 * until the person taps Add.
 */
class TxnListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString() ?: return
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(sbn.postTime), ZoneId.systemDefault())
        val app = application as MoneyMapApp
        // Card statements become "Pay card bill" items on Month, with reminders before the due date.
        CardBillParser.parse(title, text, at.toLocalDate())?.let { bill ->
            app.appScope.launch {
                runCatching {
                    if (app.repository.addCardBill(bill)) {
                        val id = "card:${bill.id}".hashCode()
                        Notifications.show(app, id, Notifications.CHANNEL_PAYMENTS,
                            "Card bill added: ${bill.card}",
                            "${formatInr(bill.amount)} due ${bill.dueDate.long()}. You'll get reminders before it's due.",
                            Notifications.openAppIntent(app, id) { putExtra(com.moneymap.MainActivity.EXTRA_TAB, 0) })
                    }
                }
            }
            return
        }
        val suggestion = TxnParser.parse(title, text, at, sbn.packageName) ?: return
        app.appScope.launch { runCatching { app.repository.addSuggestion(suggestion) } }
    }
}
