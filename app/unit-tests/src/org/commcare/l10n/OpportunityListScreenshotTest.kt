package org.commcare.l10n

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.ConnectMockApiServer
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.ConnectRequestManager
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.coroutines.DispatcherProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Connect screens seeded with [ConnectScreenshotData] and served from the local cache, so no
 * screen waits on the network.
 */
@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class OpportunityListScreenshotTest(
    private val locale: String,
) {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    private val mockApi = ConnectMockApiServer()

    @Before
    fun setUp() {
        mockApi.start()
        mockApi.server.dispatcher = emptyJsonDispatcher()
        ConnectRepository.resetInstance()
        ConnectRequestManager.cancelAll()
        mockOutsideWorld()

        ConnectTestUtils.signInToPersonalId(hasConnectAccess = true)
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.LoggedIn
        ConnectJobUtils.storeJobs(ApplicationProvider.getApplicationContext(), ConnectScreenshotData.opportunities(), true)
        ConnectSyncPreferences.getInstance().apply {
            markSessionStart()
            storeLastSyncTime(ConnectRepository.SYNC_KEY_OPPORTUNITIES)
        }
    }

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
        mockApi.shutdown()
        unmockkAll()
    }

    @Test
    fun opportunityList() {
        val controller =
            Robolectric
                .buildActivity(ConnectActivity::class.java)
                .create()
                .postCreate(null)
                .start()
                .resume()
        L10nScreenshots.capture(controller, locale, "20_connect_opportunity_list")
    }

    private fun mockOutsideWorld() {
        mockkObject(DispatcherProvider)
        every { DispatcherProvider.io() } returns UnconfinedTestDispatcher()

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

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns true
    }

    private fun emptyJsonDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(200).setBody("{}")
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
