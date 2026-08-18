package org.commcare.connect

import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds real [ConnectJobRecord]s for the learning screens, so tests exercise the same parsing and
 * progress calculations as production instead of stubbing the record.
 */
object ConnectLearnJobTestData {
    const val JOB_UUID = "job-uuid-learn-1"
    const val JOB_TITLE = "Infant Vaccine Check"
    const val LEARN_APP_ID = "learn-app-001"
    const val DELIVERY_APP_ID = "delivery-app-001"
    const val PASSING_SCORE = 80
    const val TOTAL_MODULES = 2

    /** Server module ids run from `MODULE_ID_BASE + 1`, mirroring the real API's opaque ids. */
    const val MODULE_ID_BASE = 4881
    const val MAX_VISITS = 40

    /**
     * The job-level daily cap sits above the combined per-unit caps so the two limits can be
     * exercised independently; a fixture where they coincide cannot express "one unit is done for
     * today while the job still allows work".
     */
    const val MAX_DAILY_VISITS = 12
    const val PAYMENT_UNIT_MAX_DAILY = 5
    const val TOTAL_BUDGET = 2500
    const val PAYMENT_UNIT_COUNT = 2
    const val CURRENCY = "INR"
    const val ASSESSMENT_DATE = "2026-02-21T10:00:00"

    /**
     * @param completedModules how many of [TOTAL_MODULES] the worker has finished
     * @param assessmentScore score of a single recorded assessment, or null for no attempt
     * @param endDate project end date; a past date makes the job finished
     */
    fun job(
        completedModules: Int = TOTAL_MODULES,
        assessmentScore: Int? = PASSING_SCORE + 10,
        endDate: String = "2027-12-31",
    ): ConnectJobRecord {
        val job = ConnectJobRecord.fromJson(jobJson(completedModules, endDate))
        if (assessmentScore != null) {
            job.assessments = listOf(assessment(job, assessmentScore))
        }
        return job
    }

    private fun assessment(
        job: ConnectJobRecord,
        score: Int,
    ): ConnectJobAssessmentRecord =
        ConnectJobAssessmentRecord.fromJson(
            JSONObject().apply {
                put(ConnectJobAssessmentRecord.META_DATE, ASSESSMENT_DATE)
                put(ConnectJobAssessmentRecord.META_SCORE, score)
                put(ConnectJobAssessmentRecord.META_PASSING_SCORE, PASSING_SCORE)
                put(ConnectJobAssessmentRecord.META_PASSED, score >= PASSING_SCORE)
            },
            job,
        )

    private fun jobJson(
        completedModules: Int,
        endDate: String,
    ): JSONObject =
        JSONObject().apply {
            put("id", 1)
            put("opportunity_id", JOB_UUID)
            put("name", JOB_TITLE)
            put("description", "A detailed description of the opportunity.")
            put("organization", "Test Org")
            put("start_date", "2025-01-01")
            put("end_date", endDate)
            put("max_visits_per_user", MAX_VISITS)
            put("daily_max_visits_per_user", MAX_DAILY_VISITS)
            put("budget_per_visit", 25)
            put("budget_per_user", TOTAL_BUDGET)
            put("currency", CURRENCY)
            put("short_description", "One line description about the opportunity.")
            put("deliver_progress", 0)
            put("payment_units", paymentUnitsJson())
            put(
                "learn_progress",
                JSONObject().apply {
                    put("total_modules", TOTAL_MODULES)
                    put("completed_modules", completedModules)
                },
            )
            put("learn_app", appJson(LEARN_APP_ID, withModules = true))
            put("deliver_app", appJson(DELIVERY_APP_ID, withModules = false))
        }

    private fun paymentUnitsJson(): JSONArray =
        JSONArray().apply {
            for (id in 1..PAYMENT_UNIT_COUNT) {
                put(
                    JSONObject().apply {
                        put("id", id)
                        put("payment_unit_id", "unit-$id")
                        put("name", "Unit $id")
                        put("max_total", MAX_VISITS / PAYMENT_UNIT_COUNT)
                        put("max_daily", PAYMENT_UNIT_MAX_DAILY)
                        put("amount", 25)
                    },
                )
            }
        }

    private fun appJson(
        appId: String,
        withModules: Boolean,
    ): JSONObject =
        JSONObject().apply {
            put("cc_domain", "test-domain")
            put("cc_app_id", appId)
            put("name", "Test App")
            put("description", "Test app description")
            put("organization", "Test Org")
            put("passing_score", PASSING_SCORE)
            put("install_url", "https://example.com/install")
            put("learn_modules", if (withModules) modulesJson() else JSONArray())
        }

    private fun modulesJson(): JSONArray =
        JSONArray().apply {
            for (index in 1..TOTAL_MODULES) {
                put(
                    JSONObject().apply {
                        put("id", MODULE_ID_BASE + index)
                        put("slug", "module-$index")
                        put("name", "Module $index")
                        put("description", "Description of module $index")
                        put("time_estimate", 1)
                    },
                )
            }
        }
}
