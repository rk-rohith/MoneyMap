package com.moneymap

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.moneymap.ui.MainViewModel
import com.moneymap.ui.MoneyMapRoot
import com.moneymap.ui.theme.MoneyMapTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MoneyMapTheme {
                MoneyMapRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val entryId = intent.getLongExtra(EXTRA_OPEN_ENTRY, -1L)
        val tab = intent.getIntExtra(EXTRA_TAB, -1)
        viewModel.openFromIntent(
            entryId = entryId.takeIf { it > 0 },
            record = intent.getBooleanExtra(EXTRA_RECORD, false),
            tab = tab.takeIf { it >= 0 },
        )
        if (intent.getBooleanExtra(EXTRA_TEST_NOTIFICATION, false)) viewModel.sendTestNotification()
    }

    companion object {
        const val EXTRA_OPEN_ENTRY = "open_entry"
        const val EXTRA_RECORD = "record"
        const val EXTRA_TAB = "tab"
        /** adb shell am start -n com.moneymap/.MainActivity --ez test_notification true */
        const val EXTRA_TEST_NOTIFICATION = "test_notification"
    }
}
