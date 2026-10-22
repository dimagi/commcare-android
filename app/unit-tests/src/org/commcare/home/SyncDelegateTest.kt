package org.commcare.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_APP_PATH
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_PASSWORD
import org.commcare.activities.BaseHomeScreenActivityTest.Companion.TEST_USER
import org.commcare.activities.CommCareActivity
import org.commcare.android.mocks.FormAndDataSyncerFake
import org.commcare.android.util.ReflectionUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.sync.ProcessAndSendTask
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.commcare.tasks.ResultAndError
import org.commcare.utils.FormUploadResult
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SyncDelegateTest {
    private val host = RecordingSyncHost()
    private val syncer = FormAndDataSyncerFake()
    private val delegate = SyncDelegate(host)

    @Before
    fun setUp() {
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)
        delegate.setFormAndDataSyncer(syncer)
    }

    @Test
    fun `send forms or sync asks the syncer to send and then sync`() {
        delegate.sendFormsOrSync(userTriggered = true)

        assertEquals(1, syncer.formSendCount)
        assertTrue(syncer.lastSendSyncsAfterwards)
        assertTrue(syncer.lastSendUserTriggered)
    }

    @Test
    fun `a pull-only sync goes to the syncer without sending forms`() {
        delegate.syncDataForLoggedInUser(userTriggered = false)

        assertEquals(1, syncer.dataSyncCount)
        assertEquals(0, syncer.formSendCount)
    }

    @Test
    fun `each pull failure reports its own message`() {
        val expected =
            mapOf(
                PullTaskResult.AUTH_FAILED to "sync.fail.auth.loggedin",
                PullTaskResult.SERVER_ERROR to "sync.fail.server.error",
                PullTaskResult.UNREACHABLE_HOST to "sync.fail.bad.network",
                PullTaskResult.CAPTIVE_PORTAL to "connection.captive_portal.action",
                PullTaskResult.AUTH_OVER_HTTP to "auth.over.http",
            )

        expected.keys.forEach { deliverPull(it) }

        assertEquals(expected.values.map { Localization.get(it) to false }, host.uiUpdates)
    }

    @Test
    fun `a successful pull reports success`() {
        deliverPull(PullTaskResult.DOWNLOAD_SUCCESS)

        assertEquals(listOf(Localization.get("sync.success.synced") to true), host.uiUpdates)
    }

    @Test
    fun `pull results with no message still reach the host`() {
        deliverPull(PullTaskResult.RETRY_NEEDED)
        deliverPull(PullTaskResult.STORAGE_FULL)

        assertTrue(host.uiUpdates.isEmpty())
        assertEquals(listOf(PullTaskResult.RETRY_NEEDED, PullTaskResult.STORAGE_FULL), host.finishedPulls)
    }

    @Test
    fun `the host hears about a pull after its message is shown`() {
        deliverPull(PullTaskResult.SERVER_ERROR)

        assertEquals(listOf("ui", "finished"), host.events)
    }

    @Test
    fun `a pull error reports an unknown failure`() {
        delegate.handlePullTaskError()

        assertEquals(listOf(Localization.get("sync.fail.unknown") to false), host.uiUpdates)
    }

    @Test
    fun `a cancelled form send names the forms`() {
        delegate.handleFormUploadResult(FormUploadResult.CANCELLED, "3 forms", userTriggered = true)

        val message = Localization.get(FormUploadResult.CANCELLED.localeKeyBase) + " 3 forms"
        assertEquals(listOf(message to false), host.uiUpdates)
    }

    @Test
    fun `an automatic rate-limited send reports the rate limit`() {
        delegate.handleFormUploadResult(FormUploadResult.RATE_LIMITED, "", userTriggered = false)

        assertEquals(listOf(Localization.get(FormUploadResult.RATE_LIMITED.localeKeyBase) to false), host.uiUpdates)
    }

    @Test
    fun `only the first pull dialog after a user-started sync can be cancelled`() {
        delegate.sendFormsOrSync(userTriggered = true)

        assertTrue(isCancellable(delegate.generateProgressDialog(DataPullTask.DATA_PULL_TASK_ID)))
        assertEquals(false, isCancellable(delegate.generateProgressDialog(DataPullTask.DATA_PULL_TASK_ID)))
    }

    @Test
    fun `form-send dialogs can always be cancelled`() {
        assertTrue(isCancellable(delegate.generateProgressDialog(ProcessAndSendTask.SEND_PHASE_ID)))
        assertTrue(isCancellable(delegate.generateProgressDialog(ProcessAndSendTask.PROCESSING_PHASE_ID)))
    }

    @Test
    fun `tasks that aren't sync tasks get no dialog from the delegate`() {
        assertNull(delegate.generateProgressDialog(ProcessAndSendTask.SEND_PHASE_ID_NO_DIALOG))
    }

    private fun deliverPull(result: PullTaskResult) {
        delegate.handlePullTaskResult(ResultAndError(result), true, true, true)
    }

    private fun isCancellable(dialog: Any?): Boolean = ReflectionUtils.readField(dialog!!, "usingCancelButton") as Boolean

    private class RecordingSyncHost : SyncHost {
        val uiUpdates = mutableListOf<Pair<String, Boolean>>()
        val finishedPulls = mutableListOf<PullTaskResult>()
        val events = mutableListOf<String>()

        override val syncConnector: CommCareActivity<*>
            get() = throw UnsupportedOperationException("these tests don't run tasks")

        override fun updateUiAfterDataPullOrSend(
            message: String,
            success: Boolean,
        ) {
            uiUpdates += message to success
            events += "ui"
        }

        override fun onPullTaskFinished(result: PullTaskResult) {
            finishedPulls += result
            events += "finished"
        }
    }
}
