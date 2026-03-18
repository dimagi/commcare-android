package org.commcare.connect.network.connectId.parser

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RetrieveChannelEncryptionKeyResponseParserTest {
    private lateinit var parser: RetrieveChannelEncryptionKeyResponseParser<Boolean>
    private lateinit var messagingDatabaseMock: MockedStatic<ConnectMessagingDatabaseHelper>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        parser = RetrieveChannelEncryptionKeyResponseParser(context)
        messagingDatabaseMock = mockStatic(ConnectMessagingDatabaseHelper::class.java)
    }

    @After
    fun tearDown() {
        messagingDatabaseMock.close()
    }

    @Test
    fun testParse_validJson_setsKeyOnChannel_andReturnsTrue() {
        // Arrange
        val channel = ConnectMessagingChannelRecord()
        val json = """{"key": "abc123-encryption-key"}"""
        val inputStream = ByteArrayInputStream(json.toByteArray())

        // Act
        val result = parser.parse(200, inputStream, channel)

        // Assert
        assertTrue(result)
        assertEquals("abc123-encryption-key", channel.getKey())
    }

    @Test
    fun testParse_emptyResponse_channelKeyUnchanged_andReturnsTrue() {
        // Arrange
        val channel = ConnectMessagingChannelRecord()
        val inputStream = ByteArrayInputStream(byteArrayOf())

        // Act
        val result = parser.parse(200, inputStream, channel)

        // Assert
        assertTrue(result)
        assertNull(channel.getKey())
    }

    @Test
    fun testParse_emptyResponse_doesNotCallStoreMessagingChannel() {
        // Arrange
        val channel = ConnectMessagingChannelRecord()
        val inputStream = ByteArrayInputStream(byteArrayOf())

        // Act
        parser.parse(200, inputStream, channel)

        // Assert — storeMessagingChannel must not be called when response is empty
        messagingDatabaseMock.verify(
            { ConnectMessagingDatabaseHelper.storeMessagingChannel(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
            ) },
            never(),
        )
    }

    @Test
    fun testParse_callsStoreMessagingChannel_once() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val channel = ConnectMessagingChannelRecord()
        val json = """{"key": "secure-key-value"}"""
        val inputStream = ByteArrayInputStream(json.toByteArray())

        // Act
        parser.parse(200, inputStream, channel)

        // Assert
        messagingDatabaseMock.verify(
            { ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel) },
            times(1),
        )
    }

    @Test(expected = RuntimeException::class)
    fun testParse_invalidJson_throwsRuntimeException() {
        // Arrange
        val channel = ConnectMessagingChannelRecord()
        val inputStream = ByteArrayInputStream("{ invalid json".toByteArray())

        // Act
        parser.parse(200, inputStream, channel)
    }

    @Test(expected = RuntimeException::class)
    fun testParse_missingKeyField_throwsRuntimeException() {
        // Arrange
        val channel = ConnectMessagingChannelRecord()
        val json = """{"other_field": "value"}"""
        val inputStream = ByteArrayInputStream(json.toByteArray())

        // Act
        parser.parse(200, inputStream, channel)
    }
}
