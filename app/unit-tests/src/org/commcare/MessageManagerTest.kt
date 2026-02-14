package org.commcare

import org.commcare.connect.ConnectConstants
import org.commcare.connect.MessageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageManagerTest {

    @Test
    fun truncateMessage_whenMessageTooLong_shouldTruncate() {

        val longMessage = "A".repeat(70000)
        val result = MessageManager.truncateLongString(
            longMessage,
            "message"
        )

        assertEquals(
            ConnectConstants.MAX_MESSAGE_LENGTH,
            result.length
        )
    }

    @Test
    fun truncateMessage_whenMessageShort_shouldRemainSame() {

        val message = "Hello"
        val result = MessageManager.truncateLongString(
            message,
            "message"
        )

        assertEquals(message, result)
    }
}
