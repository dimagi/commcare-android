package org.commcare.activities

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import org.commcare.CommCareApplication
import org.commcare.activities.components.FormEntryConstants
import org.commcare.activities.components.FormEntrySessionWrapper
import org.commcare.android.database.user.models.FormRecord
import org.commcare.android.util.ActivityAssertions.assertOnly
import org.commcare.android.util.ActivityAssertions.assertStartedForResult
import org.commcare.android.util.ActivityAssertions.startedForResultIntents
import org.commcare.android.util.ActivityAssertions.startedIntents
import org.commcare.preferences.DevSessionRestorer
import org.commcare.preferences.HiddenPreferences
import org.commcare.session.SessionFrame
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization pins for home's form-entry leg: how it launches form entry, and what it does with
 * the result form entry hands back.
 *
 * The completed-form leg already goes through home in `EndOfFormTest`, `FormRecordProcessingTest`
 * and `XFormUpdateInfoTest`. These pin the rest: cancelled and viewed forms, the external-launch
 * exit, the interrupted-form relaunch, reopening an incomplete form, and the restored form-entry
 * session.
 */
class HomeFormEntryTest : BaseHomeScreenActivityTest() {
    @Test
    fun `a completed form is queued for sending without a sync`() {
        val home = buildVisibleHome()
        enterForm(home)
        setCurrentRecordStatus(FormRecord.STATUS_UNSENT)

        deliverFormEntryResult(home, RESULT_OK)

        assertEquals(1, fakeSyncer.formSendCount)
        assertFalse("a form save sends forms only", fakeSyncer.lastSendSyncsAfterwards)
        assertFalse(fakeSyncer.lastSendUserTriggered)
    }

    @Test
    fun `cancelling an unstarted form wipes its record and steps back to the menu`() {
        val home = buildVisibleHome()
        enterForm(home)
        val recordId = currentRecordId()

        deliverFormEntryResult(home, RESULT_CANCELED)

        assertFalse("the stub record should be wiped", formStorage().exists(recordId))
        assertEquals(-1, CommCareApplication.instance().currentSessionWrapper.formRecordId)
        assertStartedForResult(home, MenuActivity::class.java)
    }

    @Test
    fun `cancelling an incomplete form returns to the incomplete forms list`() {
        val home = buildVisibleHome()
        enterForm(home)
        setCurrentRecordStatus(FormRecord.STATUS_INCOMPLETE)
        val recordId = currentRecordId()

        deliverFormEntryResult(home, RESULT_CANCELED)

        val archive = assertStartedForResult(home, FormRecordListActivity::class.java)
        assertEquals(FormRecord.STATUS_INCOMPLETE, archive.getStringExtra(FormRecord.META_STATUS))
        assertEquals(recordId, archive.getIntExtra(FormRecordListActivity.KEY_INITIAL_RECORD_ID, -1))
        assertNull("the session should be reset", currentCommand())
    }

    @Test
    fun `leaving a form viewed from the archive returns to the saved forms list`() {
        val home = buildVisibleHome()
        enterForm(home)
        setCurrentRecordStatus(FormRecord.STATUS_SAVED)
        val recordId = currentRecordId()

        deliverFormEntryResult(home, RESULT_OK)

        val archive = assertStartedForResult(home, FormRecordListActivity::class.java)
        assertFalse(archive.hasExtra(FormRecord.META_STATUS))
        assertEquals(recordId, archive.getIntExtra(FormRecordListActivity.KEY_INITIAL_RECORD_ID, -1))
        assertEquals("viewing a form must not send anything", 0, fakeSyncer.formSendCount)
        assertNull("the session should be reset", currentCommand())
    }

    @Test
    fun `an external launch that asks to exit after the form finishes home on cancel`() {
        val home =
            buildVisibleHome {
                putExtra(DispatchActivity.WAS_EXTERNAL, true)
                putExtra(DispatchActivity.EXIT_AFTER_FORM_SUBMISSION, true)
            }
        startedForResultIntents(home)
        startedIntents(home)
        enterForm(home)

        deliverFormEntryResult(home, RESULT_CANCELED)

        assertTrue(home.isFinishing)
        assertEquals(RESULT_CANCELED, shadowOf(home).resultCode)
        assertNull("the session should be reset", currentCommand())
    }

    @Test
    fun `a form result with no form record clears the session and warns`() {
        val home = buildVisibleHome()
        enterForm(home)
        CommCareApplication.instance().currentSessionWrapper.setFormRecordId(-1)

        deliverFormEntryResult(home, RESULT_OK)

        assertTrue(startedForResultIntents(home).isEmpty())
        assertNull("the session should be reset", currentCommand())
        home.supportFragmentManager.executePendingTransactions()
        assertNotNull("the user should be warned the session was refreshed", home.currentAlertDialog)
    }

