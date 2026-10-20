package org.commcare.l10n

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Realistic Connect opportunities for the localization screenshots, built through the real JSON
 * parsing. Opportunity content comes from the server untranslated, so it stays English in every locale.
 */
object ConnectScreenshotData {
    private const val ORGANIZATION = "Kisumu Community Health Partners"
    private const val CURRENCY = "INR"
    private const val LEARN_MODULES = 5
    private const val MAX_VISITS = 40

    /** One opportunity per state the opportunity list shows, with a long title and an expiry warning among them. */
    fun opportunities(): List<ConnectJobRecord> =
        listOf(
            opportunity(1, "Household Malaria Prevention Survey", ConnectJobRecord.STATUS_LEARNING, endInDays = 21, modulesDone = 3),
            opportunity(
                2,
                "Child Growth Monitoring and Nutrition Counselling, Kisumu County",
                ConnectJobRecord.STATUS_DELIVERING,
                endInDays = 2,
                visitsDone = 27,
            ),
            opportunity(3, "Maternal Health Home Visits", ConnectJobRecord.STATUS_DELIVERING, endInDays = 60, visitsDone = 12),
            opportunity(4, "Community Vaccination Awareness Campaign", ConnectJobRecord.STATUS_AVAILABLE_NEW, endInDays = 45),
            opportunity(5, "Polio Campaign Follow-up", ConnectJobRecord.STATUS_DELIVERING, endInDays = -14, visitsDone = MAX_VISITS),
            opportunity(6, "Bed Net Distribution Check", ConnectJobRecord.STATUS_DELIVERING, endInDays = -40, visitsDone = 18),
        )

    private fun opportunity(
        id: Int,
        title: String,
        status: Int,
        endInDays: Int,
        modulesDone: Int = LEARN_MODULES,
        visitsDone: Int = 0,
    ): ConnectJobRecord =
        ConnectJobRecord.fromJson(jobJson(id, title, endInDays, modulesDone, visitsDone)).apply {
            this.status = status
        }

    private fun jobJson(
        id: Int,
        title: String,
        endInDays: Int,
        modulesDone: Int,
        visitsDone: Int,
    ): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("opportunity_id", "showcase-opportunity-$id")
            put("name", title)
            put("description", "Visit households in your area, record what you find and share health guidance with each family.")
            put("short_description", "Visit households and record what you find.")
            put("organization", ORGANIZATION)
            put("start_date", isoDate(endInDays - 120))
            put("end_date", isoDate(endInDays))
            put("max_visits_per_user", MAX_VISITS)
            put("daily_max_visits_per_user", 8)
            put("budget_per_visit", 1250)
            put("budget_per_user", 1250 * MAX_VISITS)
            put("currency", CURRENCY)
            put("deliver_progress", visitsDone)
            put("payment_units", paymentUnitsJson())
            put(
                "learn_progress",
                JSONObject().apply {
                    put("total_modules", LEARN_MODULES)
                    put("completed_modules", modulesDone)
                },
            )
            put("learn_app", appJson("showcase-learn-$id", withModules = true))
            put("deliver_app", appJson("showcase-deliver-$id", withModules = false))
        }

    private fun paymentUnitsJson(): JSONArray =
        JSONArray().put(
            JSONObject().apply {
                put("id", 1)
                put("payment_unit_id", "household-visit")
                put("name", "Household visit")
                put("max_total", MAX_VISITS)
                put("max_daily", 8)
                put("amount", 1250)
            },
        )

    private fun appJson(
        appId: String,
        withModules: Boolean,
    ): JSONObject =
        JSONObject().apply {
            put("cc_domain", "showcase-domain")
            put("cc_app_id", appId)
            put("name", if (withModules) "Community Health Training" else "Household Visits")
            put("description", "Training and forms for community health workers.")
            put("organization", ORGANIZATION)
            put("passing_score", 80)
            put("install_url", "https://example.org/$appId.ccz")
            put("learn_modules", if (withModules) modulesJson() else JSONArray())
        }

    private fun modulesJson(): JSONArray =
        JSONArray().apply {
            listOf("Introduction to Connect", "Talking with Households", "Recognising Danger Signs", "Recording a Visit", "Referrals")
                .forEachIndexed { index, name ->
                    put(
                        JSONObject().apply {
                            put("id", index + 1)
                            put("slug", "module-${index + 1}")
                            put("name", name)
                            put("description", "$name for community health workers.")
                            put("time_estimate", 2)
                        },
                    )
                }
        }

    private fun isoDate(daysFromNow: Int): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysFromNow) }.time)
}
