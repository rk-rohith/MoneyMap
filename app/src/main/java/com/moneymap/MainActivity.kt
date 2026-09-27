package com.moneymap

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.moneymap.data.AppLock
import com.moneymap.ui.LockScreen
import com.moneymap.ui.MainViewModel
import com.moneymap.ui.MoneyMapRoot
import com.moneymap.ui.theme.MoneyMapTheme

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var locked by mutableStateOf(false)
    private var prompting = false
    /** Prompt automatically once per lock; after a cancel the user taps Unlock instead of being re-prompted. */
    private var autoPrompted = false

    /** Android 8.x: confirm with the system screen-lock page instead of the biometric library's dialog. */
    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        prompting = false
        if (result.resultCode == RESULT_OK) {
            locked = false
            AppLock.markLeft(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = AppLock.isEnabled(this) && (savedInstanceState == null || AppLock.shouldLockOnReturn(this))
        applyRecentsPrivacy()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MoneyMapTheme {
                if (locked) LockScreen(onUnlock = ::authenticate)
                else MoneyMapRoot(viewModel, onLockChanged = ::onLockSettingChanged)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!locked && AppLock.shouldLockOnReturn(this)) {
            locked = true
            autoPrompted = false
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
        if (locked && !autoPrompted) {
            autoPrompted = true
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.markLeft(this)
    }

    /** Hide app content in the recent-apps screen while the lock is on (Android 13+). */
    private fun applyRecentsPrivacy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!AppLock.isEnabled(this))
        }
    }

    /** Returns false when the phone has no screen lock to protect the app with. */
    private fun onLockSettingChanged(enabled: Boolean): Boolean {
        if (enabled && !canLock()) return false
        AppLock.setEnabled(this, enabled)
        AppLock.markLeft(this)
        applyRecentsPrivacy()
        return true
    }

    private fun authenticate() {
        if (prompting) return
        if (!canLock()) {
            // Screen lock was removed from the phone: nothing to check against, so don't trap the user.
            locked = false
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val keyguard = getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val confirm = keyguard?.createConfirmDeviceCredentialIntent("Unlock Money map", null)
            if (confirm == null) {
                locked = false
            } else {
                prompting = true
                confirmCredential.launch(confirm)
            }
            return
        }
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    locked = false
                    AppLock.markLeft(this@MainActivity)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Money map")
                .setSubtitle("Use your fingerprint, face or screen lock")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }

    private fun canLock(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        } else {
            BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
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
        if (intent.action == ACTION_QUICK_ADD || intent.getBooleanExtra(EXTRA_QUICK_ADD, false)) viewModel.showQuickAdd()
        if (intent.action == ACTION_ADD_ENTRY) viewModel.showAddEntry()
        if (intent.getBooleanExtra(EXTRA_TEST_NOTIFICATION, false)) viewModel.sendTestNotification()
    }

    companion object {
        const val EXTRA_OPEN_ENTRY = "open_entry"
        const val EXTRA_RECORD = "record"
        const val EXTRA_TAB = "tab"
        const val EXTRA_QUICK_ADD = "quick_add"
        const val ACTION_QUICK_ADD = "com.moneymap.action.QUICK_ADD"
        const val ACTION_ADD_ENTRY = "com.moneymap.action.ADD_ENTRY"
        /** adb shell am start -n com.moneymap/.MainActivity --ez test_notification true */
        const val EXTRA_TEST_NOTIFICATION = "test_notification"

        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
