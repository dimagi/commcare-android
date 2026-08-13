package org.commcare.connect.network.connectId.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.network.connect.parser.ConnectReleaseTogglesParser
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectReleaseTogglesParserTest {
    private var context: Context = CommCareTestApplication.instance()
    private lateinit var parser: ConnectReleaseTogglesParser
    private lateinit var savedPersonalIdStatus: PersonalIdManager.PersonalIdStatus

    @Before
    fun setUp() {
        parser = ConnectReleaseTogglesParser()
        savedPersonalIdStatus = PersonalIdManager.getInstance().status
        mockkStatic(ConnectAppDatabaseUtil::class)

        // Mock the DB storage call so it doesn't try to access a real DB.
        every { ConnectAppDatabaseUtil.storeReleaseToggles(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectAppDatabaseUtil::class)
        PersonalIdManager.getInstance().status = savedPersonalIdStatus
    }

    private fun setSignedIn(signedIn: Boolean) {
        PersonalIdManager.getInstance().status =
            if (signedIn) {
                PersonalIdManager.PersonalIdStatus.LoggedIn
            } else {
                PersonalIdManager.PersonalIdStatus.NotIntroduced
            }
    }

    private fun singleToggleJson() =
        """
        {
            "toggles": {
                "feature_a": {
                    "active": true
                }
            }
        }
        """.trimIndent()

    @Test
    fun testParseValidResponse() {
        // Arrange
        val mockDateString = "2026-01-26T00:00:00Z"
        val mockJson =
            """
            {
                "toggles": {
                    "feature_a": {
                        "active": true,
                        "created_at": "$mockDateString",
                        "modified_at": "$mockDateString"
                    },
                    "feature_b": {
                        "active": false,
                        "created_at": "$mockDateString",
                        "modified_at": "$mockDateString"
                    }
                }
            }
            """.trimIndent()

        val expectedDate = DateUtils.parseDateTime(mockDateString)

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Act
        val result = parser.parse(200, inputStream, context)

        // Assert
        assertEquals(2, result.size)

        assertEquals("feature_a", result[0].slug)
        assertTrue(result[0].active)
        assertEquals(expectedDate, result[0].createdAt)
        assertEquals(expectedDate, result[0].modifiedAt)

        assertEquals("feature_b", result[1].slug)
        assertFalse(result[1].active)
        assertEquals(expectedDate, result[1].createdAt)
        assertEquals(expectedDate, result[1].modifiedAt)
    }

    @Test(expected = JSONException::class)
    fun testParseWithInvalidTogglesJson() {
        // Arrange
        val mockJson =
            """
            {
                "toggles": "not_an_object"
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Act
        parser.parse(200, inputStream, context)
    }

    @Test(expected = JSONException::class)
    fun testParseWithInvalidJson() {
        // Arrange
        val mockJson =
            """
            {
                "invalid_json"
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Act
        parser.parse(200, inputStream, context)
    }

    @Test
    fun testParseWithSingleInvalidToggleJson() {
        // Arrange
        val mockDateString = "2026-01-26T00:00:00Z"
        val mockJson =
            """
            {
                "toggles": {
                    "feature_a": {
                        "active": true,
                        "created_at": "$mockDateString",
                        "modified_at": "$mockDateString"
                    },
                    "feature_b": "not_an_object"
                }
            }
            """.trimIndent()

        val expectedDate = DateUtils.parseDateTime(mockDateString)

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Act
        val result = parser.parse(200, inputStream, context)

        // Assert
        assertEquals(1, result.size)

        assertEquals("feature_a", result[0].slug)
        assertTrue(result[0].active)
        assertEquals(expectedDate, result[0].createdAt)
        assertEquals(expectedDate, result[0].modifiedAt)
    }

    @Test
    fun testParseWithInvalidIndividualTogglesJson() {
        // Arrange
        val mockJson =
            """
            {
                "toggles": {
                    "feature_a": "not_an_object",
                    "feature_b": "not_an_object"
                }
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())

        // Act
        val result = parser.parse(200, inputStream, context)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun testTogglesAreStoredWhileSignedIn() {
        setSignedIn(true)
        val inputStream = ByteArrayInputStream(singleToggleJson().toByteArray())

        val result = parser.parse(200, inputStream, context)

        assertEquals(1, result.size)
        verify(exactly = 1) { ConnectAppDatabaseUtil.storeReleaseToggles(context, result) }
    }

    @Test
    fun testTogglesAreNotStoredWhenSignedOut() {
        setSignedIn(false)
        val inputStream = ByteArrayInputStream(singleToggleJson().toByteArray())

        val result = parser.parse(200, inputStream, context)

        //  the response is still parsed and returned, it just isn't persisted
        assertEquals(1, result.size)
        verify(exactly = 0) { ConnectAppDatabaseUtil.storeReleaseToggles(any(), any()) }
    }

    @Test
    fun testParseWithMissingDateFieldsUsesDefaults() {
        // Arrange
        val mockJson =
            """
            {
                "toggles": {
                    "feature_a": {
                        "active": true
                    }
                }
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(mockJson.toByteArray())
        val testStartTime = System.currentTimeMillis()

        // Act
        val result = parser.parse(200, inputStream, context)

        // Assert
        assertEquals(1, result.size)
        assertTrue(result[0].active)
        assertTrue(result[0].createdAt.time >= testStartTime)
        assertTrue(result[0].modifiedAt.time >= testStartTime)
    }
}
