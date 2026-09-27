package com.moneymap.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moneymap.MainActivity
import com.moneymap.R

object Notifications {
    const val CHANNEL_PAYMENTS = "payments"
    const val CHANNEL_PEOPLE = "people"
    const val CHANNEL_WEEKLY = "weekly"

    const val ID_WEEKLY = 1001
    const val ID_TEST = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PAYMENTS, "Payments & tasks", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Bills, autopays, salary-day steps and the monthly review"
                },
                NotificationChannel(CHANNEL_PEOPLE, "Money lent & borrowed", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Due dates for money others owe you and money you owe"
                },
                NotificationChannel(CHANNEL_WEEKLY, "Weekly spending", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Sunday summary of what is left to spend this cycle"
                },
            )
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context, requestCode: Int, configure: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            configure()
        }
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        contentIntent: PendingIntent,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.launcher_background))
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull() ?: text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel == CHANNEL_WEEKLY) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        actions.forEach { builder.addAction(it) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
