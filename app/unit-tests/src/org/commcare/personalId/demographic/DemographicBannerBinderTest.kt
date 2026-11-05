package org.commcare.personalId.demographic

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ConnectMockApiServer
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.ConnectRequestManager
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdFeatureFlagChecker
import org.commcare.personalId.PersonalIdFeatureFlagChecker.FeatureFlag.Companion.DEMOGRAPHIC_PROMPT
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.commcare.utils.coroutines.DispatcherProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import kotlin.time.Duration.Companion.hours

@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class DemographicBannerBinderTest {
    private lateinit var controller: ActivityController<ConnectActivity>
    private lateinit var savedStatus: PersonalIdSessionManager.PersonalIdStatus
    private val mockApi = ConnectMockApiServer()

    private val activity: ConnectActivity get() = controller.get()

    @Before
    fun setUp() {
        MockAndroidKeyStoreProvider.registerProvider()
        savedStatus = PersonalIdSessionManager.getInstance().status
        mockApi.start()
        mockApi.server.dispatcher = emptyJsonDispatcher()
        ConnectRepository.resetInstance()
        ConnectRequestManager.cancelAll()

        mockkObject(DispatcherProvider)
        every { DispatcherProvider.io() } returns UnconfinedTestDispatcher()

        mockkObject(PersonalIdFeatureFlagChecker.Companion)
        every { PersonalIdFeatureFlagChecker.isFeatureEnabled(DEMOGRAPHIC_PROMPT) } returns true

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportConnectTabChange(any()) } returns Unit
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }

        PersonalIdUserPreferences.clear()
        ConnectTestUtils.signInToPersonalId(hasConnectAccess = true)
        PersonalIdSessionManager.getInstance().status = PersonalIdSessionManager.PersonalIdStatus.LoggedIn
        ConnectSyncPreferences.getInstance().apply {
            markSessionStart()
            storeLastSyncTime(ConnectRepository.SYNC_KEY_OPPORTUNITIES)
        }
    }

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
        PersonalIdUserPreferences.clear()
        PersonalIdSessionManager.getInstance().status = savedStatus
        mockApi.shutdown()
        unmockkAll()
        MockAndroidKeyStoreProvider.deregisterProvider()
    }

    @Test
    fun `the prompt shows on the opportunity list when the profile is incomplete`() {
        launchOpportunityList()

        assertNotNull(promptView())
    }

    @Test
    fun `a due backup code reminder shows instead of the prompt`() {
        PersonalIdUserPreferences.setNextBackupCodeReminderDue(System.currentTimeMillis() - 1.hours.inWholeMilliseconds)

        launchOpportunityList()

        assertNotNull(activity.currentAlertDialog)
        assertNull(promptView())
        assertEquals(0, PersonalIdUserPreferences.getDemographicPromptCount())
    }

    @Test
    fun `the prompt does not show while its feature flag is off`() {
        every { PersonalIdFeatureFlagChecker.isFeatureEnabled(DEMOGRAPHIC_PROMPT) } returns false

        launchOpportunityList()

        assertNull(activity.currentAlertDialog)
    }

    @Test
    fun `the prompt does not show when the profile is complete`() {
        updateUser {
            email = "user@example.org"
            gender = "female"
            dateOfBirth = "1990-01-01"
            languages = "en"
            education = "secondary"
            chwExperience = "2"
            techProficiency = "high"
        }

        launchOpportunityList()

        assertNull(activity.currentAlertDialog)
    }

    @Test
    fun `the prompt does not show while it is snoozed`() {
        PersonalIdUserPreferences.setDemographicPromptNextShowAt(System.currentTimeMillis() + 1.hours.inWholeMilliseconds)

        launchOpportunityList()

        assertNull(activity.currentAlertDialog)
    }

    @Test
    fun `the first prompt shows neither warning and offers remind later`() {
        launchOpportunityList()

        assertStageViews(warningVisible = false, finalVisible = false, declineText = R.string.personalid_demographic_banner_remind_later)
    }

    @Test
    fun `the third prompt shows the warning`() {
        PersonalIdUserPreferences.setDemographicPromptCount(2)

        launchOpportunityList()

        assertStageViews(warningVisible = true, finalVisible = false, declineText = R.string.personalid_demographic_banner_remind_later)
    }

    @Test
    fun `the fourth prompt shows the final message and offers dismiss`() {
        PersonalIdUserPreferences.setDemographicPromptCount(3)

        launchOpportunityList()

        assertStageViews(warningVisible = false, finalVisible = true, declineText = R.string.personalid_demographic_banner_dismiss)
    }

    @Test
    fun `remind later closes the prompt and snoozes it for 24 hours`() {
        launchOpportunityList()

        tapPromptButton(R.id.demographic_prompt_decline)

        assertNull(activity.currentAlertDialog)
        assertPromptRecordedAndSnoozed()
        assertEquals(activity.getString(R.string.personalid_demographic_banner_snoozed), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `complete profile closes the prompt and snoozes it for 24 hours`() {
        launchOpportunityList()

        tapPromptButton(R.id.demographic_prompt_complete)

        assertNull(activity.currentAlertDialog)
        assertPromptRecordedAndSnoozed()
    }

    private fun launchOpportunityList() {
        controller =
            Robolectric
                .buildActivity(ConnectActivity::class.java)
                .create()
                .postCreate(null)
                .start()
                .resume()
                .visible()
        ShadowLooper.idleMainLooper()
    }

    private fun promptView(): View? =
        activity.currentAlertDialog
            ?.dialog
            ?.findViewById(R.id.demographic_prompt_title)

    private fun tapPromptButton(buttonId: Int) {
        activity.currentAlertDialog!!
            .dialog!!
            .findViewById<View>(buttonId)
            .performClick()
        ShadowLooper.idleMainLooper()
    }

    private fun assertStageViews(
        warningVisible: Boolean,
        finalVisible: Boolean,
        declineText: Int,
    ) {
        val dialog = activity.currentAlertDialog!!.dialog!!
        assertEquals(warningVisible, dialog.findViewById<View>(R.id.demographic_prompt_warning).visibility == View.VISIBLE)
        assertEquals(finalVisible, dialog.findViewById<View>(R.id.demographic_prompt_final).visibility == View.VISIBLE)
        assertEquals(activity.getString(declineText), dialog.findViewById<TextView>(R.id.demographic_prompt_decline).text.toString())
    }

    private fun assertPromptRecordedAndSnoozed() {
        assertEquals(1, PersonalIdUserPreferences.getDemographicPromptCount())
        val snoozeMillis = PersonalIdUserPreferences.getDemographicPromptNextShowAt() - System.currentTimeMillis()
        assertTrue(snoozeMillis > 23.hours.inWholeMilliseconds)
        assertFalse(snoozeMillis > 24.hours.inWholeMilliseconds)
    }

    private fun updateUser(update: ConnectUserRecord.() -> Unit) {
        val user = ConnectUserDatabaseUtil.getUser()
        user.update()
        ConnectUserDatabaseUtil.storeUser(user)
    }

    private fun emptyJsonDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(200).setBody("{}")
        }
}
