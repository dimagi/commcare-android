package org.commcare.connect.messaging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentFileStore
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingAttachmentDownloaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val encryptedVector = Base64.decode(TEST_VECTOR_BLOB)
    private val fetchedAttachmentIds = mutableListOf<String>()

    @Before
    fun setUp() {
        ConnectMessagingDatabaseHelper.storeMessagingChannel(
            context,
            ConnectMessagingChannelRecord().apply {
                channelId = CHANNEL_ID
                channelCreated = Date()
                consented = true
                channelSource = ""
                keyUrl = ""
                key = TEST_VECTOR_KEY
                channelName = ""
            },
        )
    }

    @After
    fun tearDown() {
        ConnectMessagingAttachmentFileStore.deleteAll(context)
        ConnectDatabaseHelper.teardown()
    }

    @Test
    fun `downloaded attachment is decrypted to disk and becomes available`() {
        storeQueuedAttachment("attachment-1", "message-1")

        val result = runPassWith { _, _, _ -> ok(encryptedVector) }

        assertEquals(ConnectMessagingAttachmentDownloader.PassResult.COMPLETE, result)
        assertEquals(ConnectMessagingAttachmentState.AVAILABLE, stateOf("attachment-1"))
        assertEquals(
            "PersonalID attachment test vector",
            ConnectMessagingAttachmentFileStore.fileFor(context, "attachment-1").readText(),
        )
    }

    @Test
    fun `size mismatch shows as failed straight away and asks for a retry`() {
        storeQueuedAttachment("attachment-1", "message-1")

        val result = runPassWith { _, _, _ -> ok(encryptedVector.copyOf(TEST_VECTOR_SIZE - 1)) }

        assertEquals(ConnectMessagingAttachmentDownloader.PassResult.RETRY_LATER, result)
        assertEquals(ConnectMessagingAttachmentState.FAILED, stateOf("attachment-1"))
        assertEquals(1, attemptsOf("attachment-1"))
    }

    @Test
    fun `failed attachment with attempts left is retried by a later pass`() {
        storeQueuedAttachment("attachment-1", "message-1", attempts = 1, state = ConnectMessagingAttachmentState.FAILED)

        runPassWith { _, _, _ -> ok(encryptedVector) }

        assertEquals(ConnectMessagingAttachmentState.AVAILABLE, stateOf("attachment-1"))
    }

    @Test
    fun `failed attachment with no attempts left waits for a tap`() {
        storeQueuedAttachment("attachment-1", "message-1", attempts = 3, state = ConnectMessagingAttachmentState.FAILED)

        runPassWith { _, _, _ -> ok(encryptedVector) }

        assertEquals(emptyList<String>(), fetchedAttachmentIds)
        assertEquals(ConnectMessagingAttachmentState.FAILED, stateOf("attachment-1"))
    }

    @Test
    fun `third failed attempt marks the attachment failed`() {
        storeQueuedAttachment("attachment-1", "message-1", attempts = 2)

        val result = runPassWith { _, _, _ -> throw IOException("connection reset") }

        assertEquals(ConnectMessagingAttachmentDownloader.PassResult.COMPLETE, result)
        assertEquals(ConnectMessagingAttachmentState.FAILED, stateOf("attachment-1"))
        assertFalse(ConnectMessagingAttachmentFileStore.fileFor(context, "attachment-1").exists())
    }

    @Test
    fun `tampered bytes count as a failed attempt and write nothing`() {
        storeQueuedAttachment("attachment-1", "message-1")
        val tampered = encryptedVector.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }

        runPassWith { _, _, _ -> ok(tampered) }

        assertEquals(1, attemptsOf("attachment-1"))
        assertFalse(ConnectMessagingAttachmentFileStore.fileFor(context, "attachment-1").exists())
    }

    @Test
    fun `server reporting the message gone marks the attachment expired`() {
        storeQueuedAttachment("attachment-1", "message-1")

        runPassWith { _, _, _ -> ConnectMessagingAttachmentFetchResponse(410, null) }

        assertEquals(ConnectMessagingAttachmentState.EXPIRED, stateOf("attachment-1"))
    }

    @Test
    fun `auth failure stops the pass and fails the attachment without counting an attempt`() {
        storeQueuedAttachment("attachment-1", "message-1", messageMinutesAgo = 2)
        storeQueuedAttachment("attachment-2", "message-2", messageMinutesAgo = 1)

        val result = runPassWith { _, _, _ -> ConnectMessagingAttachmentFetchResponse(401, null) }

        assertEquals(ConnectMessagingAttachmentDownloader.PassResult.STOPPED_BY_AUTH_FAILURE, result)
        assertEquals(listOf("attachment-1"), fetchedAttachmentIds)
        assertEquals(ConnectMessagingAttachmentState.FAILED, stateOf("attachment-1"))
        assertEquals(0, attemptsOf("attachment-1"))
        assertEquals(ConnectMessagingAttachmentState.QUEUED, stateOf("attachment-2"))
    }

    @Test
    fun `attachment left downloading by a stopped job is downloaded again`() {
        storeQueuedAttachment("attachment-1", "message-1", state = ConnectMessagingAttachmentState.DOWNLOADING)

        runPassWith { _, _, _ -> ok(encryptedVector) }

        assertEquals(ConnectMessagingAttachmentState.AVAILABLE, stateOf("attachment-1"))
    }

    @Test
    fun `attachments of older messages download first and waiting ones are left alone`() {
        storeQueuedAttachment("newer", "message-newer", messageMinutesAgo = 1)
        storeQueuedAttachment("older", "message-older", messageMinutesAgo = 5)
        storeQueuedAttachment("tap-only", "message-tap", messageMinutesAgo = 9, state = ConnectMessagingAttachmentState.WAITING)

        runPassWith { _, _, _ -> ok(encryptedVector) }

        assertEquals(listOf("older", "newer"), fetchedAttachmentIds)
        assertEquals(ConnectMessagingAttachmentState.WAITING, stateOf("tap-only"))
    }

    @Test
    fun `tapped attachments download ahead of older automatic ones`() {
        storeQueuedAttachment("older", "message-older", messageMinutesAgo = 5)
        storeQueuedAttachment("tapped", "message-tapped", messageMinutesAgo = 1, state = ConnectMessagingAttachmentState.REQUESTED)

        runPassWith { _, _, _ -> ok(encryptedVector) }

        assertEquals(listOf("tapped", "older"), fetchedAttachmentIds)
    }

    @Test
    fun `failed tapped download shows as failed straight away`() {
        storeQueuedAttachment("attachment-1", "message-1", state = ConnectMessagingAttachmentState.REQUESTED)

        val result = runPassWith(requestedAttachmentId = "attachment-1") { _, _, _ -> throw IOException("connection refused") }

        assertEquals(ConnectMessagingAttachmentDownloader.PassResult.RETRY_LATER, result)
        assertEquals(ConnectMessagingAttachmentState.FAILED, stateOf("attachment-1"))
    }

    @Test
    fun `tapped download only fetches the tapped attachment and marks other interrupted ones failed`() {
        storeQueuedAttachment("tapped", "message-1", state = ConnectMessagingAttachmentState.REQUESTED)
        storeQueuedAttachment("other", "message-2", state = ConnectMessagingAttachmentState.DOWNLOADING)

        runPassWith(requestedAttachmentId = "tapped") { _, _, _ -> ok(encryptedVector) }

        assertEquals(listOf("tapped"), fetchedAttachmentIds)
        assertEquals(ConnectMessagingAttachmentState.AVAILABLE, stateOf("tapped"))
        assertEquals(ConnectMessagingAttachmentState.FAILED, stateOf("other"))
    }

    private fun runPassWith(
        requestedAttachmentId: String? = null,
        fetcher: ConnectMessagingAttachmentFetcher,
    ): ConnectMessagingAttachmentDownloader.PassResult {
        val recordingFetcher =
            ConnectMessagingAttachmentFetcher { messageId, attachmentId, expectedSize ->
                fetchedAttachmentIds.add(attachmentId)
                fetcher.fetch(messageId, attachmentId, expectedSize)
            }
        val downloader = ConnectMessagingAttachmentDownloader(context, recordingFetcher)
        return downloader.runPass({ requestedAttachmentId == null || it.attachmentId == requestedAttachmentId }) { false }
    }

    private fun ok(body: ByteArray) = ConnectMessagingAttachmentFetchResponse(200, body)

    private fun storeQueuedAttachment(
        attachmentId: String,
        messageId: String,
        messageMinutesAgo: Long = 1,
        attempts: Int = 0,
        state: ConnectMessagingAttachmentState = ConnectMessagingAttachmentState.QUEUED,
    ) {
        ConnectMessagingDatabaseHelper.storeMessagingMessage(
            context,
            ConnectMessagingMessageRecord().apply {
                this.messageId = messageId
                channelId = CHANNEL_ID
                timeStamp = Date(System.currentTimeMillis() - messageMinutesAgo * 60_000)
                message = "Hello"
            },
        )
        ConnectMessagingAttachmentDatabaseHelper.save(
            ConnectMessagingAttachmentRecord().apply {
                this.attachmentId = attachmentId
                this.messageId = messageId
                name = "file"
                type = "image/png"
                size = TEST_VECTOR_SIZE.toLong()
                downloadState = state
                this.attempts = attempts
            },
        )
    }

    private fun stateOf(attachmentId: String) = ConnectMessagingAttachmentDatabaseHelper.getAttachment(attachmentId)!!.downloadState

    private fun attemptsOf(attachmentId: String) = ConnectMessagingAttachmentDatabaseHelper.getAttachment(attachmentId)!!.attempts

    private companion object {
        const val CHANNEL_ID = "channel-1"
        const val TEST_VECTOR_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        const val TEST_VECTOR_BLOB = "oKGio6Slpqeoqaqrtn0OXiqlY9MrIaeycw6hvRjBPH7mlzYJ73oG8BrIAW6gb+xqABsBM0cg1w0oetetHQ=="
        const val TEST_VECTOR_SIZE = 61
    }
}
