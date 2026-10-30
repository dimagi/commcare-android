package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_APP_PATH
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_PASSWORD
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_USER
import org.commcare.activities.DispatchActivity
import org.commcare.activities.FormEntryActivity
import org.commcare.activities.components.FormEntrySessionWrapper
import org.commcare.android.database.user.models.FormRecord
import org.commcare.android.util.TestAppInstaller
import org.commcare.preferences.DevSessionRestorer
import org.commcare.preferences.HiddenPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SessionLaunchDelegateTest {
    private val host = FakeHomeActivityHost()
    private val delegate = SessionLaunchDelegate(host)

    @Before
    fun setUp() {
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)
    }

    @Test
    fun `starting a form commits a stub record and launches form entry on it`() {
        val state = seatFormCommand()

        delegate.startForm(state)

        val (intent, requestCode) = host.startedForResult.single()
        assertEquals(SessionLaunchDelegate.FORM_ENTRY_REQUEST_CODE, requestCode)
        assertEquals(FormEntryActivity::class.java.name, intent.component!!.className)
        assertEquals(
            CommCareApplication.instance().commCarePlatform.getFormDefId(state.formRecord.formNamespace),
            intent.getIntExtra(FormEntryActivity.KEY_FORM_DEF_ID, -1),
        )
        assertEquals(host.formEntryTitle, intent.getStringExtra(FormEntryActivity.KEY_HEADER_STRING))
        assertFalse(intent.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, true))
        assertEquals(FormRecord.STATUS_UNSTARTED, state.formRecord.status)
        assertEquals("background syncs are held off for form entry", 1, host.formEntryLaunchingCount)
    }

    @Test
    fun `session restoration claims the launch only when the session has a command`() {
        assertFalse(delegate.tryRestoringSession())
        assertEquals(0, host.sessionStepCount)

        seatFormCommand()

        assertTrue(delegate.tryRestoringSession())
        assertEquals(1, host.sessionStepCount)
    }

    @Test
    fun `a restored session replays its form entry session into the next form only`() {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION, SAVED_FORM_ENTRY_SESSION)
            .apply()
        val state = seatFormCommand()
        delegate.tryRestoringSession()

        delegate.startForm(state)
        state.setFormRecordId(-1)
        delegate.startForm(state)

        val (restored, next) = host.startedForResult.map { it.first }
        assertEquals(SAVED_FORM_ENTRY_SESSION, restored.getStringExtra(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION))
        assertFalse(next.hasExtra(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION))
    }

    @Test
    fun `an interrupted form is reopened as a restart after expiration`() {
        val state = seatFormCommand()
        state.commitStub()
        HiddenPreferences.setInterruptedSSD(state.sessionDescriptorId)

        assertTrue(delegate.tryRestoringFormFromSessionExpiration())

        val intent = host.startedForResult.single().first
        assertTrue(intent.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false))
    }

    @Test
    fun `no form is restored when none was interrupted`() {
        HiddenPreferences.clearInterruptedSSD()

        assertFalse(delegate.tryRestoringFormFromSessionExpiration())
        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `the update info form step does nothing unless an update asked for it`() {
        assertFalse(delegate.showUpdateInfoForm())
        assertEquals(0, host.safeSessionStepCount)
    }

    @Test
    fun `a completed form is reported as completed`() {
        val state = seatFormCommand()
        delegate.startForm(state)
        setStatus(state.formRecord, FormRecord.STATUS_UNSENT)

        val outcome = delegate.handleFormEntryResult(RESULT_OK, Intent(), wasExternal = false)

        assertTrue(outcome.formCompleted)
        assertEquals(1, host.refreshHostUiCount)
        assertTrue(host.finishedWithResults.isEmpty())
    }

    @Test
    fun `an external launch that asked to exit is finished once its form is cancelled`() {
        host.launchIntent = Intent().putExtra(DispatchActivity.EXIT_AFTER_FORM_SUBMISSION, true)
        val state = seatFormCommand()
        delegate.startForm(state)

        val outcome = delegate.handleFormEntryResult(RESULT_CANCELED, Intent(), wasExternal = true)

        assertFalse(outcome.continueSessionNav)
        assertFalse(outcome.formCompleted)

        assertEquals(listOf(RESULT_CANCELED), host.finishedWithResults)
        assertFalse("the exit request is consumed", host.launchIntent!!.hasExtra(DispatchActivity.EXIT_AFTER_FORM_SUBMISSION))
    }

    @Test
    fun `the exit request is ignored when the host was not launched externally`() {
        host.launchIntent = Intent().putExtra(DispatchActivity.EXIT_AFTER_FORM_SUBMISSION, true)
        val state = seatFormCommand()
        delegate.startForm(state)

        assertTrue(delegate.handleFormEntryResult(RESULT_CANCELED, Intent(), wasExternal = false).continueSessionNav)

        assertTrue(host.finishedWithResults.isEmpty())
    }

    @Test
    fun `cancelling an incomplete form shows the incomplete forms list`() {
        val state = seatFormCommand()
        delegate.startForm(state)
        val record = setStatus(state.formRecord, FormRecord.STATUS_INCOMPLETE)

        assertFalse(delegate.handleFormEntryResult(RESULT_CANCELED, Intent(), wasExternal = false).continueSessionNav)

        val (incomplete, shown) = host.shownFormArchives.single()
        assertTrue(incomplete)
        assertEquals(record.id, shown!!.id)
    }

    @Test
    fun `a form result with no record clears the session and warns`() {
        seatFormCommand()

        assertFalse(delegate.handleFormEntryResult(RESULT_OK, Intent(), wasExternal = false).continueSessionNav)

        assertNull(CommCareApplication.instance().currentSession.command)
        assertEquals(1, host.refreshHostUiCount)
        assertEquals(1, host.shownDialogs.size)
    }

    @Test
    fun `backing out of the incomplete forms list stays home`() {
        assertFalse(delegate.handleIncompleteFormResult(RESULT_CANCELED, null))
        assertEquals(1, host.refreshHostUiCount)
    }

    @Test
    fun `an incomplete forms result with no record continues the session`() {
        assertTrue(delegate.handleIncompleteFormResult(RESULT_OK, Intent()))
        assertTrue(host.startedForResult.isEmpty())
    }

    @Test
    fun `choosing an incomplete form reopens its saved instance`() {
        val state = seatFormCommand()
        state.commitStub()
        val record = setStatus(state.formRecord, FormRecord.STATUS_INCOMPLETE, filePath = "incomplete-form.xml")
        state.reset()

        assertFalse(delegate.handleIncompleteFormResult(RESULT_OK, Intent().putExtra("FORMRECORDS", record.id)))

        val intent = host.startedForResult.single().first
        assertEquals(record.id, intent.getIntExtra(FormEntryActivity.KEY_FORM_RECORD_ID, -1))
        assertSame(state, CommCareApplication.instance().currentSessionWrapper)
        assertEquals(record.id, state.formRecordId)
    }

    private fun seatFormCommand() =
        CommCareApplication.instance().currentSessionWrapper.also {
            it.session.setCommand(FORM_COMMAND)
        }

    private fun setStatus(
        record: FormRecord,
        status: String,
        filePath: String? = null,
    ): FormRecord {
        record.status = status
        filePath?.let { record.filePath = it }
        CommCareApplication.instance().getUserStorage(FormRecord::class.java).write(record)
        return record
    }

    companion object {
        private const val FORM_COMMAND = "m0-f0"
        private const val SAVED_FORM_ENTRY_SESSION = "saved-form-entry-session"
    }
}
