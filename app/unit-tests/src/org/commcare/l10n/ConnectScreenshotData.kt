package org.commcare.l10n

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Realistic Connect opportunities for the localization screenshots, built through the real JSON
 * parsing, and messaging channels with a conversation. Server content comes untranslated, so it stays English in every locale.
 */
object ConnectScreenshotData {
    private const val ORGANIZATION = "Kisumu Community Health Partners"
    private const val CURRENCY = "INR"
    private const val LEARN_MODULES = 5
    private const val LEARNING_MODULES_DONE = 3
    private const val MAX_VISITS = 40
    private const val BUDGET_PER_VISIT = 1250
    private val HOUSEHOLDS = listOf("Achieng Odhiambo", "Baraka Mwangi", "Wanjiku Kamau", "Otieno Family", "Nyambura Njoroge")
    private const val DELIVERY_VISITS = 27
    private const val VISITS_PER_DAY = 4
    private const val PAYMENT_UNIT_ID = "household-visit"
    const val CONVERSATION_CHANNEL_ID = "showcase-channel-partners"
    const val LEARNING_OPPORTUNITY_ID = 1
    const val DELIVERING_OPPORTUNITY_ID = 2
    const val NEW_OPPORTUNITY_ID = 4

    /** A PersonalID user with a full name, a Kenyan phone number and an email. */
    fun buildUser(hasConnectAccess: Boolean = false): ConnectUserRecord =
        ConnectUserRecord(
            "+254712345678",
            "showcase-user-id",
            "showcase-password",
            "Amina Wanjiru Otieno",
            "",
            Date(),
            null,
            false,
            "",
            hasConnectAccess,
        ).apply {
            email = "amina.otieno@example.org"
        }

    /** One opportunity per state the opportunity list shows, with a long title and an expiry warning among them. */
    fun buildOpportunities(): List<ConnectJobRecord> =
        listOf(
            buildOpportunity(
                LEARNING_OPPORTUNITY_ID,
                "Household Malaria Prevention Survey",
                ConnectJobRecord.STATUS_LEARNING,
                endInDays = 21,
                modulesDone = LEARNING_MODULES_DONE,
            ),
            buildOpportunity(
                DELIVERING_OPPORTUNITY_ID,
                "Child Growth Monitoring and Nutrition Counselling, Kisumu County",
                ConnectJobRecord.STATUS_DELIVERING,
                endInDays = 2,
                visitsDone = DELIVERY_VISITS,
            ),
            buildOpportunity(3, "Maternal Health Home Visits", ConnectJobRecord.STATUS_DELIVERING, endInDays = 60, visitsDone = 12),
            buildOpportunity(
                NEW_OPPORTUNITY_ID,
                "Community Vaccination Awareness Campaign",
                ConnectJobRecord.STATUS_AVAILABLE_NEW,
                endInDays = 45,
            ),
            buildOpportunity(5, "Polio Campaign Follow-up", ConnectJobRecord.STATUS_DELIVERING, endInDays = -14, visitsDone = MAX_VISITS),
            buildOpportunity(6, "Bed Net Distribution Check", ConnectJobRecord.STATUS_DELIVERING, endInDays = -40, visitsDone = 18),
        )

    /** The opportunity UUID the server and the local database key the opportunity with [id] by. */
    fun getOpportunityUuid(id: Int): String = "showcase-opportunity-$id"

    /**
     * The learn progress response for the opportunity with [id]: the learning opportunity is part way through its
     * modules, and every other one has finished them and passed the assessment.
     */
    fun buildLearningProgressJson(id: Int): String {
        val modulesDone = if (id == LEARNING_OPPORTUNITY_ID) LEARNING_MODULES_DONE else LEARN_MODULES
        return JSONObject()
            .put(
                "completed_modules",
                JSONArray().apply {
                    (1..modulesDone).forEach { module ->
                        put(
                            JSONObject().apply {
                                put("id", module)
                                put("date", formatIsoDateTime(minutesAgo = (40 - module * 6) * 24 * 60))
                                put("module", module)
                                put("duration", "01:45:00")
                            },
                        )
                    }
                },
            ).put(
                "assessments",
                JSONArray().apply {
                    if (modulesDone == LEARN_MODULES) {
                        put(
                            JSONObject().apply {
                                put("date", formatIsoDateTime(minutesAgo = 9 * 24 * 60))
                                put("score", 92)
                                put("passing_score", 80)
                                put("passed", true)
                            },
                        )
                    }
                },
            ).toString()
    }

