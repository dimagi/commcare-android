package org.commcare.connect

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectSyncFeedback.Outcome
import org.commcare.dalvik.R
import org.commcare.home.FormSendProgress
import org.commcare.home.SyncStepResult
import org.commcare.sync.ProcessAndSendTask
import org.commcare.tasks.DataPullTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectSyncFeedbackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val feedback = ConnectSyncFeedback()

    private fun formStep(
        sent: Int,
        userTriggered: Boolean = true,
    ) = SyncStepResult("$sent forms sent", true, userTriggered, formsSent = sent, pullFollows = true)

    private fun pullStep(
        success: Boolean = true,
        userTriggered: Boolean = true,
    ) = SyncStepResult(if (success) "synced" else "server error", success, userTriggered)

    @Test
    fun `a form send with a pull to come leaves the sync pending`() {
        assertEquals(Outcome.Pending, feedback.onStepFinished(formStep(2)))
    }

    @Test
    fun `a sync that sent forms completes with their count`() {
        feedback.onStepFinished(formStep(2))

        assertEquals(Outcome.Complete(2), feedback.onStepFinished(pullStep()))
    }

    @Test
    fun `a sync with no forms to send is up to date`() {
        feedback.onStepFinished(formStep(0))

        assertEquals(Outcome.UpToDate, feedback.onStepFinished(pullStep()))
    }

    @Test
    fun `a failed pull fails with its message`() {
        feedback.onStepFinished(formStep(2))

        assertEquals(Outcome.Failed("server error"), feedback.onStepFinished(pullStep(success = false)))
    }

    @Test
    fun `a failed form send fails with its message`() {
        val step = SyncStepResult("could not send", false, true, formsSent = 1)

        assertEquals(Outcome.Failed("could not send"), feedback.onStepFinished(step))
    }

    @Test
    fun `forms sent by one sync don't count toward the next`() {
        feedback.onStepFinished(formStep(2))
        feedback.onStepFinished(pullStep())

        assertEquals(Outcome.UpToDate, feedback.onStepFinished(pullStep()))
    }

    @Test
    fun `a sync the user didn't start shows each message as before`() {
        assertEquals(Outcome.Toast("2 forms sent"), feedback.onStepFinished(formStep(2, userTriggered = false)))
        assertEquals(Outcome.Toast("synced"), feedback.onStepFinished(pullStep(userTriggered = false)))
    }

    @Test
    fun `a sync that didn't start fails with the reason`() {
        assertEquals(Outcome.Failed("offline"), feedback.onSyncNotAttempted("offline"))
    }

    @Test
    fun `each sync task gets a dialog, and nothing else does`() {
        assertNotNull(feedback.progressDialogFor(context, ProcessAndSendTask.PROCESSING_PHASE_ID))
        assertNotNull(feedback.progressDialogFor(context, ProcessAndSendTask.SEND_PHASE_ID))
        assertNotNull(feedback.progressDialogFor(context, DataPullTask.DATA_PULL_TASK_ID))
        assertNull(feedback.progressDialogFor(context, ProcessAndSendTask.SEND_PHASE_ID_NO_DIALOG))
        assertFalse(feedback.isSyncTask(ProcessAndSendTask.SEND_PHASE_ID_NO_DIALOG))
    }

    @Test
    fun `a hidden sync shows no more dialogs until it ends`() {
        feedback.onProgressHidden()

        assertNull(feedback.progressDialogFor(context, DataPullTask.DATA_PULL_TASK_ID))

        feedback.onStepFinished(pullStep())

        assertNotNull(feedback.progressDialogFor(context, DataPullTask.DATA_PULL_TASK_ID))
    }

    @Test
    fun `a new sync shows its progress again`() {
        feedback.onProgressHidden()

        feedback.onSyncStarted()

        assertFalse(feedback.progressHidden)
    }

    @Test
    fun `the hidden progress and forms sent survive a saved state`() {
        feedback.onStepFinished(formStep(3))
        feedback.onProgressHidden()
        val saved = Bundle().also { feedback.saveState(it) }

        val restored = ConnectSyncFeedback().apply { restoreState(saved) }

        assertTrue(restored.progressHidden)
        assertEquals(Outcome.Complete(3), restored.onStepFinished(pullStep()))
    }

    @Test
    fun `form progress reads as forms of the total`() {
        val message = feedback.formProgressMessage(context, FormSendProgress.State(1, 4, 25))

        assertEquals(context.getString(R.string.connect_sync_dialog_forms_progress, 1, 4), message)
    }
}
