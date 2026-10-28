package org.commcare.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_REQUIRE_APP_SYNC
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_SESSION_ENDPOINT_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_BODY
import org.commcare.connect.ConnectConstants.NOTIFICATION_CHANNEL_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_KEY
import org.commcare.connect.ConnectConstants.NOTIFICATION_MESSAGE_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_STATUS
import org.commcare.connect.ConnectConstants.NOTIFICATION_TIME_STAMP
import org.commcare.connect.ConnectConstants.NOTIFICATION_TITLE
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS
import org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID
import org.commcare.connect.ConnectConstants.PAYMENT_ID
import org.commcare.connect.ConnectConstants.PAYMENT_UUID
import org.commcare.connect.ConnectConstants.REDIRECT_ACTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PushNotificationApiHelperTest {
    private fun buildRecord(): PushNotificationRecord =
        PushNotificationRecord().apply {
            action = "ccc_opportunity_summary_page"
            title = "Test Title"
            body = "Test Body"
            notificationId = "notif-123"
            createdDate = Date(0L)
            confirmationStatus = "pending"
            connectMessageId = "msg-456"
            channel = "channel-789"
            opportunityId = "opp-001"
            opportunityUUID = "opp-uuid-001"
            paymentUUID = "pay-uuid-001"
            paymentId = "pay-001"
            key = "key-value"
            opportunityStatus = "learn"
            sessionEndpointId = "endpoint-001"
            requireAppSync = false
        }

    @Test
    fun `convertPNRecordToPayload maps all record fields to the correct payload keys`() {
        val record = buildRecord()
        val payload = PushNotificationApiHelper.convertPNRecordToPayload(record)

        assertEquals(record.action, payload[REDIRECT_ACTION])
        assertEquals(record.title, payload[NOTIFICATION_TITLE])
        assertEquals(record.body, payload[NOTIFICATION_BODY])
        assertEquals(record.notificationId, payload[NOTIFICATION_ID])
        assertEquals(record.createdDate.toString(), payload[NOTIFICATION_TIME_STAMP])
        assertEquals(record.confirmationStatus, payload[NOTIFICATION_STATUS])
        assertEquals(record.connectMessageId, payload[NOTIFICATION_MESSAGE_ID])
        assertEquals(record.channel, payload[NOTIFICATION_CHANNEL_ID])
        assertEquals(record.opportunityId, payload[OPPORTUNITY_ID])
        assertEquals(record.opportunityUUID, payload[OPPORTUNITY_UUID])
        assertEquals(record.paymentUUID, payload[PAYMENT_UUID])
        assertEquals(record.paymentId, payload[PAYMENT_ID])
        assertEquals(record.key, payload[NOTIFICATION_KEY])
        assertEquals(record.opportunityStatus, payload[OPPORTUNITY_STATUS])
        assertEquals(record.sessionEndpointId, payload[META_SESSION_ENDPOINT_ID])
        assertEquals(record.requireAppSync.toString(), payload[META_REQUIRE_APP_SYNC])
    }

    @Test
    fun `convertPNRecordToPayload returns a payload containing exactly the expected keys`() {
        val payload = PushNotificationApiHelper.convertPNRecordToPayload(buildRecord())
        val expectedKeys =
            setOf(
                REDIRECT_ACTION,
                NOTIFICATION_TITLE,
                NOTIFICATION_BODY,
                NOTIFICATION_ID,
                NOTIFICATION_TIME_STAMP,
                NOTIFICATION_STATUS,
                NOTIFICATION_MESSAGE_ID,
                NOTIFICATION_CHANNEL_ID,
                OPPORTUNITY_ID,
                OPPORTUNITY_UUID,
                PAYMENT_UUID,
                PAYMENT_ID,
                NOTIFICATION_KEY,
                OPPORTUNITY_STATUS,
                META_SESSION_ENDPOINT_ID,
                META_REQUIRE_APP_SYNC,
            )
        assertEquals(expectedKeys, payload.keys)
    }

    @Test
    fun `convertPNRecordsToPayload returns an empty list when given a null list`() {
        val result = PushNotificationApiHelper.convertPNRecordsToPayload(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `convertPNRecordsToPayload returns an empty list when given an empty list`() {
        val result = PushNotificationApiHelper.convertPNRecordsToPayload(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `convertPNRecordsToPayload maps two records to two payloads in order`() {
        val record1 = buildRecord().apply { action = "action_one" }
        val record2 = buildRecord().apply { action = "action_two" }
        val result = PushNotificationApiHelper.convertPNRecordsToPayload(listOf(record1, record2))
        assertEquals(2, result.size)
        assertEquals("action_one", result[0][REDIRECT_ACTION])
        assertEquals("action_two", result[1][REDIRECT_ACTION])
    }
}