    /**
     * The delivery progress response for the delivering opportunity: its visits spread over the last week, the newest
     * of them still pending and one rejected, and two confirmed payments ahead of one still to confirm.
     */
    fun buildDeliveryProgressJson(): String {
        val rejectedVisit = 7
        val pendingVisits = 2
        val approvedVisits = DELIVERY_VISITS - pendingVisits - 1
        return JSONObject()
            .put("max_payments", MAX_VISITS)
            .put("payment_accrued", approvedVisits * BUDGET_PER_VISIT)
            .put("is_user_suspended", false)
            .put(
                "deliveries",
                JSONArray().apply {
                    (0 until DELIVERY_VISITS).forEach { visit ->
                        val status =
                            when {
                                visit < pendingVisits -> "pending"
                                visit == rejectedVisit -> "rejected"
                                else -> "approved"
                            }
                        put(buildDeliveryJson(visit, status, minutesAgo = (visit / VISITS_PER_DAY) * 24 * 60 + 30 + visit * 20))
                    }
                },
            ).put(
                "payments",
                JSONArray().apply {
                    put(buildPaymentJson("1", visits = 12, daysAgo = 14, confirmed = true))
                    put(buildPaymentJson("2", visits = 6, daysAgo = 7, confirmed = true))
                    put(buildPaymentJson("3", visits = 5, daysAgo = 1, confirmed = false))
                },
            ).toString()
    }

    /** Subscribed channels with unread, read and sent last messages, plus one the worker has unsubscribed from. */
    fun buildChannels(): List<ConnectMessagingChannelRecord> =
        listOf(
            buildChannel(CONVERSATION_CHANNEL_ID, ORGANIZATION, consented = true),
            buildChannel("showcase-channel-support", "Connect Support", consented = true),
            buildChannel("showcase-channel-malaria", "Household Malaria Prevention Survey", consented = true),
            buildChannel("showcase-channel-nutrition", "Nutrition Programme Updates", consented = false),
        )

    /** A conversation in [CONVERSATION_CHANNEL_ID] ending in two unread messages, and a last message in each other subscribed channel. */
    fun buildMessages(): List<ConnectMessagingMessageRecord> =
        listOf(
            buildMessage(
                CONVERSATION_CHANNEL_ID,
                1,
                minutesAgo = 26 * 60,
                outgoing = false,
                "Welcome to the Maternal Health Home Visits programme! Reply here if you have any questions.",
            ),
            buildMessage(
                CONVERSATION_CHANNEL_ID,
                2,
                minutesAgo = 25 * 60,
                outgoing = true,
                "Thank you. Do I need to visit every household on my list this week?",
            ),
            buildMessage(
                CONVERSATION_CHANNEL_ID,
                3,
                minutesAgo = 24 * 60,
                outgoing = false,
                "No, aim for 8 visits a day. Start with the households marked as high priority, and record each visit before you leave the house.",
            ),
            buildMessage(CONVERSATION_CHANNEL_ID, 4, minutesAgo = 50, outgoing = true, "Understood. I finished 6 visits today."),
            buildMessage(
                CONVERSATION_CHANNEL_ID,
                5,
                minutesAgo = 12,
                outgoing = false,
                "Great work! Your payment for last week has been approved.",
                viewed = false,
            ),
            buildMessage(
                CONVERSATION_CHANNEL_ID,
                6,
                minutesAgo = 10,
                outgoing = false,
                "Remember that the programme ends on Friday.",
                viewed = false,
            ),
            buildMessage(
                "showcase-channel-support",
                1,
                minutesAgo = 30 * 60,
                outgoing = true,
                "My photo upload keeps failing, can you help?",
            ),
            buildMessage(
                "showcase-channel-malaria",
                1,
                minutesAgo = 6 * 24 * 60,
                outgoing = false,
                "The training modules are now available.",
            ),
        )

    /** The credentials response for Work History: one entry per activity level, newest first once sorted. */
    fun buildWorkHistoryJson(): String =
        JSONObject()
            .put(
                "credentials",
                JSONArray().apply {
                    listOf(
                        Triple("Maternal Health Home Visits", "12MON_ACTIVE", "2026-09-30T09:00:00Z"),
                        Triple("Household Malaria Prevention Survey", "6MON_ACTIVE", "2026-07-14T09:00:00Z"),
                        Triple("Child Growth Monitoring and Nutrition Counselling, Kisumu County", "3MON_ACTIVE", "2026-05-02T09:00:00Z"),
                        Triple("Community Vaccination Awareness Campaign", "1MON_ACTIVE", "2026-02-20T09:00:00Z"),
                    ).forEachIndexed { index, (title, level, issued) ->
                        put(
                            JSONObject().apply {
                                put("uuid", "showcase-credential-$index")
                                put("app_id", "showcase-deliver-$index")
                                put("opp_id", "showcase-opportunity-$index")
                                put("date", issued)
                                put("title", title)
                                put("issuer", ORGANIZATION)
                                put("level", level)
                                put("type", "DELIVER")
                                put("issuer_environment", "production")
                                put("slug", "showcase-credential-$index")
                            },
                        )
                    }
                },
            ).toString()

