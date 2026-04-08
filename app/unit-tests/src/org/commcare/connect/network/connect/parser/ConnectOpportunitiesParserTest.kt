package org.commcare.connect.network.connect.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.workers.ConnectReleaseTogglesWorker
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectOpportunitiesParserTest {

    private lateinit var parser: ConnectOpportunitiesParser<ConnectOpportunitiesResponseModel>
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        parser = ConnectOpportunitiesParser()
        mockkStatic(ConnectJobRecord::class)
        mockkStatic(ConnectJobUtils::class)
        mockkObject(ConnectReleaseTogglesWorker.Companion)
        mockkStatic(FirebaseAnalyticsUtil::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectJobRecord::class)
        unmockkStatic(ConnectJobUtils::class)
        unmockkObject(ConnectReleaseTogglesWorker.Companion)
        unmockkStatic(FirebaseAnalyticsUtil::class)
    }

    private fun validJobJson(id: Int): JSONObject = JSONObject().apply {
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
        put("learn_progress", JSONObject().apply {
            put("total_modules", 3)
            put("completed_modules", 0)
        })
        put("learn_app", appJson())
        put("deliver_app", appJson())
    }

    private fun appJson(): JSONObject = JSONObject().apply {
        put("cc_domain", "test-domain")
        put("cc_app_id", "app-id-001")
        put("name", "Test App")
        put("description", "Test app description")
        put("organization", "Test Org")
        put("passing_score", 80)
        put("install_url", "https://example.com/install")
        put("learn_modules", JSONArray())
    }

    @Test
    fun testEmptyResponseBody_returnsEmptyModel_noStoreJobsCalled() {
        val inputStream = ByteArrayInputStream("".toByteArray())

        val result = parser.parse(200, inputStream, context)

        assertEquals(0, result.validJobs.size)
        assertEquals(0, result.corruptJobs.size)
        verify(exactly = 0) { ConnectJobUtils.storeJobs(any(), any(), any()) }
    }

    @Test
    fun testEmptyJsonArray_returnsEmptyModel_storeJobsNotCalled() {
        val inputStream = ByteArrayInputStream(JSONArray().toString().toByteArray())
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        val result = parser.parse(200, inputStream, context)

        assertEquals(0, result.validJobs.size)
        assertEquals(0, result.corruptJobs.size)
        verify(exactly = 0) { ConnectJobUtils.storeJobs(any(), any(), any()) }
    }

    @Test
    fun testSingleValidJob_parsedJobAddedToValidJobsList() {
        unmockkStatic(ConnectJobRecord::class)
        val inputStream = ByteArrayInputStream(JSONArray().apply { put(validJobJson(42)) }.toString().toByteArray())
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 1
        every { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) } just Runs
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        val result = parser.parse(200, inputStream, context)

        assertEquals(1, result.validJobs.size)
        assertEquals(0, result.corruptJobs.size)
        assertEquals(42, result.validJobs[0].jobId)
        assertEquals("Test Job 42", result.validJobs[0].title)
        verify(exactly = 1) { ConnectJobUtils.storeJobs(context, match { it.size == 1 && it[0].jobId == 42 }, true) }
        verify(exactly = 1) { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(context) }
    }

    @Test
    fun testMultipleValidJobs_allAddedToValidJobsList() {
        val mockJob1 = mockk<ConnectJobRecord>()
        val mockJob2 = mockk<ConnectJobRecord>()
        val array = JSONArray().apply {
            put(JSONObject().apply { put("id", 1) })
            put(JSONObject().apply { put("id", 2) })
        }
        val inputStream = ByteArrayInputStream(array.toString().toByteArray())
        every { ConnectJobRecord.fromJson(any()) } returnsMany listOf(mockJob1, mockJob2)
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        val result = parser.parse(200, inputStream, context)

        assertEquals(2, result.validJobs.size)
        assertEquals(0, result.corruptJobs.size)
    }

    @Test
    fun testCorruptJobEntry_fromJsonThrowsJSONException_addsToCorruptJobsList() {
        val corruptJob = mockk<ConnectJobRecord>()
        val array = JSONArray().apply { put(JSONObject().apply { put("id", 99) }) }
        val inputStream = ByteArrayInputStream(array.toString().toByteArray())
        every { ConnectJobRecord.fromJson(any()) } throws JSONException("parse error")
        every { ConnectJobRecord.corruptJobFromJson(any()) } returns corruptJob
        every { corruptJob.title } returns "Corrupt Job Title"
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        val result = parser.parse(200, inputStream, context)

        assertEquals(0, result.validJobs.size)
        assertEquals(1, result.corruptJobs.size)
        assertEquals("Corrupt Job Title", result.corruptJobs[0].name)
    }

    @Test
    fun testNewJobsGreaterThanZero_schedulesOneTimeFetch() {
        val mockJob = mockk<ConnectJobRecord>()
        val array = JSONArray().apply { put(JSONObject().apply { put("id", 1) }) }
        val inputStream = ByteArrayInputStream(array.toString().toByteArray())
        every { ConnectJobRecord.fromJson(any()) } returns mockJob
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 2
        every { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) } just Runs
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        parser.parse(200, inputStream, context)

        verify(exactly = 1) { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(context) }
    }

    @Test
    fun testNewJobsEqualsZero_doesNotScheduleOneTimeFetch() {
        val mockJob = mockk<ConnectJobRecord>()
        val array = JSONArray().apply { put(JSONObject().apply { put("id", 1) }) }
        val inputStream = ByteArrayInputStream(array.toString().toByteArray())
        every { ConnectJobRecord.fromJson(any()) } returns mockJob
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        parser.parse(200, inputStream, context)

        verify(exactly = 0) { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) }
    }

    @Test
    fun testParseSuccess_reportsApiJobsWithCorrectCounts() {
        val mockJob = mockk<ConnectJobRecord>()
        val array = JSONArray().apply { put(JSONObject().apply { put("id", 1) }) }
        val inputStream = ByteArrayInputStream(array.toString().toByteArray())
        every { ConnectJobRecord.fromJson(any()) } returns mockJob
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 3
        every { ConnectReleaseTogglesWorker.scheduleOneTimeFetch(any()) } just Runs
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        parser.parse(200, inputStream, context)

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccApiJobs(true, 1, 3) }
    }

    @Test(expected = RuntimeException::class)
    fun testInvalidJson_throwsRuntimeException() {
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs
        val inputStream = ByteArrayInputStream("{not an array}".toByteArray())

        parser.parse(200, inputStream, context)
    }

    @Test
    fun testInvalidJson_reportsApiCallWithFailure() {
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs
        val inputStream = ByteArrayInputStream("[invalid".toByteArray())

        try {
            parser.parse(200, inputStream, context)
        } catch (e: RuntimeException) {
            // expected
        }

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccApiJobs(false, 0, 0) }
    }
}
