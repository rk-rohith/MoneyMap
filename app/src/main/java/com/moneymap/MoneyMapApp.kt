package com.moneymap

import android.app.Application
import com.moneymap.data.AutoBackup
import com.moneymap.data.MoneyDatabase
import com.moneymap.data.MoneyRepository
import com.moneymap.notify.AlarmScheduler
import com.moneymap.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class MoneyMapApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val rescheduleRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val repository: MoneyRepository by lazy {
        MoneyRepository(MoneyDatabase.create(this)) { rescheduleRequests.tryEmit(Unit) }
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch {
            rescheduleRequests.debounce(500).collect {
                runCatching { AlarmScheduler.rescheduleAll(this@MoneyMapApp, repository) }
                runCatching { repository.recordNetWorth() }
            }
        }
        appScope.launch {
            repository.init()
            // Receipt photos of expenses deleted earlier (kept until now so Undo could bring them back).
            runCatching { com.moneymap.data.Receipts.cleanUp(this@MoneyMapApp, repository.expenseIds()) }
            AlarmScheduler.rescheduleAll(this@MoneyMapApp, repository)
            AutoBackup.runIfDue(this@MoneyMapApp, repository)
        }
    }
}
