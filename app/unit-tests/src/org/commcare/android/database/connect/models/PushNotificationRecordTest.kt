package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectConstants
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PushNotificationRecordTest {

    private val timestamp = "2023-01-15T10:30:00Z"

    private fun buildFullJson(
        notificationId: String = "notif-001",
        title: String = "Test Title",
        body: String = "Test body",
        notificationType: String = "PUSH",
        timestamp: String = this.timestamp,
        messageId: String = "msg-123",
        channel: String = "channel-abc",
        action: String = "redirect",
        confirmationStatus: String = "pending",
        opportunityId: String = "opp-456",
        opportunityUUID: String = "opp-uuid-456",
        paymentId: String = "pay-789",
        paymentUUID: String = "pay-uuid-789",
        key: String = "key-value",
        opportunityStatus: String = "learn",
        readStatus: Boolean = false,
    ): JSONObject =
        JSONObject().apply {
            put(PushNotificationRecord.META_NOTIFICATION_ID, notificationId)
            put(PushNotificationRecord.META_TITLE, title)
            put(PushNotificationRecord.META_BODY, body)
            put(PushNotificationRecord.META_NOTIFICATION_TYPE, notificationType)
            put(PushNotificationRecord.META_TIME_STAMP, timestamp)
            put(PushNotificationRecord.META_MESSAGE_ID, messageId)
            put(PushNotificationRecord.META_CHANNEL, channel)
            put(PushNotificationRecord.META_ACTION, action)
            put(PushNotificationRecord.META_CONFIRMATION_STATUS, confirmationStatus)
            put(PushNotificationRecord.META_OPPORTUNITY_ID, opportunityId)
            put(PushNotificationRecord.META_OPPORTUNITY_UUID, opportunityUUID)
            put(PushNotificationRecord.META_PAYMENT_ID, paymentId)
            put(PushNotificationRecord.META_PAYMENT_UUID, paymentUUID)
            put(PushNotificationRecord.META_KEY, key)
            put(PushNotificationRecord.META_OPPORTUNITY_STATUS, opportunityStatus)
            put(PushNotificationRecord.META_READ_STATUS, readStatus)
        }

    @Test
    fun fromJson_allFields_parsedCorrectly() {
        val expectedDate = DateUtils.parseDateTime(timestamp)
        val record = PushNotificationRecord.fromJson(buildFullJson())

        assertEquals("notif-001", record.notificationId)
        assertEquals("Test Title", record.title)
        assertEquals("Test body", record.body)
        assertEquals("PUSH", record.notificationType)
        assertEquals(expectedDate, record.createdDate)
        assertEquals("msg-123", record.connectMessageId)
        assertEquals("channel-abc", record.channel)
        assertEquals("redirect", record.action)
        assertEquals("pending", record.confirmationStatus)
        assertEquals("opp-456", record.opportunityId)
        assertEquals("opp-uuid-456", record.opportunityUUID)
        assertEquals("pay-789", record.paymentId)
        assertEquals("pay-uuid-789", record.paymentUUID)
        assertEquals("key-value", record.key)
        assertEquals("learn", record.opportunityStatus)
        assertFalse(record.readStatus)
    }

    @Test
    fun fromJson_readStatusTrue_parsedCorrectly() {
        val record = PushNotificationRecord.fromJson(buildFullJson(readStatus = true))
        assertTrue(record.readStatus)
    }

    @Test
    fun fromJson_missingOptionalFields_defaultsToEmpty() {
        val json =
            JSONObject().apply {
                put(PushNotificationRecord.META_TIME_STAMP, timestamp)
            }
        val record = PushNotificationRecord.fromJson(json)

        assertEquals("", record.notificationId)
        assertEquals("", record.title)
        assertEquals("", record.body)
        assertEquals("", record.notificationType)
        assertEquals("", record.connectMessageId)
        assertEquals("", record.channel)
        assertEquals("", record.action)
        assertEquals("", record.confirmationStatus)
        assertEquals("", record.opportunityId)
        assertEquals("", record.opportunityUUID)
        assertEquals("", record.paymentId)
        assertEquals("", record.paymentUUID)
        assertEquals("", record.key)
        assertEquals("", record.opportunityStatus)
        assertFalse(record.readStatus)
    }

    @Test(expected = JSONException::class)
    fun fromJson_missingTimestamp_throwsJSONException() {
        val json =
            JSONObject().apply {
                put(PushNotificationRecord.META_NOTIFICATION_ID, "notif-001")
            }
        PushNotificationRecord.fromJson(json)
    }

    @Test
    fun getNotificationActionFromRecord_genericOpportunityActionWithNonEmptyKey_returnsKey() {
        val record =
            PushNotificationRecord().apply {
                action = ConnectConstants.CCC_GENERIC_OPPORTUNITY
                key = "deep-link-key"
            }
        assertEquals("deep-link-key", record.getNotificationActionFromRecord())
    }

    @Test
    fun getNotificationActionFromRecord_genericOpportunityActionWithEmptyKey_returnsAction() {
        val record =
            PushNotificationRecord().apply {
                action = ConnectConstants.CCC_GENERIC_OPPORTUNITY
                key = ""
            }
        assertEquals(ConnectConstants.CCC_GENERIC_OPPORTUNITY, record.getNotificationActionFromRecord())
    }

    @Test
    fun getNotificationActionFromRecord_nonGenericAction_returnsAction() {
        val record =
            PushNotificationRecord().apply {
                action = "some_other_action"
                key = "some-key"
            }
        assertEquals("some_other_action", record.getNotificationActionFromRecord())
    }

    @Test
    fun titleSetter_longTitle_isTruncatedToMaxLength() {
        val longTitle = "A".repeat(70000)
        val record = PushNotificationRecord().apply { title = longTitle }
        assertEquals(65535, record.title.length)
    }

    @Test
    fun bodySetter_longBody_isTruncatedToMaxLength() {
        val longBody = "A".repeat(70000)
        val record = PushNotificationRecord().apply { body = longBody }
        assertEquals(65535, record.body.length)
    }

    @Test
    fun fromV23_allFieldsCopied_newFieldsDefaultToEmpty() {
        val v23 =
            PushNotificationRecordV23().apply {
                notificationId = "v23-id"
                title = "V23 Title"
                body = "V23 body"
                notificationType = "PAYMENT"
                confirmationStatus = "confirmed"
                paymentId = "pay-123"
                readStatus = true
                createdDate = Date(1_000_000L)
                connectMessageId = "msg-456"
                channel = "chan-789"
                action = "action-v23"
                acknowledged = true
                opportunityId = "opp-111"
                opportunityUUID = "opp-uuid-111"
                paymentUUID = "pay-uuid-123"
            }

        val record = PushNotificationRecord.fromV23(v23)

        assertEquals("v23-id", record.notificationId)
        assertEquals("V23 Title", record.title)
        assertEquals("V23 body", record.body)
        assertEquals("PAYMENT", record.notificationType)
        assertEquals("confirmed", record.confirmationStatus)
        assertEquals("pay-123", record.paymentId)
        assertTrue(record.readStatus)
        assertEquals(Date(1_000_000L), record.createdDate)
        assertEquals("msg-456", record.connectMessageId)
        assertEquals("chan-789", record.channel)
        assertEquals("action-v23", record.action)
        assertTrue(record.acknowledged)
        assertEquals("opp-111", record.opportunityId)
        assertEquals("opp-uuid-111", record.opportunityUUID)
        assertEquals("pay-uuid-123", record.paymentUUID)
        assertEquals("", record.key)
        assertEquals("", record.opportunityStatus)
    }
}
