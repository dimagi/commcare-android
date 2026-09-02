package org.commcare.fragments.connect

import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.ConnectAppUtils
import org.commcare.connect.ConnectDateUtils
import org.commcare.connect.ConnectMoneyUtils
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.dalvik.R
import org.commcare.views.connect.ConnectInfoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.text.DateFormat
import java.util.Date

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
            activity.resources.getQuantityString(
                R.plurals.connect_opportunity_estimated_hours,
                2,
                2,
            ),
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
            activity.resources.getQuantityString(
                R.plurals.connect_opportunity_payment_units,
                2,
                2,
            ),
            cardSubtitle(fragment, R.id.card_max_earnings),
        )
    }

    @Test
    fun `footer button shows download app label and is enabled`() {
        val fragment = launch()
        val button = fragment.requireView().findViewById<MaterialButton>(R.id.cta_button)
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
        seedConnectUser()
        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false

        // A missing app now installs in place, so the download is stubbed out rather than run.
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns true

        val jobUuidSlot = slot<String>()
        val repo = ConnectRepository.getInstance()
        every { repo.startLearning(capture(jobUuidSlot)) } returns
            flow {
                emit(DataState.Success(Unit))
            }

        val fragment = launch()
        val button = fragment.requireView().findViewById<MaterialButton>(R.id.cta_button)

        activity.runOnUiThread { button.performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(job.jobUUID, jobUuidSlot.captured)
        assertEquals(ConnectJobRecord.STATUS_LEARNING, job.status)
        assertEquals(
            ConnectJobRecord.STATUS_LEARNING,
            ConnectJobUtils.getCompositeJob(job.jobUUID)?.status,
        )
        verify { ConnectAppUtils.downloadApp(any(), any()) }
        assertEquals(
            R.id.connect_job_intro_fragment,
            navController.currentDestination?.id,
        )
        assertEquals(View.GONE, button.visibility)
        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.cta_progress_ring).visibility,
        )
    }

    /**
     * Writes a real user through the Connect storage layer so [ConnectUserDatabaseUtil.getUser]
     * returns it. [ConnectDatabaseHelper.dbExists] has to be stubbed because it probes for the
     * on-disk connect db, which never exists under the in-memory test open helper.
     */
    private fun seedConnectUser() {
        mockkStatic(ConnectDatabaseHelper::class)
        every { ConnectDatabaseHelper.dbExists() } returns true
        ConnectUserDatabaseUtil.storeUser(
            ConnectUserRecord(
                "1234567890",
                "test-user-id",
                "password",
                "Test User",
                "1234",
                Date(),
                null,
                false,
                PersonalIdSessionData.PIN,
                true,
            ),
        )
    }
}
