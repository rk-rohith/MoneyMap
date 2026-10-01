package com.moneymap.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as v1 of the app left it, then opens it with the current Room schema.
 * Room checks every table against the entities on open, so a broken migration fails here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun createV1() {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO entries (person, direction, amount, reason, dateEpochDay, dueEpochDay, returnPod, notes, " +
                    "seedKey, createdAt, updatedAt) VALUES ('Friend', 'LENT', 5000, 'Trip', 20723, NULL, 'JUPITER_MAIN', '', NULL, 1, 1)"
            )
            db.execSQL("INSERT INTO txns (entryId, type, amount, dateEpochDay, note, linkKey) VALUES (1, 'OPENING', 5000, 20723, '', NULL)")
            db.execSQL("INSERT INTO expenses (amount, note, dateEpochDay, createdAt) VALUES (250, 'Lunch', 20725, 1)")
            db.execSQL("INSERT INTO done_items (itemId, doneAt) VALUES ('2026-10-17:car-emi', 1)")
            db.version = 1
        }
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun migrate1To2KeepsData() = runTest {
        val db = Room.databaseBuilder(context, MoneyDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.dao()
            val entry = dao.entries().single()
            assertEquals("Friend", entry.entry.person)
            assertEquals(1, entry.txns.size)
            val expense = dao.expenses().single()
            assertEquals("Lunch", expense.note)
            assertEquals("OTHER", expense.category)
            assertEquals(listOf("2026-10-17:car-emi"), dao.done().map { it.itemId })
            assertEquals(null, dao.setting("anything"))
        } finally {
            db.close()
        }
    }

    private companion object {
        /** The v1 tables (commit 05cb5cb), as Room created them. */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, " +
                "`direction` TEXT NOT NULL, `amount` INTEGER NOT NULL, `reason` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, " +
                "`dueEpochDay` INTEGER, `returnPod` TEXT NOT NULL, `notes` TEXT NOT NULL, `seedKey` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `txns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, `linkKey` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_txns_entryId` ON `txns` (`entryId`)",
            "CREATE INDEX IF NOT EXISTS `index_txns_linkKey` ON `txns` (`linkKey`)",
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `done_items` (`itemId` TEXT NOT NULL, `doneAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))",
        )
    }
}
