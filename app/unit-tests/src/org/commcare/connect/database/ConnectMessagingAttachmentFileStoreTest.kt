package org.commcare.connect.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingAttachmentFileStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ConnectMessagingAttachmentFileStore.deleteAll(context)
    }

    @Test
    fun `write stores the contents under the attachment id and leaves no partial file`() {
        val contents = byteArrayOf(1, 2, 3)

        ConnectMessagingAttachmentFileStore.write(context, "attachment-1", contents)

        val directory = ConnectMessagingAttachmentFileStore.directory(context)
        assertArrayEquals(contents, ConnectMessagingAttachmentFileStore.fileFor(context, "attachment-1").readBytes())
        assertFalse(File(directory, "attachment-1.part").exists())
    }

    @Test
    fun `write replaces an existing file for the same attachment`() {
        ConnectMessagingAttachmentFileStore.write(context, "attachment-1", byteArrayOf(1))

        ConnectMessagingAttachmentFileStore.write(context, "attachment-1", byteArrayOf(2, 2))

        assertArrayEquals(byteArrayOf(2, 2), ConnectMessagingAttachmentFileStore.fileFor(context, "attachment-1").readBytes())
    }

    @Test
    fun `deleteAll removes the attachment directory`() {
        ConnectMessagingAttachmentFileStore.write(context, "attachment-1", byteArrayOf(1))
        assertTrue(ConnectMessagingAttachmentFileStore.directory(context).exists())

        ConnectMessagingAttachmentFileStore.deleteAll(context)

        assertFalse(ConnectMessagingAttachmentFileStore.directory(context).exists())
    }
}
