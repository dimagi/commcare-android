package org.commcare.views.connect

import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobLearningRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecordV28
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectLearnJobTestData
import org.commcare.dalvik.R
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.DateFormat

/**
 * Robolectric tests for [ConnectLearnProgressView]: verifies each of the three learning states
 * renders the right progress count, banner, "Continue Learning" card and CTA, using real
 * [ConnectJobRecord]s so the module/completion matching runs exactly as it does in production.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectLearnProgressViewTest {
    private lateinit var context: Context

    /** Server ids the test data assigns: `MODULE_ID_BASE + n` for "Module n". */
    private val moduleOneId = ConnectLearnJobTestData.MODULE_ID_BASE + 1
    private val moduleTwoId = ConnectLearnJobTestData.MODULE_ID_BASE + 2

    @Before
    fun setUp() {
        context =
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext<CommCareTestApplication>(),
                R.style.ConnectTheme,
            )
        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region state builders

    /** Modules still outstanding: only [completedModuleIds] are done. */
    private fun inProgressJob(vararg completedModuleIds: Int): ConnectJobRecord =
        ConnectLearnJobTestData
            .job(completedModules = completedModuleIds.size, assessmentScore = null)
            .withLearnings(*completedModuleIds)

    /** Every module done, assessment not yet attempted. */
    private fun assessmentPendingJob(): ConnectJobRecord =
        ConnectLearnJobTestData
            .job(completedModules = ConnectLearnJobTestData.TOTAL_MODULES, assessmentScore = null)
            .withLearnings(moduleOneId, moduleTwoId)

    /** Every module done, assessment attempted and failed. */
    private fun assessmentFailedJob(): ConnectJobRecord =
        ConnectLearnJobTestData
            .job(completedModules = ConnectLearnJobTestData.TOTAL_MODULES, assessmentScore = 10)
            .withLearnings(moduleOneId, moduleTwoId)

    /**
     * Attaches a completion record per module id, each a day later than the last so the most recent
     * completion is the final id passed.
     */
    private fun ConnectJobRecord.withLearnings(vararg moduleIds: Int): ConnectJobRecord =
        apply {
            learnings =
                moduleIds.mapIndexed { index, moduleId ->
                    learningRecord(this, moduleId, "2026-03-0${index + 1}T10:00:00")
                }
        }

    private fun learningRecord(
        job: ConnectJobRecord,
        moduleId: Int,
        date: String,
    ): ConnectJobLearningRecord =
        ConnectJobLearningRecord.fromJson(
            JSONObject().apply {
                put(ConnectJobLearningRecord.META_DATE, date)
                put(ConnectJobLearningRecord.META_MODULE, moduleId)
                put(ConnectJobLearningRecord.META_DURATION, "00:10:00")
            },
            job,
        )

    /**
     * Replaces the job's modules with records carrying no server id, which is what rows written
     * before the id was persisted look like until the next sync.
     */
    private fun ConnectJobRecord.withoutModuleIds(): ConnectJobRecord =
        apply {
            learnAppInfo.learnModules =
                learnAppInfo.learnModules.mapIndexed { index, module ->
                    ConnectLearnModuleSummaryRecord.fromV28(
                        ConnectLearnModuleSummaryRecordV28().apply {
                            name = module.name
                            slug = module.slug
                            timeEstimate = module.timeEstimate
                            moduleIndex = index
                        },
                    )
                }
        }

    // endregion

    private fun bind(
        job: ConnectJobRecord,
        onCta: () -> Unit = {},
    ): ConnectLearnProgressView =
        ConnectLearnProgressView(context).also { view ->
            view.bind(job) { onCta() }
        }

    private fun ConnectLearnProgressView.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun ConnectLearnProgressView.visibility(id: Int) = findViewById<View>(id).visibility

    private fun estimatedTime(hours: Int) =
        context.resources.getQuantityString(
            R.plurals.connect_opportunity_estimated_hours,
            hours,
            hours,
        )

    @Test
    fun `header shows the job title and the short-formatted expiry date`() {
        val job = inProgressJob(moduleOneId)
        val view = bind(job)

        assertEquals(ConnectLearnJobTestData.JOB_TITLE, view.text(R.id.learn_progress_job_title))
        assertEquals(
            ConnectDateUtils.formatDate(job.projectEndDate, DateFormat.SHORT),
            view.text(R.id.learn_progress_expiry_value),
        )
    }

    @Test
    fun `progress card counts completed modules and hides the banner while modules remain`() {
        val view = bind(inProgressJob(moduleOneId))

        assertEquals(
            context.getString(R.string.connect_learn_modules_completed_label),
            view.text(R.id.progress_card_bar_label),
        )
        assertEquals(
            context.getString(
                R.string.connect_progress_count_format,
                1,
                ConnectLearnJobTestData.TOTAL_MODULES,
            ),
            view.text(R.id.progress_card_bar_count),
        )
        assertEquals(
            context.getString(R.string.connect_learn_unlock_assessment_caption),
            view.text(R.id.progress_card_bar_caption),
        )
        assertEquals(View.GONE, view.visibility(R.id.progress_card_info_message))
    }

    @Test
    fun `continue card names the next unfinished module`() {
        val view = bind(inProgressJob(moduleOneId))

        assertEquals(
            context.getString(R.string.connect_learn_up_next_label),
            view.text(R.id.learn_progress_continue_label),
        )
        assertEquals("2. Module 2", view.text(R.id.learn_progress_continue_title))
        assertEquals(estimatedTime(1), view.text(R.id.learn_progress_continue_subtitle))
    }

    @Test
    fun `next module is the unfinished one even when modules were completed out of order`() {
        val view = bind(inProgressJob(moduleTwoId))

        assertEquals("1. Module 1", view.text(R.id.learn_progress_continue_title))
    }

    @Test
    fun `assessment is up next once every module is done and it has not been attempted`() {
        val view = bind(assessmentPendingJob())

        assertEquals(
            context.getString(R.string.connect_learn_up_next_label),
            view.text(R.id.learn_progress_continue_label),
        )
        assertEquals(
            context.getString(R.string.connect_learn_assessment_name),
            view.text(R.id.learn_progress_continue_title),
        )
        assertEquals(
            context.getString(R.string.connect_learn_assessment_description),
            view.text(R.id.learn_progress_continue_subtitle),
        )
        assertEquals(View.GONE, view.visibility(R.id.progress_card_info_message))
    }

    @Test
    fun `a failed assessment shows the retry banner and the last completed module`() {
        val view = bind(assessmentFailedJob())

        assertEquals(View.VISIBLE, view.visibility(R.id.progress_card_info_message))
        assertEquals(
            context.getString(R.string.connect_learn_assessment_failed_banner),
            view.text(R.id.progress_card_info_text),
        )
        assertEquals(
            context.getString(R.string.connect_learn_completed_module_label),
            view.text(R.id.learn_progress_continue_label),
        )
        assertEquals("2. Module 2", view.text(R.id.learn_progress_continue_title))
    }

    @Test
    fun `the last completed module is the most recent one, not the last in order`() {
        val job =
            ConnectLearnJobTestData
                .job(completedModules = ConnectLearnJobTestData.TOTAL_MODULES, assessmentScore = 10)
                .withLearnings(moduleTwoId, moduleOneId)
        val view = bind(job)

        assertEquals("1. Module 1", view.text(R.id.learn_progress_continue_title))
    }

    @Test
    fun `cta subtitle tracks the learning state`() {
        assertEquals(
            context.resources.getQuantityString(
                R.plurals.connect_opportunity_learn_modules_label,
                ConnectLearnJobTestData.TOTAL_MODULES,
            ),
            bind(inProgressJob(moduleOneId)).text(R.id.cta_subtitle_text),
        )
        assertEquals(
            context.getString(R.string.connect_learn_cta_complete_assessment),
            bind(assessmentPendingJob()).text(R.id.cta_subtitle_text),
        )
        assertEquals(
            context.getString(R.string.connect_learn_cta_take_assessment),
            bind(assessmentFailedJob()).text(R.id.cta_subtitle_text),
        )
    }

    @Test
    fun `cta offers the learn download when the learn app is missing and start when installed`() {
        val missing = bind(inProgressJob(moduleOneId))
        assertEquals(
            context.getString(R.string.connect_learn_continue),
            missing.text(R.id.cta_title_text),
        )
        assertEquals(
            context.getString(R.string.connect_download_learn),
            missing.text(R.id.cta_button),
        )

        every { AppUtils.isAppInstalled(ConnectLearnJobTestData.LEARN_APP_ID) } returns true
        val installed = bind(inProgressJob(moduleOneId))
        assertEquals(
            context.getString(R.string.connect_learn_cta_start),
            installed.text(R.id.cta_button),
        )
    }

    @Test
    fun `an ended job shows the warning banner and an active job does not`() {
        val ended =
            ConnectLearnJobTestData
                .job(completedModules = 1, assessmentScore = null, endDate = "2020-01-01")
                .withLearnings(moduleOneId)
        val endedView = bind(ended)

        assertEquals(View.VISIBLE, endedView.visibility(R.id.cta_info_banner))
        assertEquals(
            context.getString(R.string.connect_learn_warning_ended),
            endedView.text(R.id.cta_info_banner),
        )

        assertEquals(
            View.GONE,
            bind(inProgressJob(moduleOneId)).visibility(R.id.cta_info_banner),
        )
    }

    @Test
    fun `the assessment is named when no module records are available`() {
        val job = inProgressJob(moduleOneId)
        job.learnAppInfo.learnModules = emptyList()
        val view = bind(job)

        assertEquals(
            context.getString(R.string.connect_learn_assessment_name),
            view.text(R.id.learn_progress_continue_title),
        )
        assertEquals(View.VISIBLE, view.visibility(R.id.learn_progress_continue_heading))
    }

    @Test
    fun `modules without a server id fall back to the completed count`() {
        val view = bind(inProgressJob(moduleOneId).withoutModuleIds())

        assertEquals("2. Module 2", view.text(R.id.learn_progress_continue_title))
    }

    @Test
    fun `tapping the cta button invokes the click listener`() {
        var clicks = 0
        val view = bind(inProgressJob(moduleOneId), onCta = { clicks++ })

        view.findViewById<MaterialButton>(R.id.cta_button).performClick()

        assertEquals(1, clicks)
    }
}
