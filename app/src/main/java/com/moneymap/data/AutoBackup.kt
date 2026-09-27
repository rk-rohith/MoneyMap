package com.moneymap.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Weekly backup of the full JSON export into a folder the user picked (Storage Access Framework).
 * Runs from the Sunday reminder and when the app starts, if the last backup is a week old.
 */
object AutoBackup {
    private const val PREFS = "auto_backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_LAST = "last_at"
    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun lastBackupAt(context: Context): Long = prefs(context).getLong(KEY_LAST, 0)

    /** Human-readable folder name, e.g. "Documents/MoneyMap". */
    fun folderLabel(uri: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { "Selected folder" } }
            .getOrDefault("Selected folder")

    fun setFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        folder(context)?.takeIf { it != uri }?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs(context).edit().putString(KEY_FOLDER, uri.toString()).apply()
    }

    fun turnOff(context: Context) {
        folder(context)?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs(context).edit().remove(KEY_FOLDER).apply()
    }

    /** Writes a backup file now. Returns the file name. */
    suspend fun runNow(context: Context, repo: MoneyRepository): String = withContext(Dispatchers.IO) {
        val tree = folder(context) ?: error("Choose a backup folder first")
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = Backup.fileName()
        val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: error("Could not create a file in the backup folder")
        val json = Backup.toJson(repo.snapshot())
        resolver.openOutputStream(doc, "w")?.use { it.write(json.toByteArray()) }
            ?: error("Could not write to the backup folder")
        prefs(context).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
        name
    }

    /** Backs up if a folder is set and the last backup is at least a week old. Never throws. */
    suspend fun runIfDue(context: Context, repo: MoneyRepository) {
        if (folder(context) == null) return
        if (System.currentTimeMillis() - lastBackupAt(context) < WEEK_MILLIS) return
        runCatching { runNow(context, repo) }
    }
}
