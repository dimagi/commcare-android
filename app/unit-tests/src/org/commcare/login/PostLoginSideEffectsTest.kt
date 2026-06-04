package org.commcare.login

import android.content.Context
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
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectAppUtils
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.utils.CrashUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PostLoginSideEffectsTest {
    private val context = mockk<Context>(relaxed = true)
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
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.updateLastAccessed(any(), any(), any()) } returns Unit
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

            val outcome = PostLoginSideEffects(context, personalIdManager).runOnSuccess("alice")

            assertEquals(PostLoginOutcome(redirectToConnectOpportunityInfo = false), outcome)
            verify { CrashUtil.registerUserData() }
            verify { notificationManager.clearNotifications(any()) }
            verify(exactly = 0) { ConnectJobUtils.getJobForApp(any(), any()) }
        }

    @Test
    fun `personalID logged in with no job does not update app access and flags link check`() =
        runTest {
            every { personalIdManager.isloggedIn() } returns true
            every { ConnectJobUtils.getJobForApp(context, "app-1") } returns null

            val outcome = PostLoginSideEffects(context, personalIdManager).runOnSuccess("alice")

            assertEquals(
                PostLoginOutcome(redirectToConnectOpportunityInfo = false, needsPersonalIdLinkCheck = true),
                outcome,
            )
            verify { commCareApplication.setConnectJobIdForAnalytics(null) }
            verify(exactly = 0) { ConnectAppUtils.updateLastAccessed(any(), any(), any()) }
            verify(exactly = 0) { ConnectJobHelper.updateJobProgress(any(), any(), any()) }
        }

    @Test
    fun `personalID logged in with job updates app access and job progress`() =
        runTest {
            every { personalIdManager.isloggedIn() } returns true
            val job = mockk<ConnectJobRecord>(relaxed = true)
            every { ConnectJobUtils.getJobForApp(context, "app-1") } returns job

            val listenerSlot = slot<ConnectActivityCompleteListener>()
            every {
                ConnectJobHelper.updateJobProgress(context, job, capture(listenerSlot))
            } answers {
                listenerSlot.captured.connectActivityComplete(true, "")
            }

            PostLoginSideEffects(context, personalIdManager).runOnSuccess("alice")

            verify { commCareApplication.setConnectJobIdForAnalytics(job) }
            verify { ConnectAppUtils.updateLastAccessed(context, "app-1", "alice") }
            verify { ConnectJobHelper.updateJobProgress(context, job, any()) }
        }
}
