package org.commcare.pn.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.utils.PushNotificationApiHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class NotificationsRetrievalWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(PushNotificationApiHelper)
    }

    @After
    fun tearDown() {
        unmockkObject(PushNotificationApiHelper)
    }

    @Test
    fun testSuccessfulNotificationRetrieval_shouldReturnSuccess() = runBlocking {
        coEvery { 
            PushNotificationApiHelper.retrieveLatestPushNotifications(any()) 
        } returns Result.success(emptyList<PushNotificationRecord>())

        val worker = TestListenableWorkerBuilder<NotificationsRetrievalWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testApiFailure_shouldReturnRetryOnFirstAttempt() = runBlocking {
        val exception = Exception("Network error")
        coEvery { 
            PushNotificationApiHelper.retrieveLatestPushNotifications(any()) 
        } returns Result.failure(exception)

        val worker = TestListenableWorkerBuilder<NotificationsRetrievalWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun testApiFailure_shouldReturnFailureAfterMaxRetries() = runBlocking {
        val exception = Exception("Network error")
        coEvery { 
            PushNotificationApiHelper.retrieveLatestPushNotifications(any()) 
        } returns Result.failure(exception)

        val worker = TestListenableWorkerBuilder<NotificationsRetrievalWorker>(context)
            .setRunAttemptCount(NotificationsRetrievalWorker.MAX_RETRIES)
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

}
