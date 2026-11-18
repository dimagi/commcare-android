package org.commcare.activities

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.ActivityAssertions
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.pn.helper.NotificationTaskStack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric

class PushNotificationLaunchActivityTest : BaseHomeScreenActivityTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpConnectUser() {
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportPersonalIdAccountForgotten(any()) } returns Unit
        ConnectTestUtils.signInToPersonalId(hasConnectAccess = true)
        ConnectTestUtils.storeJob(ConnectTestUtils.connectJob(jobUUID = LAST_ACCESSED_UUID))
    }

    @After
    fun forgetPersonalIdUser() {
        PersonalIdSessionManager.getInstance().forgetUser("push notification launch activity test teardown")
    }

    @Test
    fun `a messaging notification opens the chat above the last accessed opportunity`() {
        PersonalIdUserPreferences.setLastAccessedOpportunityUuid(LAST_ACCESSED_UUID)

        val (bottomIntent, targetIntent) = ActivityAssertions.assertTwoScreenTask(launchNotification(buildMessagingIntent()))

        assertOpportunityHomeAtTaskBottom(bottomIntent, LAST_ACCESSED_UUID)
        assertOpenedFromNotificationOrInvite(targetIntent, ConnectMessagingActivity::class.java)
    }

    @Test
    fun `a messaging notification with no last accessed opportunity opens the chat above the Opportunity List`() {
        val (bottomIntent, targetIntent) = ActivityAssertions.assertTwoScreenTask(launchNotification(buildMessagingIntent()))

        assertOpportunityListAtTaskBottom(bottomIntent)
        assertOpenedFromNotificationOrInvite(targetIntent, ConnectMessagingActivity::class.java)
    }

    @Test
    fun `a notification for an opportunity at its intro opens it above the last accessed opportunity`() {
        PersonalIdUserPreferences.setLastAccessedOpportunityUuid(LAST_ACCESSED_UUID)
        ConnectTestUtils.storeJob(
            ConnectTestUtils.connectJob(jobUUID = TARGET_UUID, status = ConnectJobRecord.STATUS_AVAILABLE_NEW),
        )

        val (bottomIntent, targetIntent) =
            ActivityAssertions.assertTwoScreenTask(launchNotification(buildOpportunityIntent(TARGET_UUID)))

        assertOpportunityHomeAtTaskBottom(bottomIntent, LAST_ACCESSED_UUID)
        assertOpenedFromNotificationOrInvite(targetIntent, ConnectActivity::class.java)
    }

    @Test
    fun `a notification for an opportunity not stored yet with no last accessed one opens it above the Opportunity List`() {
        val (bottomIntent, targetIntent) =
            ActivityAssertions.assertTwoScreenTask(launchNotification(buildOpportunityIntent(TARGET_UUID)))

        assertOpportunityListAtTaskBottom(bottomIntent)
        assertOpenedFromNotificationOrInvite(targetIntent, ConnectActivity::class.java)
    }

    @Test
    fun `a notification for an opportunity past its intro opens it as the only screen in the task`() {
        PersonalIdUserPreferences.setLastAccessedOpportunityUuid(LAST_ACCESSED_UUID)
        ConnectTestUtils.storeJob(ConnectTestUtils.connectJob(jobUUID = TARGET_UUID))

        val targetIntent = ActivityAssertions.assertOnly(launchNotification(buildOpportunityIntent(TARGET_UUID)))

        assertOpenedFromNotificationOrInvite(targetIntent, ConnectActivity::class.java)
        assertTaskRoot(targetIntent)
    }

    @Test
    fun `a notification that opens the router opens it as the only screen in the task`() {
        val targetIntent = ActivityAssertions.assertOnly(launchNotification(Intent(context, DispatchActivity::class.java)))

        assertEquals(DispatchActivity::class.java.name, targetIntent.component?.className)
        assertFalse(targetIntent.hasExtra(ConnectConstants.OPENED_FROM_NOTIFICATION_OR_INVITE))
        assertTaskRoot(targetIntent)
    }

    @Test
    fun `the seeded home comes before the target so the target is shown on top`() {
        val stack = NotificationTaskStack.build(context, buildMessagingIntent())

        assertEquals(ConnectActivity::class.java.name, stack[0].component?.className)
        assertEquals(ConnectMessagingActivity::class.java.name, stack[1].component?.className)
    }

    @Test
    fun `a notification tapped after PersonalID was forgotten opens its target alone`() {
        PersonalIdSessionManager.getInstance().forgetUser("push notification launch activity test")

        val targetIntent = ActivityAssertions.assertOnly(launchNotification(buildMessagingIntent()))

        assertEquals(ConnectMessagingActivity::class.java.name, targetIntent.component?.className)
        assertTaskRoot(targetIntent)
    }

    private fun launchNotification(wrapped: Intent): List<Intent> {
        val launchIntent =
            Intent(context, PushNotificationLaunchActivity::class.java)
                .putExtra(PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT, wrapped)
        val activity = Robolectric.buildActivity(PushNotificationLaunchActivity::class.java, launchIntent).create().get()

        assertTrue(activity.isFinishing)
        return ActivityAssertions.startedIntents(activity)
    }

    private fun buildMessagingIntent(): Intent =
        Intent(context, ConnectMessagingActivity::class.java)
            .putExtra(ConnectConstants.REDIRECT_ACTION, ConnectConstants.CCC_MESSAGE)

    private fun buildOpportunityIntent(opportunityUuid: String): Intent =
        Intent(context, ConnectActivity::class.java)
            .putExtra(ConnectConstants.REDIRECT_ACTION, ConnectConstants.CCC_GENERIC_OPPORTUNITY)
            .putExtra(ConnectConstants.OPPORTUNITY_UUID, opportunityUuid)

    private fun assertOpportunityHomeAtTaskBottom(
        intent: Intent,
        opportunityUuid: String,
    ) {
        assertEquals(ConnectActivity::class.java.name, intent.component?.className)
        assertTrue(intent.getBooleanExtra(ConnectConstants.GO_TO_JOB_STATUS, false))
        assertEquals(opportunityUuid, intent.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
        assertTaskRoot(intent)
    }

    private fun assertOpportunityListAtTaskBottom(intent: Intent) {
        assertEquals(ConnectActivity::class.java.name, intent.component?.className)
        assertFalse(intent.hasExtra(ConnectConstants.REDIRECT_ACTION))
        assertFalse(intent.hasExtra(ConnectConstants.GO_TO_JOB_STATUS))
        assertTaskRoot(intent)
    }

    private fun assertOpenedFromNotificationOrInvite(
        intent: Intent,
        target: Class<*>,
    ) {
        assertEquals(target.name, intent.component?.className)
        assertTrue(intent.getBooleanExtra(ConnectConstants.OPENED_FROM_NOTIFICATION_OR_INVITE, false))
    }

    private fun assertTaskRoot(intent: Intent) {
        val taskRootFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(taskRootFlags, intent.flags and taskRootFlags)
    }

    private companion object {
        const val LAST_ACCESSED_UUID = "last-accessed-job-uuid"
        const val TARGET_UUID = "target-job-uuid"
    }
}
