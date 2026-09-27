package com.moneymap.data

import android.content.Context

/** Per-device display preferences (not included in backups). */
object UiPrefs {
    private const val PREFS = "ui"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    fun dynamicColor(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
}
