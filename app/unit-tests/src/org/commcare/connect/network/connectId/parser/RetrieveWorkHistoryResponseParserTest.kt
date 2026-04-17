package org.commcare.connect.network.connectId.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito.eq
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RetrieveWorkHistoryResponseParserTest {
    private var context: Context = CommCareTestApplication.instance()

    private lateinit var parser: RetrieveWorkHistoryResponseParser<List<PersonalIdWorkHistory>>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        parser = RetrieveWorkHistoryResponseParser(context)
    }

    private data class ExpectedCredential(
        val uuid: String,
        val appId: String,
        val oppId: String,
        val title: String,
        val issuedDate: String,
        val issuer: String,
        val level: String,
        val type: String,
        val issuerEnvironment: String,
        val slug: String,
    )

    private fun assertCredentialMatches(
        actual: PersonalIdWorkHistory,
        expected: ExpectedCredential,
    ) {
        assertEquals(expected.uuid, actual.uuid)
        assertEquals(expected.appId, actual.appId)
        assertEquals(expected.oppId, actual.oppId)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.issuedDate, actual.issuedDate)
        assertEquals(expected.issuer, actual.issuer)
        assertEquals(expected.level, actual.level)
        assertEquals(expected.type, actual.type)
        assertEquals(expected.issuerEnvironment, actual.issuerEnvironment)
        assertEquals(expected.slug, actual.slug)
    }

    private fun assertCredentialsMatch(
        actualList: List<PersonalIdWorkHistory>,
        expectedList: List<ExpectedCredential>,
    ) {
        assertEquals(expectedList.size, actualList.size)
        expectedList.forEachIndexed { index, expected ->
            assertCredentialMatches(actualList[index], expected)
        }
    }

    private fun verifyDatabaseStorage(
        mockedUtil: MockedStatic<ConnectAppDatabaseUtil>,
        expectedCredentials: List<ExpectedCredential>,
    ) {
        val listCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<PersonalIdWorkHistory>>
        mockedUtil.verify(
            { ConnectAppDatabaseUtil.storeCredentialDataInTable(eq(context), listCaptor.capture()) },
            times(1),
        )

        val capturedList = listCaptor.value
        assertCredentialsMatch(capturedList, expectedCredentials)
    }

    @Test
    fun testParseValidResponseWithMultipleCredentials() {
        // Arrange
        val expectedCredentials =
            listOf(
                ExpectedCredential(
                    uuid = "cred_1",
                    appId = "app123",
                    oppId = "opp123",
                    title = "Vaccine Delivery",
                    issuedDate = "2023-01-15T10:00:00Z",
                    issuer = "HQ",
                    level = "1K_DELIVERIES",
                    type = "DELIVER",
                    issuerEnvironment = "production",
                    slug = "slug123",
                ),
                ExpectedCredential(
                    uuid = "cred_2",
                    appId = "app456",
                    oppId = "opp456",
                    title = "Field Surveyor",
                    issuedDate = "2023-03-20T14:30:00Z",
                    issuer = "Connect",
                    level = "2K_DELIVERIES",
                    type = "LEARN",
                    issuerEnvironment = "staging",
                    slug = "slug456",
                ),
            )

        val jsonResponse =
            """
            {
                "credentials": [
                    {
                        "uuid": "${expectedCredentials[0].uuid}",
                        "app_id": "${expectedCredentials[0].appId}",
                        "opp_id": "${expectedCredentials[0].oppId}",
                        "title": "${expectedCredentials[0].title}",
                        "date": "${expectedCredentials[0].issuedDate}",
                        "issuer": "${expectedCredentials[0].issuer}",
                        "level": "${expectedCredentials[0].level}",
                        "type": "${expectedCredentials[0].type}",
                        "issuer_environment": "${expectedCredentials[0].issuerEnvironment}",
                        "slug": "${expectedCredentials[0].slug}"
                    },
                    {
                        "uuid": "${expectedCredentials[1].uuid}",
                        "app_id": "${expectedCredentials[1].appId}",
                        "opp_id": "${expectedCredentials[1].oppId}",
                        "title": "${expectedCredentials[1].title}",
                        "date": "${expectedCredentials[1].issuedDate}",
                        "issuer": "${expectedCredentials[1].issuer}",
                        "level": "${expectedCredentials[1].level}",
                        "type": "${expectedCredentials[1].type}",
                        "issuer_environment": "${expectedCredentials[1].issuerEnvironment}",
                        "slug": "${expectedCredentials[1].slug}"
                    }
                ]
            }
            """.trimIndent()

        mockStatic(ConnectAppDatabaseUtil::class.java).use { mockedUtil ->
            val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())

            // Act
            val result = parser.parse(200, inputStream, null)

            // Assert
            assertCredentialsMatch(result, expectedCredentials)
            verifyDatabaseStorage(mockedUtil, expectedCredentials)
        }
    }

    @Test
    fun testParseValidResponseWithEmptyCredentialsArray() {
        // Arrange
        val jsonResponse =
            """
            {
                "credentials": []
            }
            """.trimIndent()

        mockStatic(ConnectAppDatabaseUtil::class.java).use { mockedUtil ->
            val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())

            // Act
            val result = parser.parse(200, inputStream, null)

            // Assert
            assertEquals(0, result.size)

            // Verify database storage was called with empty list
            val listCaptor =
                ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<PersonalIdWorkHistory>>
            mockedUtil.verify({
                ConnectAppDatabaseUtil.storeCredentialDataInTable(
                    eq(context),
                    listCaptor.capture(),
                )
            }, times(1))

            // Verify the captured list is empty
            val capturedList = listCaptor.value
            assertEquals(0, capturedList.size)
        }
    }

    @Test(expected = JSONException::class)
    fun testParseInvalidJsonThrowsException() {
        // Arrange
        val invalidJson = "{ invalid json }"
        val inputStream = ByteArrayInputStream(invalidJson.toByteArray())

        // Act & Assert
        parser.parse(200, inputStream, null)
    }

    @Test(expected = JSONException::class)
    fun testParseMissingCredentialsFieldThrowsException() {
        // Arrange
        val jsonResponse =
            """
            {
                "other_field": "value"
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())

        // Act & Assert
        parser.parse(200, inputStream, null)
    }

    @Test(expected = JSONException::class)
    fun testParseNullCredentialsFieldThrowsException() {
        // Arrange
        val jsonResponse =
            """
            {
                "credentials": null
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())

        // Act & Assert
        parser.parse(200, inputStream, null)
    }

    @Test(expected = JSONException::class)
    fun testParseNonArrayCredentialsFieldThrowsException() {
        // Arrange
        val jsonResponse =
            """
            {
                "credentials": "not_an_array"
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())

        // Act & Assert
        parser.parse(200, inputStream, null)
    }

    @Test(expected = JSONException::class)
    fun testParseEmptyResponseThrowsException() {
        // Arrange
        val inputStream = ByteArrayInputStream("".toByteArray())

        // Act & Assert
        parser.parse(200, inputStream, null)
    }
}
