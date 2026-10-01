package com.moneymap.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneymap.MoneyMapApp
import com.moneymap.core.TxnParser
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
        val suggestion = TxnParser.parse(title, text, at, sbn.packageName) ?: return
        val app = application as MoneyMapApp
        app.appScope.launch { runCatching { app.repository.addSuggestion(suggestion) } }
    }
}
