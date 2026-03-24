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
import java.io.InputStream

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

    @Test
    fun testEmptyResponseBody_returnsEmptyModel_noStoreJobsCalled() {
        val inputStream: InputStream = ByteArrayInputStream("".toByteArray())

        val result = parser.parse(200, inputStream, context)

        assertEquals(0, result.validJobs.size)
        assertEquals(0, result.corruptJobs.size)
        verify(exactly = 0) { ConnectJobUtils.storeJobs(any(), any(), any()) }
    }

    @Test
    fun testEmptyJsonArray_returnsEmptyModel_storeJobsCalledOnce() {
        val inputStream: InputStream = ByteArrayInputStream(JSONArray().toString().toByteArray())
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        val result = parser.parse(200, inputStream, context)

        assertEquals(0, result.validJobs.size)
        assertEquals(0, result.corruptJobs.size)
        verify(exactly = 1) { ConnectJobUtils.storeJobs(context, any(), true) }
    }

    @Test
    fun testSingleValidJob_parsedJobAddedToValidJobsList() {
        val mockJob = mockk<ConnectJobRecord>()
        val array = JSONArray().apply { put(JSONObject().apply { put("id", 42) }) }
        val inputStream: InputStream = ByteArrayInputStream(array.toString().toByteArray())
        every { ConnectJobRecord.fromJson(any()) } returns mockJob
        every { ConnectJobUtils.storeJobs(any(), any(), any()) } returns 0
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs

        val result = parser.parse(200, inputStream, context)

        assertEquals(1, result.validJobs.size)
        assertEquals(0, result.corruptJobs.size)
    }

    @Test
    fun testMultipleValidJobs_allAddedToValidJobsList() {
        val mockJob1 = mockk<ConnectJobRecord>()
        val mockJob2 = mockk<ConnectJobRecord>()
        val array = JSONArray().apply {
            put(JSONObject().apply { put("id", 1) })
            put(JSONObject().apply { put("id", 2) })
        }
        val inputStream: InputStream = ByteArrayInputStream(array.toString().toByteArray())
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
        val inputStream: InputStream = ByteArrayInputStream(array.toString().toByteArray())
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
        val inputStream: InputStream = ByteArrayInputStream(array.toString().toByteArray())
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
        val inputStream: InputStream = ByteArrayInputStream(array.toString().toByteArray())
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
        val inputStream: InputStream = ByteArrayInputStream(array.toString().toByteArray())
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
        val inputStream: InputStream = ByteArrayInputStream("{not an array}".toByteArray())

        parser.parse(200, inputStream, context)
    }

    @Test
    fun testInvalidJson_reportsApiCallWithFailure() {
        every { FirebaseAnalyticsUtil.reportCccApiJobs(any(), any(), any()) } just Runs
        val inputStream: InputStream = ByteArrayInputStream("[invalid".toByteArray())

        try {
            parser.parse(200, inputStream, context)
        } catch (e: RuntimeException) {
            // expected
        }

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccApiJobs(false, 0, 0) }
    }
}
