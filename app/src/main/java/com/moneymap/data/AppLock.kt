package com.moneymap.data

import android.content.Context

/** App-lock setting and the "was the app away long enough to lock again?" rule. */
object AppLock {
    private const val PREFS = "app_lock"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LEFT_AT = "left_at"
    /** Coming back within this window (e.g. from a file picker or share sheet) doesn't lock again. */
    const val GRACE_MILLIS = 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun markLeft(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LEFT_AT, now).apply()
    }

    fun shouldLockOnReturn(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        isEnabled(context) && now - prefs(context).getLong(KEY_LEFT_AT, 0) > GRACE_MILLIS
}
