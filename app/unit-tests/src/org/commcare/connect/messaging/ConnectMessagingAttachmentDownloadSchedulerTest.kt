package org.commcare.connect.messaging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.commcare.CommCareTestApplication
import org.commcare.preferences.ConnectMessagingPreferences
import org.commcare.preferences.ConnectMessagingPreferences.AttachmentAutoDownload
import org.commcare.utils.PushNotificationApiHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingAttachmentDownloadSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ConnectMessagingAttachmentDownloadScheduler.cancelDownloads(context)
        ConnectMessagingPreferences.clear(context)
    }

    @Test
    fun `by default one job downloads every queued attachment on any network`() {
        ConnectMessagingAttachmentDownloadScheduler.scheduleQueuedDownloads(context)

        assertEquals(NetworkType.CONNECTED, pendingNetworkType(ConnectMessagingAttachmentDownloadScheduler.ALL_FILES_WORK_NAME))
        assertTrue(pendingWork(ConnectMessagingAttachmentDownloadScheduler.LARGE_FILES_WORK_NAME).isEmpty())
    }

    @Test
    fun `large on wifi only holds large automatic downloads for wifi and keeps small ones on any network`() {
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.LARGE_ON_WIFI_ONLY)

        ConnectMessagingAttachmentDownloadScheduler.scheduleQueuedDownloads(context)

        assertEquals(NetworkType.CONNECTED, pendingNetworkType(ConnectMessagingAttachmentDownloadScheduler.SMALL_FILES_WORK_NAME))
        assertEquals(NetworkType.UNMETERED, pendingNetworkType(ConnectMessagingAttachmentDownloadScheduler.LARGE_FILES_WORK_NAME))
        assertTrue(pendingWork(ConnectMessagingAttachmentDownloadScheduler.ALL_FILES_WORK_NAME).isEmpty())
    }

    @Test
    fun `manual only schedules no automatic downloads`() {
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.MANUAL_ONLY)

        ConnectMessagingAttachmentDownloadScheduler.scheduleQueuedDownloads(context)

        assertTrue(automaticWork().isEmpty())
    }

    @Test
    fun `a tapped attachment is attempted right away whatever the setting or network`() {
        for (setting in AttachmentAutoDownload.values()) {
            ConnectMessagingPreferences.setAttachmentAutoDownload(context, setting)

            ConnectMessagingAttachmentDownloadScheduler.downloadNow(context, "attachment-${setting.value}")

            val work = latestWork(ConnectMessagingAttachmentDownloadScheduler.requestedWorkName("attachment-${setting.value}"))
            assertEquals(NetworkType.NOT_REQUIRED, work.constraints.requiredNetworkType)
            assertEquals(0L, work.initialDelayMillis)
        }
    }

    @Test
    fun `a failed tapped attachment is retried in the background once a network is available`() {
        ConnectMessagingAttachmentDownloadScheduler.retryRequestedDownloadLater(context, "attachment-1")

        val work = pendingWork(ConnectMessagingAttachmentDownloadScheduler.requestedRetryWorkName("attachment-1")).single()
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertEquals(TimeUnit.MINUTES.toMillis(PushNotificationApiHelper.SYNC_BACKOFF_DELAY_IN_MINS), work.initialDelayMillis)
    }

    @Test
    fun `tapping again replaces the pending background retry with an immediate attempt`() {
        ConnectMessagingAttachmentDownloadScheduler.retryRequestedDownloadLater(context, "attachment-1")

        ConnectMessagingAttachmentDownloadScheduler.downloadNow(context, "attachment-1")

        assertEquals(
            WorkInfo.State.CANCELLED,
            latestWork(ConnectMessagingAttachmentDownloadScheduler.requestedRetryWorkName("attachment-1")).state,
        )
        assertEquals(
            NetworkType.NOT_REQUIRED,
            latestWork(ConnectMessagingAttachmentDownloadScheduler.requestedWorkName("attachment-1")).constraints.requiredNetworkType,
        )
    }

    @Test
    fun `applying a new setting replaces the automatic jobs`() {
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.LARGE_ON_WIFI_ONLY)
        ConnectMessagingAttachmentDownloadScheduler.scheduleQueuedDownloads(context)

        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.ANY_NETWORK)
        ConnectMessagingAttachmentDownloadScheduler.applyAutoDownloadSetting(context)

        assertEquals(NetworkType.CONNECTED, pendingNetworkType(ConnectMessagingAttachmentDownloadScheduler.ALL_FILES_WORK_NAME))
        assertTrue(pendingWork(ConnectMessagingAttachmentDownloadScheduler.LARGE_FILES_WORK_NAME).isEmpty())
    }

    @Test
    fun `applying manual only cancels the automatic jobs`() {
        ConnectMessagingAttachmentDownloadScheduler.scheduleQueuedDownloads(context)

        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.MANUAL_ONLY)
        ConnectMessagingAttachmentDownloadScheduler.applyAutoDownloadSetting(context)

        assertTrue(automaticWork().isEmpty())
    }

    private fun automaticWork(): List<WorkInfo> =
        listOf(
            ConnectMessagingAttachmentDownloadScheduler.ALL_FILES_WORK_NAME,
            ConnectMessagingAttachmentDownloadScheduler.SMALL_FILES_WORK_NAME,
            ConnectMessagingAttachmentDownloadScheduler.LARGE_FILES_WORK_NAME,
        ).flatMap { pendingWork(it) }

    private fun pendingWork(workName: String): List<WorkInfo> =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(workName)
            .get()
            .filter { !it.state.isFinished }

    private fun latestWork(workName: String): WorkInfo =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(workName)
            .get()
            .single()

    private fun pendingNetworkType(workName: String): NetworkType =
        pendingWork(workName).single().constraints.requiredNetworkType
}
