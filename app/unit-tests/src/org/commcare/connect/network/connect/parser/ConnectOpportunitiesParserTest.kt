package org.commcare.connect.network.connect.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.workers.ConnectReleaseTogglesWorker
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectOpportunitiesParserTest {
    private lateinit var parser: ConnectOpportunitiesParser<ArrayList<ConnectJobRecord>>

    @Before
    fun setUp() {
        parser = ConnectOpportunitiesParser()
        mockkStatic(ConnectJobUtils::class)
        mockkObject(ConnectReleaseTogglesWorker.Companion)
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectJobUtils::class)
        unmockkObject(ConnectReleaseTogglesWorker.Companion)
        unmockkStatic(FirebaseAnalyticsUtil::class)
    }

    private fun validJobJson(id: Int): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("opportunity_id", "job-uuid-$id")
            put("name", "Test Job $id")
            put("description", "Description for job $id")
            put("organization", "Test Org")
            put("end_date", "2025-12-31")
            put("start_date", "2025-01-01")
            put("max_visits_per_user", 100)
            put("daily_max_visits_per_user", 10)
            put("budget_per_visit", 50)
            put("budget_per_user", 5000)
            put("currency", "USD")
            put("short_description", "Short description")
            put("deliver_progress", 0)
            put("payment_units", JSONArray())
            put(
                "learn_progress",
                JSONObject().apply {
                    put("total_modules", 3)
                    put("completed_modules", 0)
                },
            )
            put("learn_app", appJson())
            put("deliver_app", appJson())
        }

    private fun appJson(): JSONObject =
        JSONObject().apply {
            put("cc_domain", "test-domain")
            put("cc_app_id", "app-id-001")
            put("name", "Test App")
            put("description", "Test app description")
            put("organization", "Test Org")
            put("passing_score", 80)
            put("install_url", "https://example.com/install")
            put("learn_modules", JSONArray())
        }

    private fun jsonArrayOf(vararg objects: JSONObject): ByteArrayInputStream {
        val array = JSONArray().apply { objects.forEach { put(it) } }
        return ByteArrayInputStream(array.toString().toByteArray())
    }

    @Test
    fun `parse returns empty list and does not call storeJobs when response body is empty`() {
        val inputStream = ByteArrayInputStream("".toByteArray())

        val result = parser.parse(200, inputStream, null)

        assertEquals(0, result.size)
        verify(exactly = 0) { ConnectJobUtils.storeJobs(any(), any(), any()) }
    }

    @Test
    fun `parse returns empty list and calls storeJobs with empty list when JSON array is empty`() {
        val inputStream = jsonArrayOf()
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0

        val result = parser.parse(200, inputStream, null)

        assertEquals(0, result.size)
        verify(exactly = 1) { ConnectJobUtils.storeJobs(any(), match { it.isEmpty() }, true) }
    }

    @Test
    fun `parse returns the parsed job when JSON contains a single valid entry`() {
        val inputStream = jsonArrayOf(validJobJson(42))
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 1
        every { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) } just Runs

        val result = parser.parse(200, inputStream, null)

        assertEquals(1, result.size)
        assertEquals(42, result[0].jobId)
        assertEquals("Test Job 42", result[0].title)
        verify(exactly = 1) {
            ConnectJobUtils.storeJobs(any(), match { it.size == 1 && it[0].jobId == 42 }, true)
        }
        verify(exactly = 1) { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) }
    }

    @Test
    fun `parse returns all parsed jobs when JSON contains multiple valid entries`() {
        val inputStream = jsonArrayOf(validJobJson(1), validJobJson(2))
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0

        val result = parser.parse(200, inputStream, null)

        assertEquals(2, result.size)
        assertEquals(1, result[0].jobId)
        assertEquals(2, result[1].jobId)
    }

    @Test
    fun `parse throws JSONException and reports failure when a corrupt entry is encountered`() {
        val corruptEntry = JSONObject().apply { put("id", 99) }
        val inputStream = jsonArrayOf(corruptEntry)
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0

        assertThrows(JSONException::class.java) {
            parser.parse(200, inputStream, null)
        }
        verify(exactly = 1) { ConnectJobUtils.storeJobs(any(), match { it.isEmpty() }, true) }
        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccApiJobs(false, 0, 0) }
    }

    @Test
    fun `parse schedules feature toggle fetch when storeJobs returns more than zero new jobs`() {
        val inputStream = jsonArrayOf(validJobJson(1))
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 2
        every { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) } just Runs

        parser.parse(200, inputStream, null)

        verify(exactly = 1) { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) }
    }

    @Test
    fun `parse does not schedule feature toggle fetch when storeJobs returns zero new jobs`() {
        val inputStream = jsonArrayOf(validJobJson(1))
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0

        parser.parse(200, inputStream, null)

        verify(exactly = 0) { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) }
    }

    @Test
    fun `parse reports successful api call with correct job counts`() {
        val inputStream = jsonArrayOf(validJobJson(1))
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 3
        every { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) } just Runs

        parser.parse(200, inputStream, null)

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccApiJobs(true, 1, 3) }
    }

    @Test
    fun `parse throws JSONException when response body is not a JSON array`() {
        val inputStream = ByteArrayInputStream("{not an array}".toByteArray())

        assertThrows(JSONException::class.java) {
            parser.parse(200, inputStream, null)
        }
    }

    @Test
    fun `parse reports failed api call when JSON parsing fails`() {
        val inputStream = ByteArrayInputStream("[invalid".toByteArray())

        try {
            parser.parse(200, inputStream, null)
        } catch (e: JSONException) {
            // expected
        }

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccApiJobs(false, 0, 0) }
    }
}
