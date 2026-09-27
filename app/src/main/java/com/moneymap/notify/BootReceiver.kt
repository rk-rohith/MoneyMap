package com.moneymap.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moneymap.MoneyMapApp
import kotlinx.coroutines.launch

/** Re-creates alarms after reboot, app update, or clock/time-zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        val app = context.applicationContext as MoneyMapApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                AlarmScheduler.rescheduleAll(app, app.repository)
            } finally {
                pending.finish()
            }
        }
    }
}
