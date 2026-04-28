package org.commcare.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PushNotificationRecord
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
        }

    @Test
    fun convertPNRecordToPayload_allFields_mappedToCorrectKeys() {
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
    }

    @Test
    fun convertPNRecordToPayload_containsExactlyExpectedKeys() {
        val payload = PushNotificationApiHelper.convertPNRecordToPayload(buildRecord())
        val expectedKeys = setOf(
            REDIRECT_ACTION, NOTIFICATION_TITLE, NOTIFICATION_BODY,
            NOTIFICATION_ID, NOTIFICATION_TIME_STAMP, NOTIFICATION_STATUS,
            NOTIFICATION_MESSAGE_ID, NOTIFICATION_CHANNEL_ID, OPPORTUNITY_ID,
            OPPORTUNITY_UUID, PAYMENT_UUID, PAYMENT_ID, NOTIFICATION_KEY,
            OPPORTUNITY_STATUS,
        )
        assertEquals(expectedKeys, payload.keys)
    }

    @Test
    fun convertPNRecordsToPayload_nullList_returnsEmptyList() {
        val result = PushNotificationApiHelper.convertPNRecordsToPayload(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun convertPNRecordsToPayload_emptyList_returnsEmptyList() {
        val result = PushNotificationApiHelper.convertPNRecordsToPayload(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun convertPNRecordsToPayload_twoRecords_returnsTwoMappedPayloads() {
        val record1 = buildRecord().apply { action = "action_one" }
        val record2 = buildRecord().apply { action = "action_two" }
        val result = PushNotificationApiHelper.convertPNRecordsToPayload(listOf(record1, record2))
        assertEquals(2, result.size)
        assertEquals("action_one", result[0][REDIRECT_ACTION])
        assertEquals("action_two", result[1][REDIRECT_ACTION])
    }
}
