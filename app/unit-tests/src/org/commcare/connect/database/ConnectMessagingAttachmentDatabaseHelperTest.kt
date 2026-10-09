package org.commcare.connect.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.TimeUnit

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingAttachmentDatabaseHelperTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Date()

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
    }

    @Test
    fun `attachment on a recent message is queued for download`() {
        storeIncomingMessage("message-1", daysAgo = 1)

        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(listOf(attachment("attachment-1", "message-1")), true, now)

        assertEquals(ConnectMessagingAttachmentState.QUEUED, stateOf("attachment-1"))
    }

    @Test
    fun `attachment on an old message waits for a tap`() {
        storeIncomingMessage("message-1", daysAgo = 10)

        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(listOf(attachment("attachment-1", "message-1")), true, now)

        assertEquals(ConnectMessagingAttachmentState.WAITING, stateOf("attachment-1"))
    }

    @Test
    fun `redelivered attachment keeps its existing state and attempts`() {
        storeIncomingMessage("message-1", daysAgo = 1)
        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(listOf(attachment("attachment-1", "message-1")), true, now)
        val stored = ConnectMessagingAttachmentDatabaseHelper.getAttachment("attachment-1")!!
        stored.downloadState = ConnectMessagingAttachmentState.AVAILABLE
        stored.attempts = 2
        ConnectDatabaseHelper.getConnectStorage(ConnectMessagingAttachmentRecord::class.java).write(stored)

        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(listOf(attachment("attachment-1", "message-1")), true, now)

        val attachments = attachmentsOf("message-1")
        assertEquals(1, attachments.size)
        assertEquals(ConnectMessagingAttachmentState.AVAILABLE, attachments[0].downloadState)
        assertEquals(2, attachments[0].attempts)
    }

    @Test
    fun `attachments for a message come back in sender order`() {
        storeIncomingMessage("message-1", daysAgo = 1)

        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(
            listOf(attachment("attachment-b", "message-1", position = 1), attachment("attachment-a", "message-1", position = 0)),
            true,
            now,
        )

        assertEquals(
            listOf("attachment-a", "attachment-b"),
            attachmentsOf("message-1").map { it.attachmentId },
        )
    }

    @Test
    fun `attachment on a recent message waits for a tap when automatic download is off`() {
        storeIncomingMessage("message-1", daysAgo = 1)

        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(listOf(attachment("attachment-1", "message-1")), false, now)

        assertEquals(ConnectMessagingAttachmentState.WAITING, stateOf("attachment-1"))
    }

    @Test
    fun `pending downloads go back to waiting and finished ones are kept`() {
        storeIncomingMessage("message-1", daysAgo = 1)
        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(
            listOf(
                attachment("queued", "message-1", position = 0),
                attachment("downloading", "message-1", position = 1),
                attachment("available", "message-1", position = 2),
            ),
            true,
            now,
        )
        setState("downloading", ConnectMessagingAttachmentState.DOWNLOADING)
        setState("available", ConnectMessagingAttachmentState.AVAILABLE)

        ConnectMessagingAttachmentDatabaseHelper.returnPendingAttachmentsToWaiting()

        assertEquals(ConnectMessagingAttachmentState.WAITING, stateOf("queued"))
        assertEquals(ConnectMessagingAttachmentState.WAITING, stateOf("downloading"))
        assertEquals(ConnectMessagingAttachmentState.AVAILABLE, stateOf("available"))
    }

    @Test
    fun `requeueing a message requests its unfinished attachments and keeps finished ones`() {
        storeIncomingMessage("message-1", daysAgo = 10)
        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(
            listOf(
                attachment("waiting", "message-1", position = 0),
                attachment("queued", "message-1", position = 1),
                attachment("failed", "message-1", position = 2),
                attachment("available", "message-1", position = 3),
            ),
            true,
            now,
        )
        setState("queued", ConnectMessagingAttachmentState.QUEUED)
        setState("failed", ConnectMessagingAttachmentState.FAILED)
        setState("available", ConnectMessagingAttachmentState.AVAILABLE)

        val requeued = ConnectMessagingAttachmentDatabaseHelper.requeueMessage("message-1")

        assertEquals(listOf("waiting", "queued", "failed"), requeued)
        assertEquals(ConnectMessagingAttachmentState.REQUESTED, stateOf("waiting"))
        assertEquals(ConnectMessagingAttachmentState.REQUESTED, stateOf("queued"))
        assertEquals(ConnectMessagingAttachmentState.REQUESTED, stateOf("failed"))
        assertEquals(ConnectMessagingAttachmentState.AVAILABLE, stateOf("available"))
    }

    @Test
    fun `requeueing an expired message does nothing`() {
        storeIncomingMessage("message-1", daysAgo = 10)
        ConnectMessagingAttachmentDatabaseHelper.storeNewAttachments(
            listOf(attachment("waiting", "message-1", position = 0), attachment("expired", "message-1", position = 1)),
            true,
            now,
        )
        setState("expired", ConnectMessagingAttachmentState.EXPIRED)

        val requeued = ConnectMessagingAttachmentDatabaseHelper.requeueMessage("message-1")

        assertEquals(emptyList<String>(), requeued)
        assertEquals(ConnectMessagingAttachmentState.WAITING, stateOf("waiting"))
    }

    private fun setState(
        attachmentId: String,
        state: ConnectMessagingAttachmentState,
    ) {
        val attachment = ConnectMessagingAttachmentDatabaseHelper.getAttachment(attachmentId)!!
        attachment.downloadState = state
        ConnectMessagingAttachmentDatabaseHelper.save(attachment)
    }

    private fun storeIncomingMessage(
        messageId: String,
        daysAgo: Long,
    ) {
        val message =
            ConnectMessagingMessageRecord().apply {
                this.messageId = messageId
                channelId = "channel-1"
                timeStamp = Date(now.time - TimeUnit.DAYS.toMillis(daysAgo))
                this.message = "Hello"
                isOutgoing = false
            }
        ConnectMessagingDatabaseHelper.storeMessagingMessage(context, message)
    }

    private fun attachment(
        attachmentId: String,
        messageId: String,
        position: Int = 0,
    ) = ConnectMessagingAttachmentRecord().apply {
        this.attachmentId = attachmentId
        this.messageId = messageId
        this.position = position
        name = "file-$attachmentId"
        type = "image/png"
        size = 100
    }

    private fun stateOf(attachmentId: String) = ConnectMessagingAttachmentDatabaseHelper.getAttachment(attachmentId)!!.downloadState

    private fun attachmentsOf(messageId: String) = ConnectMessagingAttachmentDatabaseHelper.getAttachmentsByMessageId().getValue(messageId)
}
