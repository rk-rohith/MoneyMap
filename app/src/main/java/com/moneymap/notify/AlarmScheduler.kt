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
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the next [ReminderPlanner.ALARM_WINDOW_DAYS] of reminders (capped at [ReminderPlanner.MAX_ALARMS]),
 * plus a daily refresh alarm that moves the window forward. Safe to call as often as data changes.
 */
object AlarmScheduler {
    private const val PREFS = "alarms"
    private const val KEY_SCHEDULED = "scheduled_keys"
    private const val REFRESH_KEY = "refresh"
    private val REFRESH_TIME: LocalTime = LocalTime.of(3, 15)
    private val mutex = Mutex()

    suspend fun rescheduleAll(context: Context, repo: MoneyRepository) = mutex.withLock {
        val now = LocalDateTime.now()
        val planned = ReminderPlanner.plan(now, repo.doneNow(), repo.entriesNow(), repo.planNow(), repo.reminderPrefsNow())
        val reminders = ReminderPlanner.alarmWindow(planned, now)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val newKeys = reminders.map { it.key }.toSet()
        (old - newKeys).forEach { cancel(context, it) }
        // One failed alarm must not stop the rest; an older alarm for the same key stays tracked.
        val scheduled = reminders.filter { r -> runCatching { schedule(context, r) }.isSuccess }.map { it.key }
        prefs.edit().putStringSet(KEY_SCHEDULED, scheduled.toSet() + (old intersect newKeys)).apply()
        scheduleRefresh(context, now)
        MoneyWidget.updateAll(context, repo)
    }

    fun schedule(context: Context, reminder: Reminder, keyOverride: String? = null, atMillis: Long? = null) {
        val key = keyOverride ?: reminder.key
        val trigger = atMillis ?: reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = AlarmReceiver.fireIntent(context, reminder, key)
        val pi = PendingIntent.getBroadcast(context, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(context, trigger, pi)
    }

    /** Inexact daily wake-up (early morning) that re-plans, so the alarm window never runs dry. */
    private fun scheduleRefresh(context: Context, now: LocalDateTime) {
        val today = now.toLocalDate().atTime(REFRESH_TIME)
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_REFRESH
            data = keyUri(REFRESH_KEY)
        }
        val pi = PendingIntent.getBroadcast(context, REFRESH_KEY.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
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
