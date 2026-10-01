package com.moneymap.notify

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.moneymap.MainActivity
import com.moneymap.R

/** Launcher shortcuts that open "Add money" for one pod: long-press entries for the biggest pods, or pinned ones. */
object PodShortcuts {
    const val ACTION_POD = "com.moneymap.action.POD"
    const val EXTRA_POD = "pod"
    private const val PREFS = "pod_shortcuts"
    private const val KEY_PUBLISHED = "published"
    private const val DYNAMIC_COUNT = 2

    private fun info(context: Context, pod: String): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, "pod:$pod")
            .setShortLabel(if (pod.length <= 12) pod else pod.take(11) + "…")
            .setLongLabel("Add to $pod")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_pod))
            .setIntent(Intent(context, MainActivity::class.java).setAction(ACTION_POD).putExtra(EXTRA_POD, pod))
            .build()

    /** Keeps the long-press shortcuts on the largest pods. Only calls the launcher when the list changes. */
    fun publish(context: Context, podsByBalance: List<String>) {
        val top = podsByBalance.take(DYNAMIC_COUNT)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = top.joinToString("\u0001")
        if (prefs.getString(KEY_PUBLISHED, null) == key) return
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, top.map { info(context, it) })
            prefs.edit().putString(KEY_PUBLISHED, key).apply()
        }
    }

    /** Asks the launcher to pin a shortcut for [pod]. Returns false when the launcher can't. */
    fun requestPin(context: Context, pod: String): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context) &&
            runCatching { ShortcutManagerCompat.requestPinShortcut(context, info(context, pod), null) }.getOrDefault(false)
}
