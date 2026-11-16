package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_FIRST_USER
import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.EntitySelectActivity
import org.commcare.activities.FormEntryActivity
import org.commcare.activities.MenuActivity
import org.commcare.android.util.TestAppInstaller
import org.commcare.models.AndroidSessionWrapper
import org.commcare.session.SessionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Session navigation over a real session in a case-list app (`index_and_cache_test`): the screen
 * each navigator status opens, and what each callout result does to the session.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SessionNavigationDelegateTest {
    private val host = FakeHomeActivityHost()
    private val sessionLaunch = SessionLaunchDelegate(host)
    private val clearedSessions = mutableListOf<Pair<AndroidSessionWrapper, Boolean>>()
    private val delegate =
        SessionNavigationDelegate(host, sessionLaunch) { state, shouldWarnUser ->
            clearedSessions += state to shouldWarnUser
        }

    @Before
    fun setUp() {
        TestAppInstaller.installAppAndLogin(CASE_LIST_APP_PATH, TEST_USER, TEST_PASSWORD)
        CommCareApplication.instance().currentSessionWrapper.reset()
    }

    // region navigator statuses

    @Test
    fun `a session without a command opens the menu`() {
        delegate.startNextSessionStep()

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(HomeRequestCodes.GET_COMMAND, requestCode)
        assertEquals(MenuActivity::class.java.name, intent.component!!.className)
    }

    @Test
    fun `a form needing a case opens the case list`() {
        setCommand(CASE_FORM_COMMAND)

        delegate.startNextSessionStep()

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(HomeRequestCodes.GET_CASE, requestCode)
        assertEquals(EntitySelectActivity::class.java.name, intent.component!!.className)
        assertEquals(CASE_FORM_COMMAND, intent.getStringExtra(SessionFrame.STATE_COMMAND_ID))
    }

    @Test
    fun `a form with everything it needs starts form entry`() {
        setCommand(REGISTRATION_FORM_COMMAND)

        delegate.startNextSessionStep()

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(HomeRequestCodes.MODEL_RESULT, requestCode)
        assertEquals(FormEntryActivity::class.java.name, intent.component!!.className)
    }

    @Test
    fun `the root menu launch carries the session's pending data`() {
        delegate.launchRootMenu()

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(HomeRequestCodes.GET_COMMAND, requestCode)
        assertEquals(MenuActivity::class.java.name, intent.component!!.className)
        assertEquals(SessionFrame.STATE_COMMAND_ID, intent.getStringExtra(PENDING_SESSION_DATA))
    }

    // endregion

    // region callout results

    @Test
    fun `backing out of the root menu stays home`() {
        setCommand(null)

        assertFalse(delegate.handleCalloutResult(HomeRequestCodes.GET_COMMAND, RESULT_CANCELED, null))

        assertEquals(1, host.refreshHostUiCount)
        assertNull(currentCommand())
    }

    @Test
    fun `backing out of a case list steps back and continues`() {
        setCommand(CASE_FORM_COMMAND)

        assertTrue(delegate.handleCalloutResult(HomeRequestCodes.GET_CASE, RESULT_CANCELED, null))

        assertNull(currentCommand())
        assertEquals(0, host.refreshHostUiCount)
    }

    @Test
    fun `a chosen command is seated and navigation goes on`() {
        delegate.launchRootMenu()
        val callout = host.startedForResult.single().first

        val chosen = Intent(callout).putExtra(SessionFrame.STATE_COMMAND_ID, CASE_FORM_COMMAND)

        assertTrue(delegate.handleCalloutResult(HomeRequestCodes.GET_COMMAND, RESULT_OK, chosen))
        assertEquals(CASE_FORM_COMMAND, currentCommand())
    }

    @Test
    fun `a stale command result clears the session`() {
        val stale =
            Intent()
                .putExtra(PENDING_SESSION_DATA, SessionFrame.STATE_DATUM_VAL)
                .putExtra(SessionFrame.STATE_COMMAND_ID, CASE_FORM_COMMAND)

        assertFalse(delegate.handleCalloutResult(HomeRequestCodes.GET_COMMAND, RESULT_OK, stale))

        assertEquals(true, clearedSessions.single().second)
        assertNull(currentCommand())
    }

    @Test
    fun `a chosen case is seated and navigation goes on`() {
        setCommand(CASE_FORM_COMMAND)
        delegate.startNextSessionStep()
        val callout = host.startedForResult.single().first

        val chosen = Intent(callout).putExtra(SessionFrame.STATE_DATUM_VAL, CASE_ID)

        assertTrue(delegate.handleCalloutResult(HomeRequestCodes.GET_CASE, RESULT_OK, chosen))
        assertEquals(CASE_ID, CommCareApplication.instance().currentSession.data[CASE_DATUM_ID])
    }

    @Test
    fun `an unknown result stops only the case list`() {
        assertFalse(delegate.handleCalloutResult(HomeRequestCodes.GET_CASE, RESULT_FIRST_USER, null))
        assertTrue(delegate.handleCalloutResult(HomeRequestCodes.GET_COMMAND, RESULT_FIRST_USER, null))
    }

    @Test
    fun `a cancelled remote request steps back and continues`() {
        setCommand(CASE_FORM_COMMAND)

        assertTrue(delegate.handleCalloutResult(HomeRequestCodes.GET_REMOTE_DATA, RESULT_CANCELED, null))
        assertNull(currentCommand())
    }

    @Test
    fun `a restart preselects the selected case`() {
        setCommand(CASE_FORM_COMMAND)

        delegate.restartAfterCallout(Intent().putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, CASE_ID))

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(HomeRequestCodes.GET_CASE, requestCode)
        assertEquals(CASE_ID, intent.getStringExtra(EntitySelectActivity.EXTRA_ENTITY_KEY))
    }

    @Test
    fun `the preselected case is used once`() {
        setCommand(CASE_FORM_COMMAND)
        delegate.restartAfterCallout(Intent().putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, CASE_ID))

        delegate.startNextSessionStep()

        assertFalse(
            host.startedForResult
                .last()
                .first
                .hasExtra(EntitySelectActivity.EXTRA_ENTITY_KEY),
        )
    }

    // endregion

    private fun setCommand(command: String?) {
        CommCareApplication
            .instance()
            .currentSessionWrapper.session
            .setCommand(command)
    }

    private fun currentCommand(): String? =
        CommCareApplication
            .instance()
            .currentSessionWrapper.session.command

    private companion object {
        const val CASE_LIST_APP_PATH = "jr://resource/commcare-apps/index_and_cache_test/profile.ccpr"
        const val TEST_USER = "test"
        const val TEST_PASSWORD = "123"
        const val REGISTRATION_FORM_COMMAND = "m0-f0"
        const val CASE_FORM_COMMAND = "m1-f0"
        const val CASE_DATUM_ID = "case_id"
        const val CASE_ID = "some-case-id"
        const val PENDING_SESSION_DATA = "pending-session-data-id"
    }
}
