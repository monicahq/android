package com.monicahq.android.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.auth.KeystoreTokenCipher
import com.monicahq.android.vault.Vault
import com.monicahq.android.vault.VaultColour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The real copy: SQLCipher, the Keystore and the files (spec 004, FR-001, FR-008–FR-015). */
@RunWith(AndroidJUnit4::class)
class OfflineDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.noBackupFilesDir, "offline-test-${UUID.randomUUID()}")
    private val cipher = KeystoreTokenCipher("monica_offline_test")
    private val copy = RoomOfflineCopy(context, directory, cipher, Dispatchers.IO)

    private val jane = Owner("https://monica.test", "7")
    private val now = 1_800_000_000_000L

    private val personal =
        Vault("v1", "Personal secret name", "A very private description", VaultColour.Blue, isDefault = true, contactCount = 3)
    private val family = Vault("v2", "Family", null, VaultColour.Green, isDefault = false, contactCount = 1)
    private val work = Vault("v3", "Work", null, VaultColour.Amber, isDefault = false, contactCount = 0)

    @After
    fun tearDown() =
        runBlocking {
            copy.erase()
            directory.deleteRecursively()
        }

    private fun databaseFile() = File(directory, RoomOfflineCopy.DATABASE_FILE)

    @Test
    fun theListReadsBackInTheInstancesOrder() =
        runBlocking {
            copy.openOrErase(jane, now)

            copy.vaults.replaceAll(jane, listOf(work, personal, family), now)

            assertEquals(listOf(work, personal, family), copy.vaults.all()?.value)
            assertEquals(now, copy.vaults.all()?.syncedAt)
        }

    @Test
    fun aNewAnswerReplacesTheCopyEntirely() =
        runBlocking {
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal, family, work), now)

            copy.vaults.replaceAll(jane, listOf(family.copy(name = "Famille"), personal), now + 1)

            assertEquals(listOf(family.copy(name = "Famille"), personal), copy.vaults.all()?.value)
        }

    @Test
    fun oneVaultIsKnownFromTheListAndLeavesWhenGone() =
        runBlocking {
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal, family), now)

            assertEquals(Cached(family, now), copy.vaults.one("v2"))

            copy.vaults.remove(jane, "v2")
            assertNull(copy.vaults.one("v2"))
            assertEquals(listOf(personal), copy.vaults.all()?.value)
        }

    @Test
    fun aWriteForAnotherOwnerIsDropped() =
        runBlocking {
            copy.openOrErase(jane, now)

            copy.vaults.replaceAll(Owner("https://elsewhere.test", "7"), listOf(personal), now)

            assertNull(copy.vaults.all())
        }

    @Test
    fun theFilesAreUnreadableWithoutTheKey() =
        runBlocking {
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal), now)
            copy.contacted(now + RoomOfflineCopy.CONTACT_INTERVAL)

            val bytes =
                directory
                    .walk()
                    .filter { it.isFile }
                    .map { it.readBytes() }
                    .toList()
            val header = databaseFile().readBytes().copyOfRange(0, 16)

            assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))
            for (content in bytes) {
                assertFalse(String(content, Charsets.ISO_8859_1).contains("Personal secret name"))
                assertFalse(String(content, Charsets.ISO_8859_1).contains("A very private description"))
            }
        }

    @Test
    fun theCopyLivesWhereAndroidNeverBacksUp() {
        assertTrue(databaseFile().canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
    }

    @Test
    fun erasingDestroysTheKeyThePassphraseAndEveryFile() =
        runBlocking {
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal), now)
            val wrappedPassphrase = File(directory, RoomOfflineCopy.KEY_FILE).readBytes()

            copy.erase()

            assertFalse(directory.exists())
            assertTrue(runCatching { cipher.decrypt(wrappedPassphrase) }.isFailure)
            assertNull(copy.vaults.all())
        }

    @Test
    fun aCopyWhoseKeyWasLostIsErasedAndReopenedEmpty() =
        runBlocking {
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal), now)
            copy.close()
            // The Keystore lost the key (phone reset, security change): the files stay, unreadable.
            cipher.destroyKey()

            val restarted = RoomOfflineCopy(context, directory, cipher, Dispatchers.IO)
            restarted.openOrErase(jane, now)

            assertNull(restarted.vaults.all())
            restarted.erase()
        }

    @Test
    fun aCopyIsKeptForTheSameOwnerAcrossARestart() =
        runBlocking {
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal), now)
            copy.close()

            val restarted = RoomOfflineCopy(context, directory, cipher, Dispatchers.IO)
            restarted.openOrErase(jane, now + RoomOfflineCopy.MAX_AGE)

            assertEquals(listOf(personal), restarted.vaults.all()?.value)
            restarted.erase()
        }

    @Test
    fun aCopyIsErasedAtRestartForAnotherOwnerAfter30DaysOrWithAClockGoneBack() =
        runBlocking {
            for ((owner, at) in listOf(
                Owner("https://monica.test", "8") to now,
                jane to now + RoomOfflineCopy.MAX_AGE + 1,
                jane to now - 1,
            )) {
                copy.openOrErase(jane, now)
                copy.vaults.replaceAll(jane, listOf(personal), now)
                copy.close()

                val restarted = RoomOfflineCopy(context, directory, cipher, Dispatchers.IO)
                restarted.openOrErase(owner, at)

                assertNull("$owner at $at", restarted.vaults.all())
                restarted.erase()
            }
        }

    @Test
    fun everyTableIsGoneAfterErasingIncludingOnesAddedLater() =
        runBlocking {
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal), now)

            copy.erase()

            // Nothing is left to open: no file means no table, whatever tables later versions add.
            assertFalse(databaseFile().exists())
            assertTrue(directory.listFiles().isNullOrEmpty())
        }

    @Test
    fun aClockSetBackBetweenTwoOpeningsErasesTheCopy() =
        runBlocking {
            val tenDays = 10 * 24 * 3_600_000L
            copy.openOrErase(jane, now)
            copy.vaults.replaceAll(jane, listOf(personal), now)
            copy.close()
            // Opened ten days later (still fresh), then the clock is set back five days.
            RoomOfflineCopy(context, directory, cipher, Dispatchers.IO).apply {
                openOrErase(jane, now + tenDays)
                close()
            }

            val restarted = RoomOfflineCopy(context, directory, cipher, Dispatchers.IO)
            restarted.openOrErase(jane, now + tenDays / 2)

            assertNull(restarted.vaults.all())
            restarted.erase()
        }

    @Test
    fun aCopyLeftOpenPast30DaysIsErasedOnTheNextRead() =
        runBlocking {
            var clock = now
            val open = RoomOfflineCopy(context, directory, cipher, Dispatchers.IO, clock = { clock })
            open.openOrErase(jane, now)
            open.vaults.replaceAll(jane, listOf(personal), now)

            clock = now + RoomOfflineCopy.MAX_AGE + 1

            assertNull(open.vaults.all())
            assertFalse(directory.exists())
        }
}