    @Test
    fun `a form interrupted by session expiration is relaunched as a restart`() {
        val home = buildVisibleHome()
        enterForm(home)
        val wrapper = CommCareApplication.instance().currentSessionWrapper
        HiddenPreferences.setInterruptedSSD(wrapper.sessionDescriptorId)

        deliverFormEntryResult(home, RESULT_CANCELED, Intent().putExtra(FormEntryConstants.WAS_INTERRUPTED, true))

        val formEntry = assertStartedForResult(home, FormEntryActivity::class.java)
        assertTrue(formEntry.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false))
    }

    @Test
    fun `choosing an incomplete form from the list reopens it in form entry`() {
        val home = buildVisibleHome()
        enterForm(home)
        val record = setCurrentRecordStatus(FormRecord.STATUS_INCOMPLETE)
        record.filePath = "incomplete-form.xml"
        formStorage().write(record)
        CommCareApplication.instance().currentSessionWrapper.reset()

        home.onActivityResultSessionSafe(
            HomeScreenBaseActivity.GET_INCOMPLETE_FORM,
            RESULT_OK,
            Intent().putExtra("FORMRECORDS", record.id),
        )

        val formEntry = assertStartedForResult(home, FormEntryActivity::class.java)
        assertEquals(record.id, formEntry.getIntExtra(FormEntryActivity.KEY_FORM_RECORD_ID, -1))
        assertFalse(formEntry.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, true))
    }

    @Test
    fun `restoring a saved session hands its form entry session to form entry once`() {
        HiddenPreferences.clearInterruptedSSD()
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION, SAVED_FORM_ENTRY_SESSION)
            .apply()
        CommCareApplication
            .instance()
            .currentSessionWrapper.session
            .setCommand(FORM_COMMAND)

        val home = buildHome { putExtra(DispatchActivity.START_FROM_LOGIN, true) }

        val restored = assertOnly(startedForResultIntents(home))
        assertEquals(
            SAVED_FORM_ENTRY_SESSION,
            restored.getStringExtra(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION),
        )
        deliverFormEntryResult(home, RESULT_CANCELED)
        val callout = assertStartedForResult(home, MenuActivity::class.java)
        home.onActivityResultSessionSafe(HomeScreenBaseActivity.GET_COMMAND, RESULT_OK, chose(FORM_COMMAND, callout))
        val nextEntry = assertStartedForResult(home, FormEntryActivity::class.java)
        assertFalse(
            "the saved session is only replayed into the restored form",
            nextEntry.hasExtra(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION),
        )
    }

    /**
     * Press Start and pick the form from the menu, leaving home waiting on form entry with a fresh
     * stub record seated, and both launch queues drained.
     */
    private fun enterForm(home: StandardHomeActivity) {
        clickHomeButton(home, Localization.get("home.start"))
        val callout = assertStartedForResult(home, MenuActivity::class.java)
        startedIntents(home)
        home.onActivityResultSessionSafe(HomeScreenBaseActivity.GET_COMMAND, RESULT_OK, chose(FORM_COMMAND, callout))
        assertStartedForResult(home, FormEntryActivity::class.java)
        startedIntents(home)
    }

    private fun deliverFormEntryResult(
        home: StandardHomeActivity,
        resultCode: Int,
        intent: Intent = Intent(),
    ) {
        home.onActivityResultSessionSafe(HomeScreenBaseActivity.MODEL_RESULT, resultCode, intent)
    }

    private fun chose(
        command: String,
        callout: Intent,
    ): Intent = Intent(callout).putExtra(SessionFrame.STATE_COMMAND_ID, command)

    private fun setCurrentRecordStatus(status: String): FormRecord {
        val record = CommCareApplication.instance().currentSessionWrapper.formRecord
        record.status = status
        formStorage().write(record)
        return record
    }

    private fun currentRecordId(): Int = CommCareApplication.instance().currentSessionWrapper.formRecordId

    private fun formStorage() = CommCareApplication.instance().getUserStorage(FormRecord::class.java)

    private fun currentCommand(): String? =
        CommCareApplication
            .instance()
            .currentSessionWrapper
            .session
            .command

    companion object {
        private const val FORM_COMMAND = "m0-f0"
        private const val SAVED_FORM_ENTRY_SESSION = "saved-form-entry-session"
    }
}
