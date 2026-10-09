package org.commcare.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.preferences.ConnectMessagingPreferences.AttachmentAutoDownload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ConnectMessagingPreferences.clear(context)
    }

    @Test
    fun `attachments download automatically on any network by default`() {
        assertEquals(AttachmentAutoDownload.ANY_NETWORK, ConnectMessagingPreferences.getAttachmentAutoDownload(context))
        assertTrue(ConnectMessagingPreferences.isAutomaticDownloadEnabled(context))
    }

    @Test
    fun `chosen setting is remembered`() {
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.LARGE_ON_WIFI_ONLY)

        assertEquals(AttachmentAutoDownload.LARGE_ON_WIFI_ONLY, ConnectMessagingPreferences.getAttachmentAutoDownload(context))
    }

    @Test
    fun `manual only turns automatic download off`() {
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.MANUAL_ONLY)

        assertFalse(ConnectMessagingPreferences.isAutomaticDownloadEnabled(context))
    }

    @Test
    fun `clearing returns to the default`() {
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.MANUAL_ONLY)

        ConnectMessagingPreferences.clear(context)

        assertEquals(AttachmentAutoDownload.ANY_NETWORK, ConnectMessagingPreferences.getAttachmentAutoDownload(context))
    }
}
