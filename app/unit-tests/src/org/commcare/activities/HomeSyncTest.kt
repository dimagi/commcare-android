package org.commcare.activities

import org.commcare.CommCareApplication
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.android.util.ReflectionUtils
import org.commcare.preferences.HiddenPreferences
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.commcare.tasks.PullTaskResultReceiver
import org.commcare.tasks.ResultAndError
import org.commcare.utils.FormUploadResult
import org.commcare.views.notifications.NotificationActionButtonInfo
import org.commcare.views.notifications.NotificationMessageFactory
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages
import org.javarosa.core.services.locale.Localization
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/**
 * Characterization pins for the home screen's sync path: what starting a sync asks the syncer for,
 * and what the user sees for each pull and form-send result.
 *
 * Pull results are delivered to the home activity's task receiver, the object `DataPullTask`
 * reports to, so these pins hold whichever class implements the receiver.
 */
class HomeSyncTest : BaseHomeScreenActivityTest() {
    @After
    fun resetBlockedUpdate() {
        UpdateActivity.sBlockedUpdateWorkflowInProgress = false
    }

    // region starting a sync

    @Test
    fun `sync button sends forms and then syncs, as a user-triggered sync`() {
        val home = buildVisibleHome()

        clickHomeButton(home, Localization.get("home.sync"))

        assertEquals(1, fakeSyncer.formSendCount)
        assertTrue(fakeSyncer.lastSendSyncsAfterwards)
        assertTrue(fakeSyncer.lastSendUserTriggered)
        assertTrue(
            "a blocking sync stamps the last upload-sync attempt",
            HiddenPreferences.getLastUploadSyncAttempt() > 0,
        )
    }

    @Test
    fun `a user-triggered sync gets a cancellable progress dialog`() {
        val home = buildVisibleHome()
        clickHomeButton(home, Localization.get("home.sync"))

        val dialog = home.generateProgressDialog(DataPullTask.DATA_PULL_TASK_ID)

        assertEquals(true, ReflectionUtils.readField(dialog, "usingCancelButton"))
    }

    @Test
    fun `an automatic sync gets a progress dialog that cannot be cancelled`() {
        val home = buildVisibleHome()

        val dialog = home.generateProgressDialog(DataPullTask.DATA_PULL_TASK_ID)

        assertEquals(false, ReflectionUtils.readField(dialog, "usingCancelButton"))
    }

    @Test
    fun `the injected syncer still receives syncs after recreation`() {
        val controller = buildStandardHomeController()
        controller.recreate()
        ShadowLooper.idleMainLooper()
        injectFakeSyncer(controller.get())
        controller.start().resume().visible()
        ShadowLooper.idleMainLooper()

        clickHomeButton(controller.get(), Localization.get("home.sync"))

        assertEquals(1, fakeSyncer.formSendCount)
    }

    // endregion

    // region pull results

    @Test
    fun `a successful pull toasts and shows the success message on the sync button`() {
        val home = buildVisibleHome()

        deliverPullResult(home, PullTaskResult.DOWNLOAD_SUCCESS)

        val expected = Localization.get("sync.success.synced")
        assertEquals(expected, ShadowToast.getTextOfLatestToast())
        assertEquals(expected, homeButtonSubText(home, Localization.get("home.sync")))
    }

    @Test
    fun `a successful pull clears the bad certificate notification`() {
        val home = buildVisibleHome()
        reportBadCertificate()

        deliverPullResult(home, PullTaskResult.DOWNLOAD_SUCCESS)

        assertTrue(pendingNotifications().none { it.title == badCertificateTitle() })
    }

    @Test
    fun `a failed pull toasts the failure reason`() {
        val home = buildVisibleHome()

        deliverPullResult(home, PullTaskResult.AUTH_FAILED)

        val expected = Localization.get("sync.fail.auth.loggedin")
        assertEquals(expected, ShadowToast.getTextOfLatestToast())
        assertEquals(expected, homeButtonSubText(home, Localization.get("home.sync")))
    }

