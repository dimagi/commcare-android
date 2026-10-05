package org.commcare.fragments.connectMessaging

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
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
                override fun onAttachmentDownloadRequested(attachmentId: String) {
                    requestedDownloads.add(attachmentId)
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
    fun `downloaded file of another type shows a chip that opens it`() {
        binder.bind(listOf(available("doc", "application/pdf")), MAX_WIDTH, MAX_HEIGHT)

        val chip = ViewConnectMessageAttachmentFileBinding.bind(container.getChildAt(0))
        assertEquals("doc.bin", chip.tvFileName.text.toString())
        chip.root.performClick()
        assertEquals(listOf("doc"), openedAttachments)
    }

    @Test
    fun `waiting attachment shows a download tile that requests the download`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.WAITING)

        assertEquals(context.getString(R.string.connect_messaging_attachment_download), tile.tvLabel.text.toString())
        assertTrue(tile.tvNameAndSize.text.startsWith("pending.bin"))
        tile.root.performClick()
        assertEquals(listOf("pending"), requestedDownloads)
    }

    @Test
    fun `failed attachment offers a retry`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.FAILED)

        assertEquals(context.getString(R.string.connect_messaging_attachment_download_failed), tile.tvLabel.text.toString())
        assertEquals(View.VISIBLE, tile.tvSecondaryLabel.visibility)
        tile.root.performClick()
        assertEquals(listOf("pending"), requestedDownloads)
    }

    @Test
    fun `downloading attachment shows progress and ignores taps`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.DOWNLOADING)

        assertEquals(View.VISIBLE, tile.progress.visibility)
        assertEquals(View.GONE, tile.ivAction.visibility)
        assertFalse(tile.root.hasOnClickListeners())
    }

    @Test
    fun `expired attachment says it is no longer available and ignores taps`() {
        val tile = bindPendingTile(ConnectMessagingAttachmentState.EXPIRED)

        assertEquals(context.getString(R.string.connect_messaging_attachment_expired), tile.tvLabel.text.toString())
        assertEquals(View.GONE, tile.ivAction.visibility)
        assertFalse(tile.root.hasOnClickListeners())
    }

    private fun bindPendingTile(state: ConnectMessagingAttachmentState): ViewConnectMessageAttachmentPendingBinding {
        binder.bind(
            listOf(ConnectMessageAttachmentItem("pending", "pending.bin", "image/png", 2048, state, null)),
            MAX_WIDTH,
            MAX_HEIGHT,
        )
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

    private companion object {
        const val MAX_WIDTH = 344
        const val MAX_HEIGHT = 361
    }
}
