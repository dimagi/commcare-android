package org.commcare.fragments.connectMessaging

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentFileBinding
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentPendingBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessageAttachmentsBinderTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.ConnectTheme)
    private val container = LinearLayout(context)
    private val requestedDownloads = mutableListOf<String>()
    private val openedAttachments = mutableListOf<String>()
    private val binder =
        ConnectMessageAttachmentsBinder(
            container,
            object : ConnectMessageAttachmentListener {
                override fun onMessageDownloadRequested(messageId: String) {
                    requestedDownloads.add(messageId)
                }

                override fun onAttachmentOpenRequested(attachment: ConnectMessageAttachmentItem) {
                    openedAttachments.add(attachment.attachmentId)
                }
            },
        )

    @Test
    fun `no attachments hides the attachment block`() {
        binder.bind(emptyList(), MAX_WIDTH, MAX_HEIGHT)

        assertEquals(View.GONE, container.visibility)
    }

    @Test
    fun `downloaded audio shows the inline player and fills the bubble`() {
        val layout = binder.bind(listOf(available("audio", "audio/mpeg")), MAX_WIDTH, MAX_HEIGHT)

        assertTrue(container.getChildAt(0) is ConnectMessageAudioAttachmentView)
        assertTrue(layout.fillsBubbleWidth)
    }

    @Test
    fun `phone photo stored sideways is sized by its upright orientation`() {
        val photo = image("sideways-photo", storedWidth = 400, storedHeight = 300, ExifInterface.ORIENTATION_ROTATE_90)

        binder.bind(listOf(photo), MAX_WIDTH, MAX_HEIGHT)

        val size = container.getChildAt(0).layoutParams
        assertTrue("expected portrait, got ${size.width}x${size.height}", size.height > size.width)
    }

    @Test
    fun `photo without rotation keeps its stored orientation`() {
        val photo = image("upright-photo", storedWidth = 400, storedHeight = 300)

        binder.bind(listOf(photo), MAX_WIDTH, MAX_HEIGHT)

        val size = container.getChildAt(0).layoutParams
        assertTrue("expected landscape, got ${size.width}x${size.height}", size.width > size.height)
    }

    @Test
    fun `image large enough to see opens the viewer when tapped`() {
        val photo = image("tappable-photo", storedWidth = 400, storedHeight = 300)

        binder.bind(listOf(photo), MAX_WIDTH, MAX_HEIGHT)
        container.getChildAt(0).performClick()

        assertEquals(listOf("tappable-photo"), openedAttachments)
    }

    @Test
    fun `image smaller than the minimum size is shown at that size but cannot be opened`() {
        val minimumSize = context.resources.getDimensionPixelSize(R.dimen.connect_touch_target_min)
        val icon = image("tiny-icon", storedWidth = 16, storedHeight = 16)

        binder.bind(listOf(icon), MAX_WIDTH, MAX_HEIGHT)

        val view = container.getChildAt(0)
        assertEquals(minimumSize, view.layoutParams.width)
        assertFalse(view.hasOnClickListeners())
    }

    @Test
    fun `downloaded file of another type shows a chip that opens it`() {
        binder.bind(listOf(available("doc", "application/pdf")), MAX_WIDTH, MAX_HEIGHT)

        val chip = ViewConnectMessageAttachmentFileBinding.bind(container.getChildAt(0))
        assertEquals("doc.bin", chip.tvFileName.text.toString())
        chip.root.performClick()
        assertEquals(listOf("doc"), openedAttachments)
    }

    @Test
    fun `message waiting for download shows one download tile that requests the whole message`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.WAITING)

        assertEquals(context.getString(R.string.connect_messaging_attachment_download), tile.tvLabel.text.toString())
        assertEquals(1, container.childCount)
        tile.root.performClick()
        assertEquals(listOf("message-1"), requestedDownloads)
    }

    @Test
    fun `message queued for automatic download shows the download tile until a download starts`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.QUEUED)

        assertEquals(View.GONE, tile.progress.visibility)
        assertEquals(context.getString(R.string.connect_messaging_attachment_download), tile.tvLabel.text.toString())
        tile.root.performClick()
        assertEquals(listOf("message-1"), requestedDownloads)
    }

    @Test
    fun `failed message download offers a retry`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.FAILED)

        assertEquals(context.getString(R.string.connect_messaging_attachment_download_failed), tile.tvLabel.text.toString())
        assertEquals(View.VISIBLE, tile.tvSecondaryLabel.visibility)
        tile.root.performClick()
        assertEquals(listOf("message-1"), requestedDownloads)
    }

    @Test
    fun `downloading or just tapped message shows progress and ignores taps`() {
        for (state in listOf(ConnectMessagingAttachmentState.DOWNLOADING, ConnectMessagingAttachmentState.REQUESTED)) {
            val tile = bindPendingTile(state)

            assertEquals(View.VISIBLE, tile.progress.visibility)
            assertEquals(View.GONE, tile.ivAction.visibility)
            assertFalse(tile.root.hasOnClickListeners())
        }
    }

    @Test
    fun `expired message says it is no longer available and ignores taps`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.EXPIRED)

        assertEquals(context.getString(R.string.connect_messaging_attachment_expired), tile.tvLabel.text.toString())
        assertEquals(View.GONE, tile.ivAction.visibility)
        assertFalse(tile.root.hasOnClickListeners())
    }

    @Test
    fun `message too new for this app shows an update placeholder with no action`() {
        binder.bindUnsupportedMessage()
        val tile = ViewConnectMessageAttachmentPendingBinding.bind(container.getChildAt(0))

        assertEquals(context.getString(R.string.connect_messaging_update_app_notice), tile.tvLabel.text.toString())
        assertEquals(View.GONE, tile.flAction.visibility)
        assertFalse(tile.root.hasOnClickListeners())
    }

    private fun bindPendingTile(state: ConnectMessagingAttachmentState): ViewConnectMessageAttachmentPendingBinding {
        binder.bindPendingMessage("message-1", state)
        return ViewConnectMessageAttachmentPendingBinding.bind(container.getChildAt(0))
    }

    private fun available(
        attachmentId: String,
        mimeType: String,
    ): ConnectMessageAttachmentItem {
        val file = File(context.cacheDir, attachmentId).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        return ConnectMessageAttachmentItem(
            attachmentId,
            "$attachmentId.bin",
            mimeType,
            3,
            ConnectMessagingAttachmentState.AVAILABLE,
            file,
        )
    }

    private fun image(
        attachmentId: String,
        storedWidth: Int,
        storedHeight: Int,
        exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL,
    ): ConnectMessageAttachmentItem {
        val file =
            ConnectMessageTestImages.writePng(File(context.cacheDir, attachmentId), storedWidth, storedHeight, exifOrientation)
        return ConnectMessageAttachmentItem(
            attachmentId,
            "$attachmentId.png",
            "image/png",
            file.length(),
            ConnectMessagingAttachmentState.AVAILABLE,
            file,
        )
    }

    private companion object {
        const val MAX_WIDTH = 344
        const val MAX_HEIGHT = 361
    }
}
