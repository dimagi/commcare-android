package org.commcare.home

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.utils.SessionRegistrationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SessionExpirationDelegateTest {
    private val host = FakeHomeActivityHost()
    private var pendingExpiration = false
    private val delegate =
        SessionExpirationDelegate(host) {
            val pending = pendingExpiration
            pendingExpiration = false
            pending
        }

    @After
    fun tearDown() {
        delegate.stopListening()
    }

    @Test
    fun `no pending expiration reports nothing`() {
        assertFalse(delegate.handlePendingExpiration())
        assertEquals(0, host.sessionLostCount)
    }

    @Test
    fun `a pending expiration is reported once and consumed`() {
        pendingExpiration = true

        assertTrue(delegate.handlePendingExpiration())
        assertFalse(delegate.handlePendingExpiration())
        assertEquals(1, host.sessionLostCount)
    }

    @Test
    fun `a session found missing mid-resume is reported`() {
        delegate.handleSessionUnavailable()

        assertEquals(1, host.sessionLostCount)
    }

    @Test
    fun `an expiration broadcast while listening is reported and clears the pending flag`() {
        delegate.startListening()
        pendingExpiration = true

        broadcastExpiration()

        assertEquals(1, host.sessionLostCount)
        assertFalse(pendingExpiration)
    }

    @Test
    fun `an expiration broadcast after listening stops is not delivered`() {
        delegate.startListening()
        delegate.stopListening()

        broadcastExpiration()

        assertEquals(0, host.sessionLostCount)
    }

    @Test
    fun `listening twice registers only one receiver`() {
        delegate.startListening()
        delegate.startListening()

        broadcastExpiration()

        assertEquals(1, host.sessionLostCount)
    }

    @Test
    fun `stopping without listening does nothing`() {
        delegate.stopListening()
    }

    private fun broadcastExpiration() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.sendBroadcast(
            Intent(SessionRegistrationHelper.USER_SESSION_EXPIRED).setPackage(context.packageName),
        )
        ShadowLooper.idleMainLooper()
    }
}
