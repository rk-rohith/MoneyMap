package com.moneymap.notify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moneymap.MainActivity
import com.moneymap.MoneyMapApp
import com.moneymap.R
import com.moneymap.core.Summary
import com.moneymap.core.formatInr
import com.moneymap.data.MoneyRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen widget: next payment, what's left to spend, and what others owe. */
class MoneyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as MoneyMapApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                updateAll(app, app.repository)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun updateAll(context: Context, repo: MoneyRepository) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, MoneyWidget::class.java))
            if (ids.isEmpty()) return
            val today = LocalDate.now()
            val s = Summary.widget(today, repo.planNow(), repo.doneNow(), repo.entriesNow(), repo.spentInCycle(today))

            val views = RemoteViews(context.packageName, R.layout.widget_money).apply {
                setTextViewText(R.id.widget_next_title, s.nextTitle)
                setTextViewText(R.id.widget_next_detail, s.nextDetail)
                setTextViewText(R.id.widget_left, formatInr(s.leftToSpend))
                setTextViewText(R.id.widget_left_detail,
                    if (s.leftToSpend > 0) "${formatInr(s.perDay)}/day left" else "over budget")
                setTextViewText(R.id.widget_owed, formatInr(s.othersOweMe))
                setOnClickPendingIntent(R.id.widget_root, openApp(context, 2001) {})
                setOnClickPendingIntent(R.id.widget_add, openApp(context, 2002) {
                    putExtra(MainActivity.EXTRA_QUICK_ADD, true)
                })
            }
            manager.updateAppWidget(ids, views)
        }

        private fun openApp(context: Context, requestCode: Int, configure: Intent.() -> Unit): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                configure()
            }
            return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