    @Test
    fun `an actionable pull failure toasts the server's message`() {
        val home = buildVisibleHome()

        receiverOf(home).handlePullTaskResult(
            ResultAndError(PullTaskResult.ACTIONABLE_FAILURE, "Ask your supervisor"),
            true,
            true,
            true,
        )
        ShadowLooper.idleMainLooper()

        assertEquals("Ask your supervisor", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a bad certificate pull reports the certificate notification`() {
        val home = buildVisibleHome()

        deliverPullResult(home, PullTaskResult.BAD_CERTIFICATE)

        assertEquals(Localization.get("sync.fail.badcert"), ShadowToast.getTextOfLatestToast())
        assertTrue(pendingNotifications().any { it.title == badCertificateTitle() })
    }

    @Test
    fun `a pull error toasts an unknown failure`() {
        val home = buildVisibleHome()

        receiverOf(home).handlePullTaskError()
        ShadowLooper.idleMainLooper()

        assertEquals(Localization.get("sync.fail.unknown"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a pull during a blocked update resumes the update with the sync outcome`() {
        val home = buildVisibleHome()
        UpdateActivity.sBlockedUpdateWorkflowInProgress = true

        deliverPullResult(home, PullTaskResult.DOWNLOAD_SUCCESS)

        val intent = assertStarted(home, UpdateActivity::class.java)
        assertTrue(intent.getBooleanExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, false))
        assertTrue(intent.getBooleanExtra(UpdateActivity.KEY_PRE_UPDATE_SYNC_SUCCEED, false))
    }

    @Test
    fun `a failed pull during a blocked update resumes the update as failed`() {
        val home = buildVisibleHome()
        UpdateActivity.sBlockedUpdateWorkflowInProgress = true

        deliverPullResult(home, PullTaskResult.SERVER_ERROR)

        val intent = assertStarted(home, UpdateActivity::class.java)
        assertFalse(intent.getBooleanExtra(UpdateActivity.KEY_PRE_UPDATE_SYNC_SUCCEED, true))
    }

    // endregion

    // region form-send results

    @Test
    fun `a full form send toasts the sent-forms label`() {
        val home = buildVisibleHome()

        deliverFormUploadResult(home, FormUploadResult.FULL_SUCCESS, "2 forms sent", userTriggered = true)

        assertEquals("2 forms sent", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a failed form send toasts the failure`() {
        val home = buildVisibleHome()

        deliverFormUploadResult(home, FormUploadResult.TRANSPORT_FAILURE, "", userTriggered = true)

        assertEquals(
            Localization.get(FormUploadResult.TRANSPORT_FAILURE.localeKeyBase),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `a rate-limited user-triggered send shows the rate limit dialog`() {
        val home = buildVisibleHome()

        deliverFormUploadResult(home, FormUploadResult.RATE_LIMITED, "", userTriggered = true)
        home.supportFragmentManager.executePendingTransactions()

        assertNotNull(home.currentAlertDialog)
    }

    @Test
    fun `a rate-limited automatic send toasts instead of showing a dialog`() {
        val home = buildVisibleHome()

        deliverFormUploadResult(home, FormUploadResult.RATE_LIMITED, "", userTriggered = false)
        home.supportFragmentManager.executePendingTransactions()

        assertNull(home.currentAlertDialog)
        assertEquals(
            Localization.get(FormUploadResult.RATE_LIMITED.localeKeyBase),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    // endregion

    @Suppress("UNCHECKED_CAST")
    private fun receiverOf(home: StandardHomeActivity): PullTaskResultReceiver = home.receiver as PullTaskResultReceiver

    private fun deliverPullResult(
        home: StandardHomeActivity,
        result: PullTaskResult,
    ) {
        receiverOf(home).handlePullTaskResult(ResultAndError(result), true, true, true)
        ShadowLooper.idleMainLooper()
    }

    private fun deliverFormUploadResult(
        home: StandardHomeActivity,
        result: FormUploadResult,
        formLabel: String,
        userTriggered: Boolean,
    ) {
        home.handleFormUploadResult(result, formLabel, userTriggered)
        ShadowLooper.idleMainLooper()
    }

    private fun reportBadCertificate() {
        CommCareApplication.notificationManager().reportNotificationMessage(
            NotificationMessageFactory.message(
                StockMessages.BadSslCertificate,
                NotificationActionButtonInfo.ButtonAction.LAUNCH_DATE_SETTINGS,
            ),
        )
    }

    private fun badCertificateTitle(): String = NotificationMessageFactory.message(StockMessages.BadSslCertificate).title
}
