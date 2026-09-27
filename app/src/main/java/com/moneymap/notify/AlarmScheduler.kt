package com.moneymap.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.moneymap.core.Reminder
import com.moneymap.data.MoneyRepository
import com.moneymap.core.ReminderPlanner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

/** Schedules every reminder about 3 months ahead. Safe to call as often as data changes. */
object AlarmScheduler {
    private const val PREFS = "alarms"
    private const val KEY_SCHEDULED = "scheduled_keys"
    private val mutex = Mutex()

    suspend fun rescheduleAll(context: Context, repo: MoneyRepository) = mutex.withLock {
        val reminders = ReminderPlanner.plan(LocalDateTime.now(), repo.doneNow(), repo.entriesNow(), repo.planNow())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val newKeys = reminders.map { it.key }.toSet()
        (old - newKeys).forEach { cancel(context, it) }
        reminders.forEach { schedule(context, it) }
        prefs.edit().putStringSet(KEY_SCHEDULED, newKeys).apply()
    }

    fun schedule(context: Context, reminder: Reminder, keyOverride: String? = null, atMillis: Long? = null) {
        val key = keyOverride ?: reminder.key
        val trigger = atMillis ?: reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = AlarmReceiver.fireIntent(context, reminder, key)
        val pi = PendingIntent.getBroadcast(context, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(context, trigger, pi)
    }

    private fun setAlarm(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                return
            } catch (_: SecurityException) {
                // Fall through to inexact.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun cancel(context: Context, key: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = keyUri(key)
        }
        val pi = PendingIntent.getBroadcast(context, key.hashCode(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    fun keyUri(key: String): Uri = Uri.Builder().scheme("moneymap").authority("alarm").appendPath(key).build()
}
