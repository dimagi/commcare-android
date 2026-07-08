package org.commcare.login

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectCredentialResolverTest {
    private val context = mockk<Context>(relaxed = true)
    private val resolver = ConnectCredentialResolver(context)

    @Before
    fun setUp() {
        mockkStatic(ConnectAppDatabaseUtil::class)
        mockkStatic(FirebaseAnalyticsUtil::class)
        every {
            FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(any())
        } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectAppDatabaseUtil::class)
        unmockkStatic(FirebaseAnalyticsUtil::class)
    }

    @Test
    fun `returns existing record password`() {
        val record = recordWith(password = "stored-pw", localPassphrase = false)
        every {
            ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice")
        } returns record

        val result = resolver.resolve("app-1", "alice", createIfNeeded = false)

        assertEquals("stored-pw", result.password)
        assertSame(record, result)
        verify(exactly = 0) { FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(any()) }
    }

    @Test
    fun `reports analytics when existing record uses local passphrase`() {
        val record = recordWith(password = "pw", localPassphrase = true)
        every {
            ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice")
        } returns record

        resolver.resolve("app-1", "alice", createIfNeeded = false)

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase("app-1") }
    }

    @Test
    fun `creates a new record when createIfNeeded and none exists`() {
        every { ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice") } returns null
        val created = recordWith(password = "generated", localPassphrase = true)
        every {
            ConnectAppDatabaseUtil.storeApp(context, "app-1", "alice", true, any(), true)
        } returns created

        val result = resolver.resolve("app-1", "alice", createIfNeeded = true)

        assertEquals("generated", result.password)
        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase("app-1") }
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when no record exists and createIfNeeded is false`() {
        every {
            ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice")
        } returns null
        resolver.resolve("app-1", "alice", createIfNeeded = false)
    }

    @Test
    fun `generated password is 20 characters in the documented alphabet`() {
        val passwordSlot = slot<String>()
        every {
            ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice")
        } returns null
        val created = recordWith(password = "ignored", localPassphrase = true)
        every {
            ConnectAppDatabaseUtil.storeApp(context, "app-1", "alice", true, capture(passwordSlot), true)
        } returns created

        resolver.resolve("app-1", "alice", createIfNeeded = true)

        val generated = passwordSlot.captured
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?"
        assertEquals(20, generated.length)
        assertTrue(
            "generated password '$generated' contains a character outside the documented alphabet",
            generated.all { alphabet.contains(it) },
        )
    }

    private fun recordWith(
        password: String,
        localPassphrase: Boolean,
    ): ConnectLinkedAppRecord {
        val record = mockk<ConnectLinkedAppRecord>()
        every { record.password } returns password
        every { record.isUsingLocalPassphrase } returns localPassphrase
        return record
    }
}
