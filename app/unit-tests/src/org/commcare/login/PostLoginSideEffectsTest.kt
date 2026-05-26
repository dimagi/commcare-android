package org.commcare.login

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.commcare.CommCareApp
import org.commcare.CommCareApplication
import org.commcare.CommCareNoficationManager
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.utils.CrashUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PostLoginSideEffectsTest {
    private val activity = mockk<CommCareActivity<Any>>(relaxed = true)
    private val personalIdManager = mockk<PersonalIdManager>(relaxed = true)
    private val notificationManager = mockk<CommCareNoficationManager>(relaxed = true)
    private val commCareApplication = mockk<CommCareApplication>(relaxed = true)
    private val currentApp = mockk<CommCareApp>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(CommCareApplication::class)
        mockkStatic(CrashUtil::class)
        mockkStatic(ConnectJobUtils::class)
        mockkObject(ConnectJobHelper)
        every { CommCareApplication.notificationManager() } returns notificationManager
        every { CommCareApplication.instance() } returns commCareApplication
        every { commCareApplication.currentApp } returns currentApp
        every { currentApp.uniqueId } returns "app-1"
        every { CrashUtil.registerUserData() } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `not logged into PersonalID runs only basic side effects and returns false`() =
        runTest {
            every { personalIdManager.isloggedIn() } returns false

            val outcome = PostLoginSideEffects(personalIdManager).runOnSuccess(activity, "alice")

            assertEquals(PostLoginOutcome(redirectToConnectOpportunityInfo = false), outcome)
            verify { CrashUtil.registerUserData() }
            verify { notificationManager.clearNotifications(any()) }
            verify(exactly = 0) { ConnectJobUtils.getJobForApp(any(), any()) }
        }

    @Test
    fun `personalID logged in with no job sets analytics null and returns false`() =
        runTest {
            every { personalIdManager.isloggedIn() } returns true
            every { ConnectJobUtils.getJobForApp(activity, "app-1") } returns null

            val outcome = PostLoginSideEffects(personalIdManager).runOnSuccess(activity, "alice")

            assertEquals(PostLoginOutcome(redirectToConnectOpportunityInfo = false), outcome)
            verify { commCareApplication.setConnectJobIdForAnalytics(null) }
            verify(exactly = 0) { personalIdManager.updateAppAccess(any(), any(), any()) }
            verify(exactly = 0) { ConnectJobHelper.updateJobProgress(any(), any(), any()) }
        }

    @Test
    fun `personalID logged in with job and updateJobProgress success and user suspended returns true`() =
        runTest {
            every { personalIdManager.isloggedIn() } returns true
            val job = mockk<ConnectJobRecord>(relaxed = true)
            every { job.isUserSuspended } returns true
            every { ConnectJobUtils.getJobForApp(activity, "app-1") } returns job

            val listenerSlot = slot<ConnectActivityCompleteListener>()
            every { ConnectJobHelper.updateJobProgress(activity, job, capture(listenerSlot)) } answers {
                listenerSlot.captured.connectActivityComplete(true, "")
            }

            val outcome = PostLoginSideEffects(personalIdManager).runOnSuccess(activity, "alice")

            assertEquals(PostLoginOutcome(redirectToConnectOpportunityInfo = true), outcome)
            verify { commCareApplication.setConnectJobIdForAnalytics(job) }
            verify { personalIdManager.updateAppAccess(activity, "app-1", "alice") }
        }

    @Test
    fun `personalID logged in with job and updateJobProgress success and user not suspended returns false`() =
        runTest {
            every { personalIdManager.isloggedIn() } returns true
            val job = mockk<ConnectJobRecord>(relaxed = true)
            every { job.isUserSuspended } returns false
            every { ConnectJobUtils.getJobForApp(activity, "app-1") } returns job

            val listenerSlot = slot<ConnectActivityCompleteListener>()
            every { ConnectJobHelper.updateJobProgress(activity, job, capture(listenerSlot)) } answers {
                listenerSlot.captured.connectActivityComplete(true, "")
            }

            val outcome = PostLoginSideEffects(personalIdManager).runOnSuccess(activity, "alice")

            assertEquals(PostLoginOutcome(redirectToConnectOpportunityInfo = false), outcome)
        }

    @Test
    fun `updateJobProgress callback returns success false returns false even when suspended`() =
        runTest {
            every { personalIdManager.isloggedIn() } returns true
            val job = mockk<ConnectJobRecord>(relaxed = true)
            every { job.isUserSuspended } returns true
            every { ConnectJobUtils.getJobForApp(activity, "app-1") } returns job

            val listenerSlot = slot<ConnectActivityCompleteListener>()
            every { ConnectJobHelper.updateJobProgress(activity, job, capture(listenerSlot)) } answers {
                listenerSlot.captured.connectActivityComplete(false, "network failure")
            }

            val outcome = PostLoginSideEffects(personalIdManager).runOnSuccess(activity, "alice")

            assertEquals(PostLoginOutcome(redirectToConnectOpportunityInfo = false), outcome)
        }
}
