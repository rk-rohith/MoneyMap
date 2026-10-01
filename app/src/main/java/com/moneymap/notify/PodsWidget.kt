package com.moneymap.notify

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.moneymap.MainActivity
import com.moneymap.MoneyMapApp
import com.moneymap.R
import com.moneymap.core.Plan
import com.moneymap.core.Pods
import com.moneymap.core.formatInr
import com.moneymap.data.MoneyRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen widget with savings pod balances. Tapping it opens the Save tab. */
class PodsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as MoneyMapApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                update(app, app.repository)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val MAX_ROWS = 5

        suspend fun update(context: Context, repo: MoneyRepository) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, PodsWidget::class.java))
            if (ids.isEmpty()) return
            val engine = repo.planNow()
            val extra = Pods.planPods(engine, Plan.cycleStartFor(LocalDate.now())) + repo.goalsNow().map { it.pod }
            val balances = Pods.balances(repo.podMovesNow(), extra)
                .filter { it.balance != 0L || it.pod == Plan.EMERGENCY_POD }
                .sortedByDescending { it.balance }
            val shown = balances.take(MAX_ROWS)
            val views = RemoteViews(context.packageName, R.layout.widget_pods).apply {
                setTextViewText(R.id.pods_names, shown.joinToString("\n") { it.pod }.ifEmpty { "No pods yet" })
                setTextViewText(R.id.pods_amounts, shown.joinToString("\n") { formatInr(it.balance) })
                setTextViewText(R.id.pods_total, "Total ${formatInr(balances.sumOf { it.balance })}" +
                    if (balances.size > MAX_ROWS) " · ${balances.size - MAX_ROWS} more" else "")
                setOnClickPendingIntent(R.id.pods_root, MoneyWidget.openApp(context, 2003) {
                    putExtra(MainActivity.EXTRA_TAB, 3)
                })
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
