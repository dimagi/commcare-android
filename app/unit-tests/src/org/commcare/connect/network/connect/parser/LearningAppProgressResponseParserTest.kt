package org.commcare.connect.network.connect.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.javarosa.core.model.utils.DateUtils
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
class LearningAppProgressResponseParserTest {
    private lateinit var parser: LearningAppProgressResponseParser<LearningAppProgressResponseModel>
    private lateinit var job: ConnectJobRecord

    @Before
    fun setUp() {
        parser = LearningAppProgressResponseParser()
        job = ConnectJobRecord()
        job.setJobId(10)
        job.setJobUUID("test-uuid-1")
    }

    @Test
    fun testEmptyResponseBody_returnsEmptyLists() {
        val inputStream = ByteArrayInputStream("".toByteArray())
        val result = parser.parse(200, inputStream, job)
        assertEquals(0, result.connectJobLearningRecords.size)
        assertEquals(0, result.connectJobAssessmentRecords.size)
    }

    @Test
    fun testBothArraysEmpty_returnsEmptyLists() {
        val json = """{"completed_modules": [], "assessments": []}"""
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val result = parser.parse(200, inputStream, job)
        assertEquals(0, result.connectJobLearningRecords.size)
        assertEquals(0, result.connectJobAssessmentRecords.size)
    }

    @Test
    fun testCompletedModules_populatesLearningRecords() {
        val dateString = "2023-01-15T10:00:00Z"
        val expectedDate = DateUtils.parseDateTime(dateString)
        val json = """
            {
                "completed_modules": [
                    {"date": "$dateString", "module": 42, "duration": "00:30:00"}
                ],
                "assessments": []
            }
        """.trimIndent()
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val result = parser.parse(200, inputStream, job)
        assertEquals(1, result.connectJobLearningRecords.size)
        assertEquals(42, result.connectJobLearningRecords[0].moduleId)
        assertEquals(expectedDate, result.connectJobLearningRecords[0].date)
        assertEquals("00:30:00", result.connectJobLearningRecords[0].duration)
        assertEquals(0, result.connectJobAssessmentRecords.size)
    }

    @Test
    fun testMultipleModules_populatesAllLearningRecords() {
        val json = """
            {
                "completed_modules": [
                    {"date": "2023-01-15T00:00:00Z", "module": 1, "duration": "00:10:00"},
                    {"date": "2023-01-16T00:00:00Z", "module": 2, "duration": "00:20:00"}
                ],
                "assessments": []
            }
        """.trimIndent()
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val result = parser.parse(200, inputStream, job)
        assertEquals(2, result.connectJobLearningRecords.size)
        assertEquals(1, result.connectJobLearningRecords[0].moduleId)
        assertEquals(2, result.connectJobLearningRecords[1].moduleId)
        assertEquals(0, result.connectJobAssessmentRecords.size)
    }

    @Test
    fun testAssessments_populatesAssessmentRecords() {
        val dateString = "2023-02-10T08:00:00Z"
        val expectedDate = DateUtils.parseDateTime(dateString)
        val json = """
            {
                "completed_modules": [],
                "assessments": [
                    {"date": "$dateString", "score": 85, "passing_score": 70, "passed": true}
                ]
            }
        """.trimIndent()
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val result = parser.parse(200, inputStream, job)
        assertEquals(0, result.connectJobLearningRecords.size)
        assertEquals(1, result.connectJobAssessmentRecords.size)
        assertEquals(85, result.connectJobAssessmentRecords[0].score)
        assertEquals(70, result.connectJobAssessmentRecords[0].passingScore)
        assertEquals(expectedDate, result.connectJobAssessmentRecords[0].date)
        assertTrue(result.connectJobAssessmentRecords[0].isPassed())
    }

    @Test
    fun testAssessmentPassedAbsent_defaultsFalse() {
        val json = """
            {
                "completed_modules": [],
                "assessments": [
                    {"date": "2023-02-10T08:00:00Z", "score": 50, "passing_score": 70}
                ]
            }
        """.trimIndent()
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val result = parser.parse(200, inputStream, job)
        assertEquals(1, result.connectJobAssessmentRecords.size)
        assertEquals(50, result.connectJobAssessmentRecords[0].score)
        assertEquals(70, result.connectJobAssessmentRecords[0].passingScore)
        assertFalse(result.connectJobAssessmentRecords[0].isPassed())
    }

    @Test
    fun testBothArraysPopulated_setsAllRecords() {
        val json = """
            {
                "completed_modules": [
                    {"date": "2023-01-15T00:00:00Z", "module": 5, "duration": "00:15:00"},
                    {"date": "2023-01-16T00:00:00Z", "module": 6, "duration": "00:25:00"}
                ],
                "assessments": [
                    {"date": "2023-01-20T00:00:00Z", "score": 90, "passing_score": 80, "passed": true},
                    {"date": "2023-01-21T00:00:00Z", "score": 60, "passing_score": 80, "passed": false}
                ]
            }
        """.trimIndent()
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val result = parser.parse(200, inputStream, job)
        assertEquals(2, result.connectJobLearningRecords.size)
        assertEquals(5, result.connectJobLearningRecords[0].moduleId)
        assertEquals(6, result.connectJobLearningRecords[1].moduleId)
        assertEquals(2, result.connectJobAssessmentRecords.size)
        assertEquals(90, result.connectJobAssessmentRecords[0].score)
        assertEquals(60, result.connectJobAssessmentRecords[1].score)
    }

    @Test(expected = RuntimeException::class)
    fun testMissingCompletedModulesKey_throwsRuntimeException() {
        val json = """{"assessments": []}"""
        val inputStream = ByteArrayInputStream(json.toByteArray())
        parser.parse(200, inputStream, job)
    }

    @Test(expected = RuntimeException::class)
    fun testMissingAssessmentsKey_throwsRuntimeException() {
        val json = """{"completed_modules": []}"""
        val inputStream = ByteArrayInputStream(json.toByteArray())
        parser.parse(200, inputStream, job)
    }

    @Test(expected = RuntimeException::class)
    fun testInvalidJson_throwsRuntimeException() {
        val inputStream = ByteArrayInputStream("{ invalid json }".toByteArray())
        parser.parse(200, inputStream, job)
    }
}
