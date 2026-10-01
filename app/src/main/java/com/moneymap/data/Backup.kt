package com.moneymap.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.moneymap.core.BackupCrypto
import com.moneymap.core.Direction
import com.moneymap.core.EntryWithTxns
import com.moneymap.core.ExpenseCategory
import com.moneymap.core.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Export to JSON (full backup, importable) + CSV (readable), and import from JSON. */
object Backup {
    private const val VERSION = 2

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", "MoneyMap")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("entries", JSONArray().apply {
            data.entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("person", e.person); put("direction", e.direction)
                    put("amount", e.amount); put("reason", e.reason)
                    put("date", LocalDate.ofEpochDay(e.dateEpochDay).toString())
                    put("dueDate", e.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: JSONObject.NULL)
                    put("returnPod", e.returnPod); put("notes", e.notes)
                    put("seedKey", e.seedKey ?: JSONObject.NULL)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                })
            }
        })
        root.put("transactions", JSONArray().apply {
            data.txns.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("entryId", t.entryId); put("type", t.type); put("amount", t.amount)
                    put("date", LocalDate.ofEpochDay(t.dateEpochDay).toString()); put("note", t.note)
                    put("linkKey", t.linkKey ?: JSONObject.NULL)
                })
            }
        })
        root.put("expenses", JSONArray().apply {
            data.expenses.forEach { x ->
                put(JSONObject().apply {
                    put("id", x.id); put("amount", x.amount); put("note", x.note)
                    put("date", LocalDate.ofEpochDay(x.dateEpochDay).toString()); put("createdAt", x.createdAt)
                    put("category", x.category)
                })
            }
        })
        root.put("done", JSONArray().apply {
            data.done.forEach { d -> put(JSONObject().apply { put("itemId", d.itemId); put("doneAt", d.doneAt) }) }
        })
        root.put("podMoves", JSONArray().apply {
            data.podMoves.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("pod", m.pod); put("amount", m.amount)
                    put("date", LocalDate.ofEpochDay(m.dateEpochDay).toString()); put("note", m.note)
                    put("linkKey", m.linkKey ?: JSONObject.NULL)
                })
            }
        })
        root.put("goals", JSONArray().apply {
            data.goals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id); put("name", g.name); put("target", g.target)
                    put("targetDate", g.targetEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: JSONObject.NULL)
                    put("pod", g.pod); put("createdAt", g.createdAt)
                })
            }
        })
        data.plan?.let { root.put("plan", JSONObject(it)) }
        data.reminders?.let { root.put("reminders", JSONObject(it)) }
        data.profile?.let { root.put("profile", JSONObject(it)) }
        data.categoryBudgets?.let { root.put("categoryBudgets", JSONObject(it)) }
        if (data.extras.isNotEmpty()) root.put("extras", JSONObject().apply { data.extras.forEach { (k, v) -> put(k, v) } })
        return root.toString(2)
    }

    fun fromJson(json: String): BackupData {
        val root = JSONObject(json)
        require(root.optString("app") == "MoneyMap") { "Not a Money map backup" }
        fun JSONObject.strOrNull(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
        fun day(s: String) = LocalDate.parse(s).toEpochDay()

        val entries = root.getJSONArray("entries").objects().map { o ->
            val direction = o.getString("direction").also { Direction.valueOf(it) }
            EntryEntity(
                id = o.getLong("id"), person = o.getString("person"), direction = direction,
                amount = o.getLong("amount"), reason = o.optString("reason"), dateEpochDay = day(o.getString("date")),
                dueEpochDay = o.strOrNull("dueDate")?.let(::day), returnPod = o.optString("returnPod", "JUPITER_MAIN"),
                notes = o.optString("notes"), seedKey = o.strOrNull("seedKey"),
                createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
            )
        }
        val txns = root.getJSONArray("transactions").objects().map { o ->
            TxnEntity(
                id = o.getLong("id"), entryId = o.getLong("entryId"),
                type = o.getString("type").also { TxnType.valueOf(it) }, amount = o.getLong("amount"),
                dateEpochDay = day(o.getString("date")), note = o.optString("note"), linkKey = o.strOrNull("linkKey"),
            )
        }
        val expenses = root.optJSONArray("expenses")?.objects().orEmpty().map { o ->
            ExpenseEntity(id = o.getLong("id"), amount = o.getLong("amount"), note = o.optString("note"),
                dateEpochDay = day(o.getString("date")), createdAt = o.optLong("createdAt"),
                category = ExpenseCategory.parse(o.strOrNull("category")).name)
        }
        val done = root.optJSONArray("done")?.objects().orEmpty().map { o ->
            DoneEntity(o.getString("itemId"), o.optLong("doneAt"))
        }
        val podMoves = root.optJSONArray("podMoves")?.objects().orEmpty().map { o ->
            PodMoveEntity(id = o.getLong("id"), pod = o.getString("pod"), amount = o.getLong("amount"),
                dateEpochDay = day(o.getString("date")), note = o.optString("note"), linkKey = o.strOrNull("linkKey"))
        }
        val goals = root.optJSONArray("goals")?.objects().orEmpty().map { o ->
            GoalEntity(id = o.getLong("id"), name = o.getString("name"), target = o.getLong("target"),
                targetEpochDay = o.strOrNull("targetDate")?.let(::day), pod = o.getString("pod"),
                createdAt = o.optLong("createdAt"))
        }
        // Validate the plan before accepting it; older backups have none and keep the current plan.
        val plan = root.optJSONObject("plan")?.let { PlanJson.encode(PlanJson.fromJson(it)) }
        val reminders = root.optJSONObject("reminders")?.let { ReminderPrefsJson.encode(ReminderPrefsJson.decode(it.toString())) }
        val profile = root.optJSONObject("profile")?.let { ProfileJson.decode(it.toString())?.let(ProfileJson::encode) }
        val categoryBudgets = root.optJSONObject("categoryBudgets")?.let {
            CategoryBudgetsJson.encode(CategoryBudgetsJson.decode(it.toString()))
        }
        val extras = root.optJSONObject("extras")?.let { o ->
            WealthJson.BACKUP_KEYS.filter { o.has(it) && !o.isNull(it) }.associateWith { o.getString(it) }
        }.orEmpty()
        return BackupData(entries, txns, expenses, done, podMoves, goals, plan, reminders, profile, categoryBudgets, extras)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun csv(vararg cells: Any?): String = cells.joinToString(",") { c ->
        val s = c?.toString() ?: ""
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun ledgerCsv(entries: List<EntryWithTxns>): String = buildString {
        appendLine(csv("entry_id", "person", "direction", "amount", "outstanding", "status", "reason", "date",
            "due_date", "return_pod", "txn_type", "txn_amount", "txn_date", "txn_note"))
        entries.forEach { e ->
            e.timeline.forEach { t ->
                appendLine(csv(e.entry.id, e.entry.person, e.entry.direction, e.entry.amount, e.outstanding,
                    e.status.label, e.entry.reason, e.entry.date, e.entry.dueDate, e.entry.returnPod.label,
                    t.type, t.amount, t.date, t.note))
            }
        }
    }

    fun expensesCsv(expenses: List<Expense>): String = buildString {
        appendLine(csv("id", "date", "amount", "category", "note"))
        expenses.forEach { appendLine(csv(it.id, it.date, it.amount, it.category.label, it.note)) }
    }

    /** Encrypted backups use their own extension so they aren't mistaken for readable JSON. */
    const val ENCRYPTED_EXTENSION = "mmbackup"

    fun fileName(now: java.time.LocalDateTime = java.time.LocalDateTime.now(), encrypted: Boolean = false): String =
        "moneymap-backup-${now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}." +
            if (encrypted) ENCRYPTED_EXTENSION else "json"

    /** The full backup as file text: encrypted when a backup password is set. */
    suspend fun backupText(context: Context, repo: MoneyRepository): String {
        val json = toJson(repo.snapshot())
        if (!BackupPassword.isSet(context)) return json
        val password = BackupPassword.get(context) ?: error("Backup password is unreadable; set it again in Settings")
        return BackupCrypto.encrypt(json, password.toCharArray())
    }

    /**
     * Writes the export files to cache and returns shareable content URIs. With a backup password the readable
     * CSVs are left out, since they would undo the protection.
     */
    suspend fun export(context: Context, repo: MoneyRepository): List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = LocalDate.now().toString()
        val encrypted = BackupPassword.isSet(context)
        val files = if (encrypted) {
            listOf(File(dir, "moneymap-backup-$stamp.$ENCRYPTED_EXTENSION").apply { writeText(backupText(context, repo)) })
        } else {
            val data = repo.snapshot()
            val entries = repo.entriesNow()
            val expenses = data.expenses.map { it.toDomain() }
            listOf(
                File(dir, "moneymap-backup-$stamp.json").apply { writeText(toJson(data)) },
                File(dir, "moneymap-ledger-$stamp.csv").apply { writeText(ledgerCsv(entries)) },
                File(dir, "moneymap-expenses-$stamp.csv").apply { writeText(expensesCsv(expenses)) },
            )
        }
        val authority = "${context.packageName}.files"
        files.map { FileProvider.getUriForFile(context, authority, it) }
    }

    /** The file is encrypted and neither [password] nor the saved backup password opens it. */
    class PasswordNeededException(val wrongPassword: Boolean) :
        Exception(if (wrongPassword) "Wrong backup password" else "This backup is password-protected")

    /** Turns backup file text into JSON, decrypting with [password] or the saved backup password. */
    fun decode(context: Context, text: String, password: String?): String {
        if (!BackupCrypto.isEncrypted(text)) return text
        val candidate = password ?: BackupPassword.get(context) ?: throw PasswordNeededException(wrongPassword = false)
        return try {
            BackupCrypto.decrypt(text, candidate.toCharArray())
        } catch (_: BackupCrypto.WrongPasswordException) {
            throw PasswordNeededException(wrongPassword = password != null)
        }
    }

    suspend fun importFrom(context: Context, repo: MoneyRepository, uri: Uri, password: String? = null): BackupData =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Could not read file")
            val data = fromJson(decode(context, text, password))
            repo.replaceAll(data)
            data
        }
}
