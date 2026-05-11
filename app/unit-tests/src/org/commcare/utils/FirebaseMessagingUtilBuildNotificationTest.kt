package org.commcare.utils

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.DispatchActivity
import org.commcare.activities.PushNotificationLaunchActivity
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ConnectConstants.NOTIFICATION_ID
import org.commcare.services.FCMMessageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FirebaseMessagingUtilBuildNotificationTest {

    @Test
    fun buildNotification_pendingIntentTargetsLaunchActivityAndWrapsOriginal() {
        val ctx = CommCareTestApplication.instance()
        val originalTarget = Intent(ctx, DispatchActivity::class.java)
            .putExtra("originalKey", "originalValue")

        val payload = HashMap<String, String>().apply {
            put(ConnectConstants.NOTIFICATION_TITLE, "title")
            put(ConnectConstants.NOTIFICATION_BODY, "body")
            put(NOTIFICATION_ID, "notif-123")
        }
        val fcm = FCMMessageData(payload)

        val builder = FirebaseMessagingUtil.buildNotificationForTest(ctx, originalTarget, fcm)
        val notification = builder.build()
        val contentIntent: PendingIntent = notification.contentIntent
        assertNotNull(contentIntent)

        val shadowPi = shadowOf(contentIntent)
        val savedIntent: Intent = shadowPi.savedIntent
        assertEquals(
            PushNotificationLaunchActivity::class.java.name,
            savedIntent.component?.className
        )
        val wrapped: Intent? = savedIntent.getParcelableExtra(
            PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT
        )
        assertNotNull(wrapped)
        assertEquals(DispatchActivity::class.java.name, wrapped!!.component?.className)
        assertEquals("originalValue", wrapped.getStringExtra("originalKey"))
        assertEquals("notif-123", wrapped.getStringExtra(NOTIFICATION_ID))
    }
}
