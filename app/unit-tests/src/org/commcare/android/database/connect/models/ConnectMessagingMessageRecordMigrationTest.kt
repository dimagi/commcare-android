package org.commcare.android.database.connect.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ConnectMessagingMessageRecordMigrationTest {
    @Test
    fun `fromV29 keeps the stored message and marks it as a plain message`() {
        val timestamp = Date(1_700_000_000_000)
        val oldRecord =
            ConnectMessagingMessageRecordV29().apply {
                messageId = "message-1"
                channelId = "channel-1"
                timeStamp = timestamp
                message = "Hello"
                isOutgoing = true
                confirmed = true
                userViewed = true
            }

        val record = ConnectMessagingMessageRecord.fromV29(oldRecord)

        assertEquals("message-1", record.messageId)
        assertEquals("channel-1", record.channelId)
        assertEquals(timestamp, record.timeStamp)
        assertEquals("Hello", record.message)
        assertTrue(record.isOutgoing)
        assertTrue(record.confirmed)
        assertTrue(record.userViewed)
        assertEquals(ConnectMessagingMessageRecord.VERSION_PLAIN, record.version)
        assertFalse(record.isRich)
        assertNull(record.richText)
        assertNull(record.format)
        assertNull(record.expiresAt)
        assertEquals("Hello", record.displayText)
    }
}
