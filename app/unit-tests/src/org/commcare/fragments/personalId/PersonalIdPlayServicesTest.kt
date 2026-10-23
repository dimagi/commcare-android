package org.commcare.fragments.personalId

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLooper

/**
 * Custom shadow for GoogleApiAvailability to enable testing of different Play Services states
 */
@Implements(GoogleApiAvailability::class)
class ShadowGoogleApiAvailability {
    @Implementation
    fun isGooglePlayServicesAvailable(context: Context): Int = playServicesStatus

    @Implementation
    fun isUserResolvableError(errorCode: Int): Boolean =
        when (errorCode) {
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> true
            else -> false
        }

    @Implementation
    fun showErrorDialogFragment(
        activity: Activity,
        errorCode: Int,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        cancelListener: DialogInterface.OnCancelListener,
    ): Boolean {
        // Track that the method was called
        showErrorDialogCalled = true
        lastErrorCode = errorCode

        // Don't actually show dialog in tests, just trigger the cancel listener
        cancelListener.onCancel(null)
        return true
    }

    companion object {
        var playServicesStatus: Int = ConnectionResult.SUCCESS
        var showErrorDialogCalled: Boolean = false
        var lastErrorCode: Int = -1

        fun reset() {
            playServicesStatus = ConnectionResult.SUCCESS
            showErrorDialogCalled = false
            lastErrorCode = -1
        }
    }
}

/**
 * Tests for PersonalIdPhoneFragment Google Play Services checking.
 * This test class is separate from PersonalIdPhoneFragmentTest to allow setting
 * Play Services state before fragment creation without needing to destroy/recreate activities.
 */
@Config(application = CommCareTestApplication::class, shadows = [ShadowGoogleApiAvailability::class])
@RunWith(AndroidJUnit4::class)
class PersonalIdPlayServicesTest : BasePersonalIdConfigurationTest<PersonalIdPhoneFragment>() {
    private fun setUpPersonalIdActivityWithFragment() {
        bootActivity()
        captureNavFragment()
        ShadowLooper.idleMainLooper()
    }

    @After
    override fun tearDown() {
        if (::activityController.isInitialized) {
            try {
                activityController.pause().stop().destroy()
            } catch (_: IllegalStateException) {
                // Ignore navigation errors during teardown
            }
        }
        ShadowGoogleApiAvailability.reset()
        super.tearDown()
    }

    @Test
    fun testCheckGooglePlayServices_showsResolvableError() {
        // Set up mock
        ShadowGoogleApiAvailability.playServicesStatus = ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED

        // Act
        setUpPersonalIdActivityWithFragment()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Verify
        assertTrue(
            "showErrorDialogFragment should be called for resolvable error",
            ShadowGoogleApiAvailability.showErrorDialogCalled,
        )

        assertEquals(
            "Error code should be SERVICE_VERSION_UPDATE_REQUIRED",
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ShadowGoogleApiAvailability.lastErrorCode,
        )
    }

    @Test
    fun testCheckGooglePlayServices_showsNonResolvableError() {
        // Set up mock
        ShadowGoogleApiAvailability.playServicesStatus = ConnectionResult.SERVICE_INVALID

        // Act
        setUpPersonalIdActivityWithFragment()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Verify
        assertFalse(
            "showErrorDialogFragment should NOT be called for non-resolvable error",
            ShadowGoogleApiAvailability.showErrorDialogCalled,
        )

        assertEquals(
            "Should navigate to message display screen for non-resolvable error",
            R.id.personalid_message_display,
            navHostFragment.navController.currentDestination!!.id,
        )

        val expectedMessage = activity.getString(R.string.play_service_update_error)
        val actualMessage = navHostFragment.navController.currentBackStackEntry!!.arguments!!.getString("message")

        assertEquals(
            "Message should indicate play services error",
            expectedMessage,
            actualMessage,
        )
    }
}
