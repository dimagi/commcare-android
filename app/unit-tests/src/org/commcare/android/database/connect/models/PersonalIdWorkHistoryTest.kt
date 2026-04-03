package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdWorkHistoryTest {

    private fun buildValidJson(
        uuid: String = "uuid-001",
        appId: String = "app-123",
        oppId: String = "opp-456",
        date: String = "2025-01-15",
        title: String = "Data Entry Certificate",
        issuer: String = "Health Ministry",
        level: String = "intermediate",
        type: String = "training",
        issuerEnvironment: String = "production",
        slug: String = "data-entry-cert",
    ): JSONObject =
        JSONObject().apply {
            put("uuid", uuid)
            put("app_id", appId)
            put("opp_id", oppId)
            put("date", date)
            put("title", title)
            put("issuer", issuer)
            put("level", level)
            put("type", type)
            put("issuer_environment", issuerEnvironment)
            put("slug", slug)
        }

    @Test
    fun testFromJsonArray_emptyArray_returnsEmptyList() {
        val result = PersonalIdWorkHistory.fromJsonArray(JSONArray())
        assertEquals(0, result.size)
    }

    @Test
    fun testFromJsonArray_singleEntry_allFieldsParsedCorrectly() {
        val array = JSONArray().put(buildValidJson())

        val result = PersonalIdWorkHistory.fromJsonArray(array)

        assertEquals(1, result.size)
        val record = result[0]
        assertEquals("uuid-001", record.uuid)
        assertEquals("app-123", record.appId)
        assertEquals("opp-456", record.oppId)
        assertEquals("2025-01-15", record.issuedDate)
        assertEquals("Data Entry Certificate", record.title)
        assertEquals("Health Ministry", record.issuer)
        assertEquals("intermediate", record.level)
        assertEquals("training", record.type)
        assertEquals("production", record.issuerEnvironment)
        assertEquals("data-entry-cert", record.slug)
    }

    @Test
    fun testFromJsonArray_multipleEntries_allParsed() {
        val array =
            JSONArray().apply {
                put(buildValidJson(uuid = "uuid-001", title = "Certificate A"))
                put(buildValidJson(uuid = "uuid-002", title = "Certificate B"))
                put(buildValidJson(uuid = "uuid-003", title = "Certificate C"))
            }

        val result = PersonalIdWorkHistory.fromJsonArray(array)

        assertEquals(3, result.size)
        assertEquals("uuid-001", result[0].uuid)
        assertEquals("Certificate A", result[0].title)
        assertEquals("uuid-002", result[1].uuid)
        assertEquals("Certificate B", result[1].title)
        assertEquals("uuid-003", result[2].uuid)
        assertEquals("Certificate C", result[2].title)
    }

    @Test
    fun testFromJsonArray_distinctFieldsPerEntry_notCrossContaminated() {
        val array =
            JSONArray().apply {
                put(buildValidJson(uuid = "uuid-A", appId = "app-A", oppId = "opp-A", slug = "slug-A"))
                put(buildValidJson(uuid = "uuid-B", appId = "app-B", oppId = "opp-B", slug = "slug-B"))
            }

        val result = PersonalIdWorkHistory.fromJsonArray(array)

        assertEquals("uuid-A", result[0].uuid)
        assertEquals("app-A", result[0].appId)
        assertEquals("opp-A", result[0].oppId)
        assertEquals("slug-A", result[0].slug)
        assertEquals("uuid-B", result[1].uuid)
        assertEquals("app-B", result[1].appId)
        assertEquals("opp-B", result[1].oppId)
        assertEquals("slug-B", result[1].slug)
    }

    @Test(expected = RuntimeException::class)
    fun testFromJsonArray_missingUuid_throwsRuntimeException() {
        val json = buildValidJson().apply { remove("uuid") }
        PersonalIdWorkHistory.fromJsonArray(JSONArray().put(json))
    }

    @Test(expected = RuntimeException::class)
    fun testFromJsonArray_missingAppId_throwsRuntimeException() {
        val json = buildValidJson().apply { remove("app_id") }
        PersonalIdWorkHistory.fromJsonArray(JSONArray().put(json))
    }

    @Test(expected = RuntimeException::class)
    fun testFromJsonArray_missingOppId_throwsRuntimeException() {
        val json = buildValidJson().apply { remove("opp_id") }
        PersonalIdWorkHistory.fromJsonArray(JSONArray().put(json))
    }

    @Test(expected = RuntimeException::class)
    fun testFromJsonArray_missingDate_throwsRuntimeException() {
        val json = buildValidJson().apply { remove("date") }
        PersonalIdWorkHistory.fromJsonArray(JSONArray().put(json))
    }

    @Test(expected = RuntimeException::class)
    fun testFromJsonArray_missingSlug_throwsRuntimeException() {
        val json = buildValidJson().apply { remove("slug") }
        PersonalIdWorkHistory.fromJsonArray(JSONArray().put(json))
    }

    @Test
    fun testFromJsonArray_missingField_errorMessageContainsIndex() {
        val json = buildValidJson().apply { remove("uuid") }
        val array = JSONArray().put(json)

        val exception =
            runCatching { PersonalIdWorkHistory.fromJsonArray(array) }
                .exceptionOrNull() as? RuntimeException

        assertEquals(true, exception?.message?.contains("index 0"))
    }

    @Test
    fun testFromJsonArray_corruptSecondEntry_errorMessageContainsIndex1() {
        val corrupt = buildValidJson().apply { remove("title") }
        val array =
            JSONArray().apply {
                put(buildValidJson())
                put(corrupt)
            }

        val exception =
            runCatching { PersonalIdWorkHistory.fromJsonArray(array) }
                .exceptionOrNull() as? RuntimeException

        assertEquals(true, exception?.message?.contains("index 1"))
    }
}
