package com.moneymap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val json = """{"app":"MoneyMap","entries":[{"person":"Friend","amount":5000,"note":"₹ and ünïcode"}]}"""

    @Test
    fun roundTrip() {
        val enc = BackupCrypto.encrypt(json, "correct horse".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(json))
        assertFalse(enc.contains("Friend"))
        assertEquals(json, BackupCrypto.decrypt(enc, "correct horse".toCharArray()))
    }

    @Test
    fun saltMakesEveryFileDifferent() {
        val a = BackupCrypto.encrypt(json, "pw".toCharArray())
        val b = BackupCrypto.encrypt(json, "pw".toCharArray())
        assertNotEquals(a, b)
    }

    @Test
    fun wrongPasswordOrTamperingIsRejected() {
        val enc = BackupCrypto.encrypt(json, "right".toCharArray())
        assertThrows(BackupCrypto.WrongPasswordException::class.java) { BackupCrypto.decrypt(enc, "wrong".toCharArray()) }
        val lines = enc.trim().lines().toMutableList()
        val data = lines[4].toCharArray()
        data[10] = if (data[10] == 'A') 'B' else 'A'
        lines[4] = String(data)
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(lines.joinToString("\n"), "right".toCharArray())
        }
    }

    @Test
    fun survivesWindowsLineEndingsAndPadding() {
        val enc = BackupCrypto.encrypt(json, "pw".toCharArray()).replace("\n", "\r\n")
        assertEquals(json, BackupCrypto.decrypt("\n  $enc  \n", "pw".toCharArray()))
    }
}
