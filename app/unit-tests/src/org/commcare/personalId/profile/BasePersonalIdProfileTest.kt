package org.commcare.personalId.profile

import android.os.Bundle
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.After
import org.junit.Before
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Shared Robolectric scaffolding for the Manage Profile fragment tests: boots
 * [PersonalIdProfileActivity] on its start (Profile) destination with a stubbed user record and a
 * mock network server.
 */
abstract class BasePersonalIdProfileTest {
    protected lateinit var activityController: ActivityController<PersonalIdProfileActivity>
    protected lateinit var activity: PersonalIdProfileActivity
    protected lateinit var navHostFragment: NavHostFragment
    protected lateinit var navController: NavController

    protected lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>
    protected lateinit var firebaseAnalyticsUtilMock: MockedStatic<FirebaseAnalyticsUtil>

    protected val mockApiServer = PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.MAIN_LOOPER)
    protected val mockWebServer get() = mockApiServer.server

    protected lateinit var user: ConnectUserRecord

    @Before
    fun setUp() {
        connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
        user =
            ConnectUserRecord(
                "+11234567890",
                "test-user-id",
                "test-password",
                "Ada Lovelace",
                "",
                null,
                null,
                false,
                "",
                false,
            ).apply {
                email = "ada@example.com"
            }
        connectUserDatabaseUtilMock
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser(any()) }
            .thenReturn(user)
        firebaseAnalyticsUtilMock = Mockito.mockStatic(FirebaseAnalyticsUtil::class.java)
        firebaseAnalyticsUtilMock
            .`when`<NavController.OnDestinationChangedListener> {
                FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
            }.thenReturn(
                NavController.OnDestinationChangedListener { _: NavController, _: NavDestination, _: Bundle? -> },
            )
        mockApiServer.start()
        launchProfileActivity()
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
        connectUserDatabaseUtilMock.close()
        firebaseAnalyticsUtilMock.close()
        mockApiServer.shutdown()
    }

    private fun launchProfileActivity() {
        activityController = Robolectric.buildActivity(PersonalIdProfileActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.profile_nav_host) as NavHostFragment
        navController = navHostFragment.navController
    }

    protected fun onUiThread(block: () -> Unit) {
        activity.runOnUiThread { block() }
        ShadowLooper.idleMainLooper()
    }

    protected fun setText(
        field: TextView,
        value: String,
    ) {
        onUiThread { field.text = value }
    }

    @IdRes
    protected fun currentDestinationId(): Int = navController.currentDestination!!.id
}
