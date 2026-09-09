package org.commcare.personalId.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileActivityTest {
    private lateinit var activityController: ActivityController<PersonalIdProfileActivity>
    private lateinit var activity: PersonalIdProfileActivity
    private lateinit var navController: NavController

    private lateinit var firebaseAnalyticsUtilMock: MockedStatic<FirebaseAnalyticsUtil>

    private val mockApiServer = PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.MAIN_LOOPER)

    @Before
    fun setUp() {
        firebaseAnalyticsUtilMock = Mockito.mockStatic(FirebaseAnalyticsUtil::class.java)
        firebaseAnalyticsUtilMock
            .`when`<NavController.OnDestinationChangedListener> {
                FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
            }.thenReturn(
                NavController.OnDestinationChangedListener { _: NavController, _: NavDestination, _: Bundle? -> },
            )
        mockApiServer.start()
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
        firebaseAnalyticsUtilMock.close()
        mockApiServer.shutdown()
    }

    private fun launchActivity(intent: Intent? = null) {
        activityController =
            if (intent != null) {
                Robolectric.buildActivity(PersonalIdProfileActivity::class.java, intent)
            } else {
                Robolectric.buildActivity(PersonalIdProfileActivity::class.java)
            }
        activity =
            activityController
                .create()
                .postCreate(null)
                .start()
                .get()
        ShadowLooper.idleMainLooper()
        val navHostFragment =
            activity.supportFragmentManager.findFragmentById(R.id.profile_nav_host) as NavHostFragment
        navController = navHostFragment.navController
    }

    private fun launchWithPendingBackupCode() {
        launchActivity(Intent().putExtra(PersonalIdProfileActivity.EXTRA_PENDING_BACKUP_CODE, true))
    }

    private fun launchAndResumeWithPendingBackupCode() {
        launchWithPendingBackupCode()
        activityController.resume()
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.personalid_profile_set_new_backup_code_fragment, navController.currentDestination!!.id)
    }

    private fun setNewBackupCodeFragment(): PersonalIdProfileSetNewBackupCodeFragment =
        activity.supportFragmentManager
            .findFragmentById(R.id.profile_nav_host)!!
            .childFragmentManager
            .primaryNavigationFragment as PersonalIdProfileSetNewBackupCodeFragment

    @Test
    fun `launched without pending backup code extra stays at profile fragment`() {
        launchActivity()
        assertEquals(R.id.personalid_profile_fragment, navController.currentDestination!!.id)
    }

    @Test
    fun `launched with EXTRA_PENDING_BACKUP_CODE navigates to set new backup code fragment`() {
        launchWithPendingBackupCode()
        assertEquals(
            R.id.personalid_profile_set_new_backup_code_fragment,
            navController.currentDestination!!.id,
        )
    }

    @Test
    fun `pressing back from set new backup code finishes the activity`() {
        launchAndResumeWithPendingBackupCode()

        activity.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        activity.runOnUiThread { dialog.findViewById<Button>(R.id.negative_button)!!.performClick() }
        ShadowLooper.idleMainLooper()

        assertTrue("activity should be finishing after exit", activity.isFinishing)
    }

    @Test
    fun `successful backup code set in pending flow finishes the activity`() {
        launchAndResumeWithPendingBackupCode()

        activity.runOnUiThread { setNewBackupCodeFragment().showSuccess() }
        ShadowLooper.idleMainLooper()

        assertTrue("activity should finish after successfully setting backup code in pending flow", activity.isFinishing)
    }
}
