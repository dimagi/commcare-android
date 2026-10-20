package org.commcare.l10n

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.AppUtils
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.network.ConnectMockApiServer
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.ConnectRequestManager
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.coroutines.DispatcherProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule

/**
 * A signed-in Connect user whose screens read seeded data from the local database, with every
 * network call answered empty so none of it is pruned.
 */
abstract class BaseConnectScreenshotTest(
    protected val locale: String,
) {
    @get:Rule
    val localeRule: TestRule = L10nScreenshots.localeRule(locale)

    private val mockApi = ConnectMockApiServer()

    @Before
    fun setUpConnect() {
        mockApi.start()
        mockApi.server.dispatcher = emptyJsonDispatcher()
        ConnectRepository.resetInstance()
        ConnectRequestManager.cancelAll()
        mockOutsideWorld()

        ConnectTestUtils.signInToPersonalId(hasConnectAccess = true)
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.LoggedIn
    }

    @After
    fun tearDownConnect() {
        ConnectDatabaseHelper.teardown()
        mockApi.shutdown()
        unmockkAll()
    }

    private fun mockOutsideWorld() {
        mockkObject(DispatcherProvider)
        every { DispatcherProvider.io() } returns UnconfinedTestDispatcher()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit
        every { MessageManager.sendUnsentMessages(any()) } returns Unit

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
}
