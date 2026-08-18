package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectLearnJobTestData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Covers [ConnectJobRecord.getLearningCompletionDate] and [ConnectJobRecord.hasLearningRecords],
 * which decide the date the Connect screens present as when the user finished learning.
 *
 * Records are built through their own `fromJson`, so an unparseable date produces the same null the
 * real payload would rather than one the test set by hand.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobLearningCompletionTest {
    @Test
    fun `the completion date is the last assessment the user sat`() {
        val job = jobWith(assessmentDates = listOf("2026-01-05T09:00:00", "2026-03-09T09:00:00"))

        assertEquals(date(2026, Calendar.MARCH, 9), job.learningCompletionDate)
    }

    @Test
    fun `without an assessment the completion date is the last module completed`() {
        val job = jobWith(learningDates = listOf("2025-06-01T09:00:00", "2025-07-30T09:00:00"))

        assertEquals(date(2025, Calendar.JULY, 30), job.learningCompletionDate)
    }

    @Test
    fun `an assessment takes precedence over a later completed module`() {
        val job =
            jobWith(
                assessmentDates = listOf("2026-01-05T09:00:00"),
                learningDates = listOf("2026-12-31T09:00:00"),
            )

        assertEquals(date(2026, Calendar.JANUARY, 5), job.learningCompletionDate)
    }

    @Test
    fun `a record whose date failed to parse does not decide the answer`() {
        val job = jobWith(learningDates = listOf("not-a-date", "2025-06-01T09:00:00"))

        assertEquals(date(2025, Calendar.JUNE, 1), job.learningCompletionDate)
    }

    @Test
    fun `records that all failed to parse fall back to today rather than to nothing`() {
        val job = jobWith(learningDates = listOf("not-a-date"))

        assertEquals(startOfToday(), truncateToDay(job.learningCompletionDate))
    }

    @Test
    fun `an unparseable assessment date does not fall through to the modules`() {
        val job =
            jobWith(
                assessmentDates = listOf("not-a-date"),
                learningDates = listOf("2025-06-01T09:00:00"),
            )

        assertEquals(startOfToday(), truncateToDay(job.learningCompletionDate))
    }

    @Test
    fun `a job with no learning records at all falls back to today`() {
        assertEquals(startOfToday(), truncateToDay(jobWith().learningCompletionDate))
    }

    @Test
    fun `a job reports whether it holds any record of learning`() {
        assertFalse("neither assessments nor modules", jobWith().hasLearningRecords())
        assertTrue(
            "a completed module",
            jobWith(learningDates = listOf("2025-06-01T09:00:00")).hasLearningRecords(),
        )
        assertTrue(
            "an assessment",
            jobWith(assessmentDates = listOf("2026-01-05T09:00:00")).hasLearningRecords(),
        )
    }

    private fun jobWith(
        assessmentDates: List<String> = emptyList(),
        learningDates: List<String> = emptyList(),
    ): ConnectJobRecord =
        ConnectLearnJobTestData.job(assessmentScore = null).apply {
            assessments = assessmentDates.map { assessment(this, it) }
            learnings = learningDates.mapIndexed { index, date -> learning(this, index, date) }
        }

    private fun assessment(
        job: ConnectJobRecord,
        date: String,
    ): ConnectJobAssessmentRecord =
        ConnectJobAssessmentRecord.fromJson(
            JSONObject().apply {
                put(ConnectJobAssessmentRecord.META_DATE, date)
                put(ConnectJobAssessmentRecord.META_SCORE, ConnectLearnJobTestData.PASSING_SCORE)
                put(ConnectJobAssessmentRecord.META_PASSING_SCORE, ConnectLearnJobTestData.PASSING_SCORE)
                put(ConnectJobAssessmentRecord.META_PASSED, true)
            },
            job,
        )

    private fun learning(
        job: ConnectJobRecord,
        moduleId: Int,
        date: String,
    ): ConnectJobLearningRecord =
        ConnectJobLearningRecord.fromJson(
            JSONObject().apply {
                put(ConnectJobLearningRecord.META_DATE, date)
                put(ConnectJobLearningRecord.META_MODULE, moduleId)
                put(ConnectJobLearningRecord.META_DURATION, "1")
            },
            job,
        )

    private fun date(
        year: Int,
        month: Int,
        day: Int,
    ): Date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(isoOf(year, month, day))!!

    private fun isoOf(
        year: Int,
        month: Int,
        day: Int,
    ): String = "%04d-%02d-%02dT09:00:00".format(year, month + 1, day)

    private fun startOfToday(): Date = truncateToDay(Date())

    private fun truncateToDay(date: Date): Date =
        Calendar
            .getInstance()
            .apply {
                time = date
                set(Calendar.MILLISECOND, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.HOUR_OF_DAY, 0)
            }.time
}
