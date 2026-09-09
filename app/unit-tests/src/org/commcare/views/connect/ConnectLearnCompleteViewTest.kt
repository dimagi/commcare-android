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
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectLearnJobTestData
import org.commcare.connect.ConnectMoneyUtils
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.Date

/**
 * Robolectric tests for [ConnectLearnCompleteView]: verifies a real [ConnectJobRecord] renders into
 * the learn header, certificate and delivery cards, and drives the certificate toggle, CTA and
 * failure-card dismissal through real view clicks.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectLearnCompleteViewTest {
    private val learnerName = "Person Name"
    private val completedOn = Date(1771670400000L)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context =
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext<CommCareTestApplication>(),
                R.style.CommonTheme,
            )
        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun bind(
        job: ConnectJobRecord = ConnectLearnJobTestData.job(),
        onCta: () -> Unit = {},
    ): ConnectLearnCompleteView =
        ConnectLearnCompleteView(context).also { view ->
            view.bind(job, completedOn, learnerName) { onCta() }
        }

    private fun ConnectLearnCompleteView.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun ConnectLearnCompleteView.card(id: Int) = findViewById<ConnectInfoCard>(id)

    private fun expectedCompletedOn() =
        context.getString(
            R.string.connect_learn_completed,
            ConnectDateUtils.formatDate(completedOn, DateFormat.SHORT),
        )

    @Test
    fun `learn header shows the short-formatted completion date on one line`() {
        val view = bind()
        val header = view.text(R.id.learn_complete_completed_on)

        assertEquals(expectedCompletedOn(), header)
        assertFalse("Header date should not wrap onto a second line", header.contains("\n"))
    }

    @Test
    fun `certificate shows the job title, learner name and passing score`() {
        val view = bind()

        assertEquals(ConnectLearnJobTestData.JOB_TITLE, view.text(R.id.cert_subject_text))
        assertEquals(learnerName, view.text(R.id.cert_person_text))
        assertEquals(
            context.getString(
                R.string.connect_learn_cert_score,
                (ConnectLearnJobTestData.PASSING_SCORE + 10).toString(),
            ),
            view.text(R.id.cert_score_text),
        )
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.cert_score_text).visibility)
    }

    @Test
    fun `certificate score is hidden when no assessment was attempted`() {
        val view = bind(ConnectLearnJobTestData.job(assessmentScore = null))

        assertEquals(View.GONE, view.findViewById<View>(R.id.cert_score_text).visibility)
    }

    @Test
    fun `delivery cards show visits, days remaining and maximum earnings`() {
        val job = ConnectLearnJobTestData.job()
        val view = bind(job)

        assertEquals(job.maxPossibleVisits.toString(), view.card(R.id.card_total_visits).valueText)
        assertEquals(
            context.getString(
                R.string.connect_opportunity_visits_per_day,
                ConnectLearnJobTestData.MAX_DAILY_VISITS,
            ),
            view.card(R.id.card_total_visits).subtitleText,
        )
        assertEquals(job.daysRemaining.toString(), view.card(R.id.card_days_to_complete).valueText)
        assertEquals(
            ConnectMoneyUtils.moneyStringWithSymbol(job.currency, job.totalBudget),
            view.card(R.id.card_max_earnings).valueText,
        )
        assertEquals(
            context.resources.getQuantityString(
                R.plurals.connect_opportunity_payment_units,
                ConnectLearnJobTestData.PAYMENT_UNIT_COUNT,
                ConnectLearnJobTestData.PAYMENT_UNIT_COUNT,
            ),
            view.card(R.id.card_max_earnings).subtitleText,
        )
    }

    @Test
    fun `cta offers the delivery download when the delivery app is missing`() {
        val view = bind()

        assertEquals(
            context.getString(R.string.connect_learn_complete_footer_start_visits),
            view.text(R.id.cta_title_text),
        )
        assertEquals(
            context.getString(R.string.connect_job_info_download_delivery).trim(),
            view.text(R.id.cta_subtitle_text),
        )
        assertEquals(
            context.getString(R.string.connect_opportunity_footer_download_app),
            view.text(R.id.cta_button),
        )
    }

    @Test
    fun `cta offers go to app and hides the subtitle when the delivery app is installed`() {
        every { AppUtils.isAppInstalled(ConnectLearnJobTestData.DELIVERY_APP_ID) } returns true
        val view = bind()

        assertEquals(
            context.getString(R.string.connect_delivery_go),
            view.text(R.id.cta_button),
        )
        assertEquals(View.GONE, view.findViewById<View>(R.id.cta_subtitle_text).visibility)
    }

    @Test
    fun `ended job shows the warning banner and an active job does not`() {
        val ended = bind(ConnectLearnJobTestData.job(endDate = "2020-01-01"))
        assertEquals(
            context.getString(R.string.connect_learn_warning_ended),
            ended.text(R.id.cta_info_banner),
        )
        assertEquals(View.VISIBLE, ended.findViewById<View>(R.id.cta_info_banner).visibility)

        val active = bind()
        assertEquals(View.GONE, active.findViewById<View>(R.id.cta_info_banner).visibility)
    }

    @Test
    fun `tapping the certificate header collapses it and tapping again expands it`() {
        val view = bind()
        val header = view.findViewById<View>(R.id.certificate_header)
        val certificate = view.findViewById<View>(R.id.certificate)
        val chevron = view.findViewById<View>(R.id.certificate_chevron)
        val container = view.findViewById<View>(R.id.certificate_container)
        val expandedPadding = container.paddingBottom

        assertEquals(View.VISIBLE, certificate.visibility)
        assertEquals(180f, chevron.rotation)
        assertTrue("Expanded container should pad below the certificate", expandedPadding > 0)

        header.performClick()
        assertEquals(View.GONE, certificate.visibility)
        assertEquals(0f, chevron.rotation)
        assertEquals(
            "Collapsed container should not keep the certificate's bottom padding",
            0,
            container.paddingBottom,
        )

        header.performClick()
        assertEquals(View.VISIBLE, certificate.visibility)
        assertEquals(180f, chevron.rotation)
        assertEquals(expandedPadding, container.paddingBottom)
    }

    @Test
    fun `tapping the cta button invokes the click listener`() {
        var clicks = 0
        val view = bind(onCta = { clicks++ })

        view.findViewById<MaterialButton>(R.id.cta_button).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `failure card is hidden until shown and its close button dismisses it`() {
        val view = bind()
        val failureCard = view.findViewById<ConnectSuccessFailureCard>(R.id.learn_complete_failure_card)

        assertEquals(View.GONE, failureCard.visibility)

        view.showClaimFailure("Unable to claim")
        assertEquals(View.VISIBLE, failureCard.visibility)
        assertEquals("Unable to claim", failureCard.messageText.toString())
        assertEquals(ConnectSuccessFailureCard.Mode.FAILURE, failureCard.mode)

        failureCard.findViewById<View>(R.id.success_failure_card_close).performClick()
        assertEquals(View.GONE, failureCard.visibility)
    }

    @Test
    fun `cta can be disabled and re-enabled`() {
        val view = bind()
        val button = view.findViewById<MaterialButton>(R.id.cta_button)
        assertTrue("CTA should start enabled", button.isEnabled)

        view.isCtaEnabled = false
        assertFalse(button.isEnabled)

        view.isCtaEnabled = true
        assertTrue(button.isEnabled)
    }
}
