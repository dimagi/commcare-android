package org.commcare.fragments.connect

import android.os.Build
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectAppUtils
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectMoneyUtils
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ApiConnect
import org.commcare.connect.network.IApiCallback
import org.commcare.dalvik.R
import org.commcare.views.connect.ConnectInfoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.ByteArrayInputStream
import java.text.DateFormat

/**
 * Robolectric UI tests for [ConnectJobIntroFragment]: verifies the job data renders into the
 * header, learn card and delivery cards, and that tapping the learn card navigates to the
 * modules bottom sheet. Interactions are driven through real view clicks.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectJobIntroFragmentTest : BaseConnectJobIntroTest() {
    private fun launch(): ConnectJobIntroFragment = navigateToIntroFragment()

    private fun cardValue(
        fragment: ConnectJobIntroFragment,
        cardId: Int,
    ): String =
        fragment
            .requireView()
            .findViewById<ConnectInfoCard>(cardId)
            .valueText
            .toString()

    private fun cardSubtitle(
        fragment: ConnectJobIntroFragment,
        cardId: Int,
    ): String =
        fragment
            .requireView()
            .findViewById<ConnectInfoCard>(cardId)
            .subtitleText
            .toString()

    @Test
    fun `job title and description are shown`() {
        val fragment = launch()
        assertEquals(
            "Infant Vaccination",
            fragment
                .requireView()
                .findViewById<TextView>(R.id.tv_job_title)
                .text
                .toString(),
        )
        assertEquals(
            "One line description about the opportunity.",
            fragment
                .requireView()
                .findViewById<TextView>(R.id.tv_job_description)
                .text
                .toString(),
        )
    }

    @Test
    fun `expiry shows the short-formatted project end date`() {
        val fragment = launch()
        assertEquals(
            ConnectDateUtils.formatDate(job.projectEndDate, DateFormat.SHORT),
            fragment
                .requireView()
                .findViewById<TextView>(R.id.tv_expiry_value)
                .text
                .toString(),
        )
    }

    @Test
    fun `header maximum earnings shows the total budget with currency symbol`() {
        val fragment = launch()
        assertEquals(
            ConnectMoneyUtils.moneyStringWithSymbol(job.currency, job.totalBudget),
            fragment
                .requireView()
                .findViewById<TextView>(R.id.tv_max_earnings_value)
                .text
                .toString(),
        )
    }

    @Test
    fun `learn card shows module count and total hours`() {
        val fragment = launch()
        assertEquals("2", cardValue(fragment, R.id.card_learn_modules))
        assertEquals(
            activity.getString(R.string.connect_opportunity_learn_hours_total, 2),
            cardSubtitle(fragment, R.id.card_learn_modules),
        )
    }

    @Test
    fun `max visits card shows max visits and visits per day`() {
        val fragment = launch()
        assertEquals("100", cardValue(fragment, R.id.card_max_visits))
        assertEquals(
            activity.getString(R.string.connect_opportunity_visits_per_day, 10),
            cardSubtitle(fragment, R.id.card_max_visits),
        )
    }

    @Test
    fun `days card shows the days remaining`() {
        val fragment = launch()
        assertEquals(job.daysRemaining.toString(), cardValue(fragment, R.id.card_days))
    }

    @Test
    fun `max earnings card shows total budget and payment unit count`() {
        val fragment = launch()
        assertEquals(
            ConnectMoneyUtils.moneyStringWithSymbol(job.currency, job.totalBudget),
            cardValue(fragment, R.id.card_max_earnings),
        )
        assertEquals(
            activity.getString(R.string.connect_opportunity_payment_units, 2),
            cardSubtitle(fragment, R.id.card_max_earnings),
        )
    }

    @Test
    fun `footer button shows download app label and is enabled`() {
        val fragment = launch()
        val button = fragment.requireView().findViewById<MaterialButton>(R.id.btn_start)
        assertEquals(
            activity.getString(R.string.connect_opportunity_footer_download_app),
            button.text.toString(),
        )
        assertTrue("Footer button should be enabled", button.isEnabled)
    }

    @Test
    fun `tapping the learn modules card navigates to the modules bottom sheet`() {
        val fragment = launch()
        val card = fragment.requireView().findViewById<ConnectInfoCard>(R.id.card_learn_modules)

        activity.runOnUiThread { card.performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(
            R.id.connect_learn_modules_bottom_sheet,
            navController.currentDestination?.id,
        )
    }

    @Test
    fun `tapping the footer button starts learning and navigates to downloading`() {
        mockkStatic(ConnectUserDatabaseUtil::class)
        every { ConnectUserDatabaseUtil.getUser(any()) } returns mockk<ConnectUserRecord>()
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.upsertJob(any(), any()) } returns Unit
        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns Unit

        // Capture the API callback without invoking it synchronously, so success is delivered
        // after the click returns (as a real async network response would be).
        val callbackSlot = slot<IApiCallback>()
        mockkStatic(ApiConnect::class)
        every {
            ApiConnect.startLearnApp(any(), any(), any(), capture(callbackSlot))
        } returns Unit

        val fragment = launch()
        val button = fragment.requireView().findViewById<MaterialButton>(R.id.btn_start)

        activity.runOnUiThread { button.performClick() }
        ShadowLooper.idleMainLooper()

        activity.runOnUiThread {
            callbackSlot.captured.processSuccess(200, ByteArrayInputStream(ByteArray(0)))
        }
        ShadowLooper.idleMainLooper()

        verify { ApiConnect.startLearnApp(any(), any(), eq(job.jobUUID), any()) }
        assertEquals(ConnectJobRecord.STATUS_LEARNING, job.status)
        assertEquals(
            R.id.connect_downloading_fragment,
            navController.currentDestination?.id,
        )
    }
}
