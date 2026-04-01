package org.commcare.connect.network.connectId.parser

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class LinkHqWorkerResponseParserTest {
    private lateinit var parser: LinkHqWorkerResponseParser<Boolean>
    private lateinit var connectAppDatabaseMock: MockedStatic<ConnectAppDatabaseUtil>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        parser = LinkHqWorkerResponseParser(context)
        connectAppDatabaseMock = mockStatic(ConnectAppDatabaseUtil::class.java)
    }

    @After
    fun tearDown() {
        connectAppDatabaseMock.close()
    }

    @Test
    fun testParse_setsWorkerLinkedTrue_andReturnsTrue() {
        // Arrange
        val appRecord = ConnectLinkedAppRecord()
        val inputStream = ByteArrayInputStream(byteArrayOf())

        // Act
        val result = parser.parse(200, inputStream, appRecord)

        // Assert
        assertTrue(result)
        assertTrue(appRecord.getWorkerLinked())
    }

    @Test
    fun testParse_workerLinkedFalseBeforeParse_trueAfter() {
        // Arrange
        val appRecord = ConnectLinkedAppRecord()
        assertTrue(!appRecord.getWorkerLinked())
        val inputStream = ByteArrayInputStream(byteArrayOf())

        // Act
        parser.parse(200, inputStream, appRecord)

        // Assert
        assertTrue(appRecord.getWorkerLinked())
    }

    @Test
    fun testParse_callsStoreApp_once() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appRecord = ConnectLinkedAppRecord()
        val inputStream = ByteArrayInputStream(byteArrayOf())

        // Act
        parser.parse(200, inputStream, appRecord)

        // Assert
        connectAppDatabaseMock.verify(
            { ConnectAppDatabaseUtil.storeApp(context, appRecord) },
            times(1),
        )
    }

    @Test(expected = NullPointerException::class)
    fun testParse_nullInputObject_throwsNullPointerException() {
        // Arrange
        val inputStream = ByteArrayInputStream(byteArrayOf())

        // Act
        parser.parse(200, inputStream, null)
    }
}