    private fun buildChannel(
        channelId: String,
        name: String,
        consented: Boolean,
    ): ConnectMessagingChannelRecord =
        ConnectMessagingChannelRecord
            .fromJson(
                JSONObject().apply {
                    put(ConnectMessagingChannelRecord.META_CHANNEL_ID, channelId)
                    put(ConnectMessagingChannelRecord.META_CONSENT, consented)
                    put(ConnectMessagingChannelRecord.META_CHANNEL_SOURCE, name)
                    put(ConnectMessagingChannelRecord.META_KEY_URL, "https://example.org/key")
                    put(ConnectMessagingChannelRecord.META_CHANNEL_NAME, name)
                },
            ).apply {
                answeredConsent = true
                key = "showcase-key"
            }

    private fun buildMessage(
        channelId: String,
        index: Int,
        minutesAgo: Int,
        outgoing: Boolean,
        text: String,
        viewed: Boolean = true,
    ): ConnectMessagingMessageRecord =
        ConnectMessagingMessageRecord().apply {
            messageId = "$channelId-message-$index"
            this.channelId = channelId
            timeStamp = Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutesAgo.toLong()))
            message = text
            isOutgoing = outgoing
            confirmed = true
            userViewed = viewed
        }

    private fun buildOpportunity(
        id: Int,
        title: String,
        status: Int,
        endInDays: Int,
        modulesDone: Int = LEARN_MODULES,
        visitsDone: Int = 0,
    ): ConnectJobRecord =
        ConnectJobRecord.fromJson(buildJobJson(id, title, endInDays, modulesDone, visitsDone)).apply {
            this.status = status
        }

    private fun buildJobJson(
        id: Int,
        title: String,
        endInDays: Int,
        modulesDone: Int,
        visitsDone: Int,
    ): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("opportunity_id", getOpportunityUuid(id))
            put("name", title)
            put("description", "Visit households in your area, record what you find and share health guidance with each family.")
            put("short_description", "Visit households and record what you find.")
            put("organization", ORGANIZATION)
            put("start_date", formatIsoDate(endInDays - 120))
            put("end_date", formatIsoDate(endInDays))
            put("max_visits_per_user", MAX_VISITS)
            put("daily_max_visits_per_user", 8)
            put("budget_per_visit", BUDGET_PER_VISIT)
            put("budget_per_user", BUDGET_PER_VISIT * MAX_VISITS)
            put("currency", CURRENCY)
            put("deliver_progress", visitsDone)
            put("payment_units", buildPaymentUnitsJson())
            put(
                "learn_progress",
                JSONObject().apply {
                    put("total_modules", LEARN_MODULES)
                    put("completed_modules", modulesDone)
                },
            )
            put("learn_app", buildAppJson("showcase-learn-$id", withModules = true))
            put("deliver_app", buildAppJson("showcase-deliver-$id", withModules = false))
        }

    private fun buildPaymentUnitsJson(): JSONArray =
        JSONArray().put(
            JSONObject().apply {
                put("id", 1)
                put("payment_unit_id", PAYMENT_UNIT_ID)
                put("name", "Household visit")
                put("max_total", MAX_VISITS)
                put("max_daily", 8)
                put("amount", BUDGET_PER_VISIT)
            },
        )

    private fun buildDeliveryJson(
        visit: Int,
        status: String,
        minutesAgo: Int,
    ): JSONObject =
        JSONObject().apply {
            put("id", visit + 1)
            put("visit_date", formatIsoDateTime(minutesAgo))
            put("status", status)
            put("deliver_unit_name", "Household visit")
            put("deliver_unit_slug", PAYMENT_UNIT_ID)
            put("deliver_unit_slug_id", PAYMENT_UNIT_ID)
            put("entity_id", "showcase-household-$visit")
            put("entity_name", HOUSEHOLDS[visit % HOUSEHOLDS.size])
            put("reason", if (status == "rejected") "The photo of the growth chart is missing." else "")
        }

    private fun buildPaymentJson(
        id: String,
        visits: Int,
        daysAgo: Int,
        confirmed: Boolean,
    ): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("payment_id", "showcase-payment-$id")
            put("date_paid", formatIsoDateTime(minutesAgo = daysAgo * 24 * 60))
            put("amount", visits * BUDGET_PER_VISIT)
            put("confirmed", confirmed)
            if (confirmed) {
                put("date_confirmed", formatIsoDate(-daysAgo + 1))
            }
        }

    private fun buildAppJson(
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
            put("learn_modules", if (withModules) buildModulesJson() else JSONArray())
        }

    private fun buildModulesJson(): JSONArray =
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

    private fun formatIsoDateTime(minutesAgo: Int): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
            .format(Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutesAgo.toLong())))

    private fun formatIsoDate(daysFromNow: Int): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysFromNow) }.time)
}
